"""
Train Crime Prediction Model - XGBoost (risk) + Random Forest (crime type).
Reads data/sample_crime_data.csv, balances classes with SMOTE or oversampling,
evaluates with cross-validation.
"""

import sys
import warnings
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from sklearn.preprocessing import LabelEncoder
from sklearn.utils import resample

try:
    import xgboost as xgb

    HAS_XGB = True
except ImportError:
    HAS_XGB = False

try:
    from imblearn.over_sampling import SMOTE

    HAS_SMOTE = True
except ImportError:
    HAS_SMOTE = False

warnings.filterwarnings("ignore", category=UserWarning)

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_PATH = SCRIPT_DIR / "data" / "sample_crime_data.csv"
MODEL_PATH = SCRIPT_DIR / "crime_model.pkl"

VALID_CRIME_TYPES = {"Theft", "Assault", "Fraud", "Vandalism"}
RISK_LEVELS = ["Low", "Medium", "High"]

# ----------------------------------------------------------------------
# FEATURE SETS
#
# CRIME_FEATURE_COLS: used to predict crime TYPE. Deliberately does NOT
# include crime_enc — you can't use the answer as an input to predict
# itself.
#
# RISK_FEATURE_COLS: used to predict risk LEVEL. Previously this only
# used city/area/time/day/report_count, so two requests that differed
# only in crime type (e.g. Theft vs Assault, same city/area/time) always
# produced the exact same risk level and probability. Adding crime_enc
# here lets the risk model actually learn that different crime types can
# carry different risk profiles for the same location/time, which is both
# more realistic and gives the model more genuinely distinct situations to
# learn from instead of memorizing one fixed answer per city+area+time
# bucket.
# ----------------------------------------------------------------------
CRIME_FEATURE_COLS = ["city_enc", "area_enc", "time_enc", "day_enc", "report_count"]
RISK_FEATURE_COLS = CRIME_FEATURE_COLS + ["crime_enc"]

# Cap on how far minority-class oversampling is allowed to go (kept from
# the earlier leakage/overfitting fix — unchanged here).
MAX_OVERSAMPLE_RATIO = 3.0


def load_data() -> pd.DataFrame:
    if not DATA_PATH.exists():
        raise FileNotFoundError(f"Dataset not found: {DATA_PATH}. Run convert_firebase_to_csv.py first.")
    df = pd.read_csv(DATA_PATH)
    required = ["city", "area", "time_of_day", "day_of_week", "crime_type", "risk_level", "report_count"]
    for col in required:
        if col not in df.columns:
            raise ValueError(f"Missing column: {col}")

    df = df[df["crime_type"].isin(VALID_CRIME_TYPES)]
    df = df[df["risk_level"].isin(RISK_LEVELS)]
    df = df.dropna(subset=required)
    return df


def balance_classes_resample(df: pd.DataFrame, label_col: str, feature_cols,
                              random_state: int = 42,
                              max_ratio: float = MAX_OVERSAMPLE_RATIO) -> pd.DataFrame:
    """
    Oversample minority classes so each class has closer-to-equal
    representation, capped at `max_ratio` times each class's own original
    size (instead of unlimited duplication up to the largest class) to
    avoid manufacturing large numbers of exact duplicate rows that a model
    can simply memorize.

    `feature_cols` tells the function which columns matter for this
    particular balancing pass (risk balancing includes crime_enc; crime
    type balancing does not), since the two pipelines now use different
    feature sets.
    """
    counts = df[label_col].value_counts()
    if counts.empty:
        return df
    target = counts.max()
    balanced_parts = []
    for label in counts.index:
        subset = df[df[label_col] == label]
        original_size = len(subset)
        capped_target = min(target, int(original_size * max_ratio))
        capped_target = max(capped_target, original_size)  # never shrink a class
        if original_size < capped_target:
            subset = resample(subset, replace=True, n_samples=capped_target, random_state=random_state)
        balanced_parts.append(subset)
    balanced = pd.concat(balanced_parts, ignore_index=True)
    return balanced.sample(frac=1, random_state=random_state).reset_index(drop=True)


