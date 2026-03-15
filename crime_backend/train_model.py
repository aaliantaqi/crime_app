"""
Train Crime Prediction Model - Random Forest / XGBoost.
Reads sample_crime_data.csv, trains classifier, saves crime_model.pkl for app.py.
"""

import os
import sys
import joblib
import pandas as pd
import numpy as np
from pathlib import Path
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder
from sklearn.model_selection import train_test_split

# Optional: XGBoost for improved accuracy
try:
    import xgboost as xgb
    HAS_XGB = True
except ImportError:
    HAS_XGB = False

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_PATH = SCRIPT_DIR / "data" / "sample_crime_data.csv"
MODEL_PATH = SCRIPT_DIR / "crime_model.pkl"


def load_data():
    """Load and preprocess crime dataset."""
    if not DATA_PATH.exists():
        raise FileNotFoundError(f"Dataset not found: {DATA_PATH}")
    df = pd.read_csv(DATA_PATH)
    # Ensure required columns
    for col in ["city", "area", "time_of_day", "day_of_week", "crime_type", "risk_level"]:
        if col not in df.columns:
            raise ValueError(f"Missing column: {col}")
    return df


def main():
    print("Loading data...")
    df = load_data()

    # Encode categorical features
    encoder_city = LabelEncoder()
    encoder_area = LabelEncoder()
    encoder_time = LabelEncoder()
    encoder_day = LabelEncoder()
    encoder_crime = LabelEncoder()

    df["city_enc"] = encoder_city.fit_transform(df["city"].astype(str))
    df["area_enc"] = encoder_area.fit_transform(df["area"].astype(str))
    df["time_enc"] = encoder_time.fit_transform(df["time_of_day"].astype(str))
    df["day_enc"] = encoder_day.fit_transform(df["day_of_week"].astype(str))
    df["crime_enc"] = encoder_crime.fit_transform(df["crime_type"].astype(str))

    # Risk level as target: Low=0, Medium=1, High=2
    risk_map = {"Low": 0, "Medium": 1, "High": 2}
    df["risk_enc"] = df["risk_level"].map(risk_map)
    df = df.dropna(subset=["risk_enc"])

    X = df[["city_enc", "area_enc", "time_enc", "day_enc"]].values
    y = df["risk_enc"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    # Train Random Forest
    print("Training Random Forest...")
    rf = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42)
    rf.fit(X_train, y_train)
    acc_rf = rf.score(X_test, y_test)
    print(f"Random Forest accuracy: {acc_rf:.2%}")

    if HAS_XGB:
        print("Training XGBoost...")
        xg = xgb.XGBClassifier(n_estimators=100, max_depth=6, random_state=42, use_label_encoder=False, eval_metric="logloss")
        xg.fit(X_train, y_train)
        acc_xg = xg.score(X_test, y_test)
        print(f"XGBoost accuracy: {acc_xg:.2%}")
        model = xg if acc_xg >= acc_rf else rf
    else:
        model = rf

    # Train crime-type classifier (for predicted_crime output)
    y_crime = df["crime_enc"].values
    X_all = df[["city_enc", "area_enc", "time_enc", "day_enc"]].values
    crime_model = RandomForestClassifier(n_estimators=50, max_depth=8, random_state=42)
    crime_model.fit(X_all, y_crime)

    # Save risk model, crime model, and encoders for inference (model.py expects these keys)
    save_dict = {
        "model": model,
        "crime_model": crime_model,
        "encoder_city": encoder_city,
        "encoder_area": encoder_area,
        "encoder_time": encoder_time,
        "encoder_day": encoder_day,
        "encoder_crime": encoder_crime,
    }
    joblib.dump(save_dict, MODEL_PATH)
    print(f"Model saved to {MODEL_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