def balance_risk_training(X: np.ndarray, y: np.ndarray, feature_cols, random_state: int = 42):
    """
    Balance risk classes for training. Prefer SMOTE on encoded features;
    fall back to manual (ratio-capped) oversampling if SMOTE is unavailable
    or fails. `feature_cols` must match the columns of `X`.
    """
    unique, counts = np.unique(y, return_counts=True)
    if len(unique) < 2:
        return X, y

    min_count = counts.min()
    if HAS_SMOTE and min_count >= 6:
        try:
            k_neighbors = min(5, min_count - 1)
            smote = SMOTE(random_state=random_state, k_neighbors=k_neighbors)
            X_bal, y_bal = smote.fit_resample(X, y)
            print(f"  Balanced with SMOTE: {len(y)} -> {len(y_bal)} rows")
            return X_bal, y_bal
        except Exception as exc:
            print(f"  SMOTE failed ({exc}), using manual oversampling")

    df = pd.DataFrame(X, columns=feature_cols)
    df["risk_enc"] = y
    balanced = balance_classes_resample(df, "risk_enc", feature_cols, random_state=random_state)
    return balanced[feature_cols].values, balanced["risk_enc"].values


def encode_features(df: pd.DataFrame):
    encoder_city = LabelEncoder()
    encoder_area = LabelEncoder()
    encoder_time = LabelEncoder()
    encoder_day = LabelEncoder()
    encoder_crime = LabelEncoder()
    encoder_risk = LabelEncoder()

    df = df.copy()
    df["city_enc"] = encoder_city.fit_transform(df["city"].astype(str))
    df["area_enc"] = encoder_area.fit_transform(df["area"].astype(str))
    df["time_enc"] = encoder_time.fit_transform(df["time_of_day"].astype(str))
    df["day_enc"] = encoder_day.fit_transform(df["day_of_week"].astype(str))
    df["crime_enc"] = encoder_crime.fit_transform(df["crime_type"].astype(str))
    df["risk_enc"] = encoder_risk.fit_transform(df["risk_level"].astype(str))

    return df, encoder_city, encoder_area, encoder_time, encoder_day, encoder_crime, encoder_risk


def evaluate_with_holdout(name: str, model, X_train_bal, y_train_bal, X_test, y_test, cv_splits: int = 5):
    """
    Holdout test set is chosen and set aside by the caller BEFORE any
    oversampling happens, so it can never contain a duplicate of anything
    the model trained on. Only the training portion is balanced/oversampled.
    Cross-validation below runs on the training portion only, for a sense
    of training stability; the classification report on X_test/y_test is
    the honest, leak-free evaluation metric.
    """
    cv = StratifiedKFold(n_splits=cv_splits, shuffle=True, random_state=42)
    scores = cross_val_score(model, X_train_bal, y_train_bal, cv=cv, scoring="accuracy")
    print(f"\n{name} — {cv_splits}-fold CV accuracy (training data only): {scores.mean():.2%} (+/- {scores.std() * 2:.2%})")

    model.fit(X_train_bal, y_train_bal)

    # Use predict_proba + argmax instead of relying on model.predict()
    # directly. Some XGBoost versions return a full probability matrix
    # from .predict() (instead of plain class labels) when a model is
    # configured for multi-class output but only 2 classes are actually
    # present in the data (e.g. "High" risk never occurring in a smaller
    # dataset) — argmax over predict_proba avoids that shape mismatch
    # regardless of how many classes ended up in this particular dataset.
    if hasattr(model, "predict_proba"):
        y_pred = np.argmax(model.predict_proba(X_test), axis=1)
    else:
        y_pred = model.predict(X_test)
    y_pred = np.asarray(y_pred).reshape(-1)
    y_test = np.asarray(y_test).reshape(-1)

    target_names = [str(c) for c in sorted(np.unique(np.concatenate([y_test, y_pred])))]
    print(f"\n{name} — hold-out classification report (untouched, pre-oversampling test set):")
    print(classification_report(y_test, y_pred, target_names=target_names, zero_division=0))
    return model


def print_expected_scenarios(enc_city, enc_area, enc_time, enc_day, enc_crime, enc_risk, risk_model):
    """
    Print sample predictions illustrating time + frequency + crime type
    combined risk. Each scenario now also specifies a crime type, since the
    risk model uses it as an input feature.
    """
    scenarios = [
        ("Karachi", "Clifton", "Night", "Friday", "Assault", 69, "High freq area + weekend night + Assault"),
        ("Karachi", "Clifton", "Night", "Friday", "Theft", 69, "Same slot, different crime type (Theft)"),
        ("Karachi", "Clifton", "Night", "Monday", "Theft", 69, "High freq area + weekday night"),
        ("Islamabad", "F-7", "Morning", "Monday", "Fraud", 28, "High freq area + morning"),
        ("Islamabad", "F-8", "Afternoon", "Tuesday", "Fraud", 20, "Medium freq + afternoon"),
        ("Multan", "Gulgasht", "Morning", "Monday", "Theft", 5, "Low freq area + morning"),
        ("Lahore", "DHA", "Morning", "Wednesday", "Fraud", 32, "High freq area + morning"),
    ]
    print("\n--- Expected risk scenarios (model inference) ---")
    for city, area, time_of_day, day, crime_type, count, note in scenarios:
        try:
            crime_idx = enc_crime.transform([crime_type])[0]
            row = np.array([[
                enc_city.transform([city])[0],
                enc_area.transform([area])[0],
                enc_time.transform([time_of_day])[0],
                enc_day.transform([day])[0],
                count,
                crime_idx,
            ]])
            pred_enc = risk_model.predict(row)[0]
            risk = enc_risk.inverse_transform([pred_enc])[0]
            print(f"  {city}/{area}, {time_of_day}, {day}, {crime_type}, count={count} -> {risk}  ({note})")
        except ValueError as exc:
            print(f"  {city}/{area}, {time_of_day}, {day}, {crime_type} -> skip ({exc})")


def main():
    print("Loading data...")
    df = load_data()
    print(f"Raw rows: {len(df)}")
    print(f"Risk distribution (raw CSV):\n{df['risk_level'].value_counts()}")

    df, enc_city, enc_area, enc_time, enc_day, enc_crime, enc_risk = encode_features(df)

    X_risk_full = df[RISK_FEATURE_COLS].values
    X_crime_full = df[CRIME_FEATURE_COLS].values
    y_risk = df["risk_enc"].values
    y_crime = df["crime_enc"].values

    # ------------------------------------------------------------------
    # Hold out a test split from the RAW, pre-oversampling data first.
    # Oversampling/balancing is applied only to the training portion below.
    # ------------------------------------------------------------------
    print("\nSplitting hold-out test set BEFORE any oversampling (risk)...")
    X_train_risk_raw, X_test_risk, y_train_risk_raw, y_test_risk = train_test_split(
        X_risk_full, y_risk, test_size=0.2, random_state=42, stratify=y_risk
    )

    print("Splitting hold-out test set BEFORE any oversampling (crime type)...")
    X_train_crime_raw, X_test_crime, y_train_crime_raw, y_test_crime = train_test_split(
        X_crime_full, y_crime, test_size=0.2, random_state=42, stratify=y_crime
    )

    print("\nBalancing risk classes for training (training split only)...")
    X_train_risk_bal, y_train_risk_bal = balance_risk_training(
        X_train_risk_raw, y_train_risk_raw, RISK_FEATURE_COLS
    )
    risk_counts = pd.Series(y_train_risk_bal).value_counts()
    print("Balanced risk class counts (encoded, training split only):")
    for enc_val, count in risk_counts.items():
        label = enc_risk.inverse_transform([enc_val])[0]
        print(f"  {label}: {count}")

    train_df_crime = pd.DataFrame(X_train_crime_raw, columns=CRIME_FEATURE_COLS)
    train_df_crime["crime_enc"] = y_train_crime_raw
    crime_train_balanced = balance_classes_resample(train_df_crime, "crime_enc", CRIME_FEATURE_COLS)
    X_train_crime_bal = crime_train_balanced[CRIME_FEATURE_COLS].values
    y_train_crime_bal = crime_train_balanced["crime_enc"].values

    if HAS_XGB:
        # NOTE: objective/num_class are intentionally NOT hardcoded here.
        # With a smaller or unevenly distributed dataset, it's possible for
        # one risk level (e.g. "High") to never actually occur, leaving
        # only 2 classes in the data. Forcing a fixed 3-class multi:softprob
        # configuration in that situation caused .predict() to return a
        # raw probability matrix instead of class labels on some XGBoost
        # versions, crashing evaluation. Leaving objective/num_class unset
        # lets XGBoost auto-configure itself correctly for however many
        # classes are actually present in this training run.
        risk_model = xgb.XGBClassifier(
            n_estimators=120,
            max_depth=4,
            learning_rate=0.1,
            subsample=0.85,
            colsample_bytree=0.85,
            reg_alpha=0.5,
            reg_lambda=1.0,
            random_state=42,
        )
        model_name = "XGBoost (risk)"
    else:
        risk_model = RandomForestClassifier(
            n_estimators=150,
            max_depth=8,
            min_samples_leaf=2,
            class_weight="balanced",
            random_state=42,
        )
        model_name = "RandomForest (risk)"

    print(f"\nTraining {model_name} (now includes crime type as an input feature)...")
    risk_model = evaluate_with_holdout(
        model_name, risk_model, X_train_risk_bal, y_train_risk_bal, X_test_risk, y_test_risk
    )

    print("\nTraining RandomForest (crime type)...")
    crime_model = RandomForestClassifier(
        n_estimators=120,
        max_depth=10,
        min_samples_leaf=2,
        class_weight="balanced",
        random_state=42,
    )
    crime_model = evaluate_with_holdout(
        "RandomForest (crime type)", crime_model, X_train_crime_bal, y_train_crime_bal, X_test_crime, y_test_crime
    )

    # ------------------------------------------------------------------
    # Final production models: retrained on the FULL dataset so the
    # deployed model benefits from every row collected so far. This does
    # not reintroduce leakage — the honest metrics above were already
    # computed on a holdout set aside before any balancing took place.
    # ------------------------------------------------------------------
    crime_model.fit(X_crime_full, y_crime)

    X_risk_bal, y_risk_bal = balance_risk_training(X_risk_full, y_risk, RISK_FEATURE_COLS)
    risk_model.fit(X_risk_bal, y_risk_bal)

    print_expected_scenarios(enc_city, enc_area, enc_time, enc_day, enc_crime, enc_risk, risk_model)

    save_dict = {
        "model": risk_model,
        "crime_model": crime_model,
        "encoder_city": enc_city,
        "encoder_area": enc_area,
        "encoder_time": enc_time,
        "encoder_day": enc_day,
        "encoder_crime": enc_crime,
        "encoder_risk": enc_risk,
        "valid_crime_types": list(VALID_CRIME_TYPES),
        "valid_risk_levels": RISK_LEVELS,
        "valid_times": list(enc_time.classes_),
        "valid_days": list(enc_day.classes_),
        # Lets model.py know this .pkl's risk model expects crime type as
        # part of its feature vector, so older .pkl files (without this
        # flag) can still be served correctly using the old 5-feature path.
        "risk_uses_crime_feature": True,
    }
    joblib.dump(save_dict, MODEL_PATH)
    print(f"\nModel saved to {MODEL_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
