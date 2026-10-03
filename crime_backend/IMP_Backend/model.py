"""
Crime prediction ML model loader and inference.
Loads crime_model.pkl (trained by train_model.py) and predicts risk level, probability, crime type.
"""

import datetime
import json
import logging
from pathlib import Path

import joblib
import numpy as np

logger = logging.getLogger(__name__)

MODEL_DIR = Path(__file__).resolve().parent
MODEL_PATH = MODEL_DIR / "crime_model.pkl"
REPORTS_PATH = MODEL_DIR / "reports.json"

VALID_CRIME_TYPES = {"Theft", "Assault", "Fraud", "Vandalism"}
VALID_RISK_LEVELS = {"Low", "Medium", "High"}
VALID_TIMES = {"Morning", "Afternoon", "Night"}

_model = None
_crime_model = None
_encoder_city = None
_encoder_area = None
_encoder_time = None
_encoder_day = None
_encoder_crime = None
_encoder_risk = None
_valid_crime_types = VALID_CRIME_TYPES
_freq_map = None
_risk_uses_crime_feature = False


def _get_time_of_day(timestamp_ms) -> str:
    if not timestamp_ms:
        return "Morning"
    dt = datetime.datetime.fromtimestamp(timestamp_ms / 1000)
    hour = dt.hour
    if 5 <= hour < 12:
        return "Morning"
    if 12 <= hour < 18:
        return "Afternoon"
    return "Night"


def _load_frequency_map() -> dict:
    global _freq_map
    if _freq_map is not None:
        return _freq_map

    _freq_map = {}
    if not REPORTS_PATH.exists():
        return _freq_map

    with open(REPORTS_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    reports = data.get("reports", data)
    for _, report in reports.items():
        city = (report.get("city") or report.get("location") or "").strip()
        area = (report.get("area") or "").strip()
        timestamp = report.get("timestamp")
        if not city or not area or not timestamp:
            continue
        time_of_day = _get_time_of_day(timestamp)
        key = (city, area, time_of_day)
        _freq_map[key] = _freq_map.get(key, 0) + 1

    return _freq_map


def _lookup_report_count(city: str, area: str, time_of_day: str) -> int:
    freq_map = _load_frequency_map()
    return int(freq_map.get((city.strip(), area.strip(), time_of_day.strip()), 0))


def _get_model():
    global _model, _crime_model, _encoder_city, _encoder_area
    global _encoder_time, _encoder_day, _encoder_crime, _encoder_risk, _valid_crime_types
    global _risk_uses_crime_feature

    if _model is not None:
        return (
            _model,
            _crime_model,
            _encoder_city,
            _encoder_area,
            _encoder_time,
            _encoder_day,
            _encoder_crime,
            _encoder_risk,
        )

    if not MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Model file not found: {MODEL_PATH}. Run convert_firebase_to_csv.py then train_model.py."
        )

    data = joblib.load(MODEL_PATH)
    _model = data["model"]
    _crime_model = data.get("crime_model")
    _encoder_city = data.get("encoder_city")
    _encoder_area = data.get("encoder_area")
    _encoder_time = data.get("encoder_time")
    _encoder_day = data.get("encoder_day")
    _encoder_crime = data.get("encoder_crime")
    _encoder_risk = data.get("encoder_risk")
    _valid_crime_types = set(data.get("valid_crime_types", list(VALID_CRIME_TYPES)))
    # Older .pkl files (trained before crime type was added as a risk
    # feature) won't have this flag — default to False so they keep using
    # the original 5-feature risk vector and don't break.
    _risk_uses_crime_feature = bool(data.get("risk_uses_crime_feature", False))

    return (
        _model,
        _crime_model,
        _encoder_city,
        _encoder_area,
        _encoder_time,
        _encoder_day,
        _encoder_crime,
        _encoder_risk,
    )


def _validate_inputs(city: str, area: str, time_of_day: str, day_of_week: str) -> None:
    if not city or not str(city).strip():
        raise ValueError("city is required")
    if not area or not str(area).strip():
        raise ValueError("area is required")
    if not time_of_day or not str(time_of_day).strip():
        raise ValueError("time_of_day is required")
    if not day_of_week or not str(day_of_week).strip():
        raise ValueError("day_of_week is required")

    time_norm = str(time_of_day).strip().capitalize()
    if time_norm == "Morning" or time_norm == "Afternoon" or time_norm == "Night":
        pass
    elif str(time_of_day).strip() not in VALID_TIMES:
        raise ValueError(f"time_of_day must be one of {sorted(VALID_TIMES)}")


def _encode(value: str, encoder, field_name: str) -> int:
    if encoder is None:
        return 0
    clean = str(value).strip()
    try:
        return int(encoder.transform([clean])[0])
    except (ValueError, KeyError):
        logger.warning("Unknown %s value '%s'; using fallback encoding", field_name, clean)
        return 0


def _normalize_time(time_of_day: str) -> str:
    t = str(time_of_day).strip()
    lower = t.lower()
    if lower == "morning":
        return "Morning"
    if lower == "afternoon":
        return "Afternoon"
    if lower == "night":
        return "Night"
    return t


def _to_risk_level(label, encoder_risk) -> str:
    if encoder_risk is not None:
        try:
            if isinstance(label, (int, np.integer)):
                return str(encoder_risk.inverse_transform([int(label)])[0])
            return str(encoder_risk.inverse_transform([label])[0])
        except (ValueError, IndexError):
            pass

    if isinstance(label, (int, float, np.integer)):
        mapping = {0: "Low", 1: "Medium", 2: "High"}
        return mapping.get(int(label), "Medium")
    if str(label) in VALID_RISK_LEVELS:
        return str(label)
    return "Medium"


def _to_crime_type(label, encoder_crime) -> str:
    crime = "Theft"
    if encoder_crime is not None:
        try:
            if isinstance(label, (int, np.integer)):
                crime = str(encoder_crime.inverse_transform([int(label)])[0])
            else:
                crime = str(encoder_crime.inverse_transform([label])[0])
        except (ValueError, IndexError):
            classes = getattr(encoder_crime, "classes_", None)
            if classes is not None and isinstance(label, (int, np.integer)):
                idx = int(label)
                if 0 <= idx < len(classes):
                    crime = str(classes[idx])

    if not crime or crime.lower() == "nan" or crime not in _valid_crime_types:
        return "Theft"
    return crime


def _calibrate_probability(raw_prob: float, low: float = 0.55, high: float = 0.92) -> float:
    """
    Squeeze a raw model probability into a more believable range.

    WHY THIS EXISTS: the risk label this model is trained on is computed
    by a deterministic formula (time + report frequency + crime type),
    not measured from uncertain real-world outcomes. A model trained on a
    formula-derived label naturally becomes extremely confident (often
    97-100%), because from its point of view there genuinely is no
    ambiguity in what it was shown during training. That confidence is
    real in the sense that the model isn't malfunctioning — but it reads
    as implausible to anyone viewing the app, since real-world crime risk
    is never that certain.

    This function rescales the raw probability (which ranges roughly from
    ~0.33, a 3-class tie, up to 1.0) into a fixed band, by default 55%-92%.
    The RANKING is preserved — a raw 0.99 always maps to a higher
    calibrated value than a raw 0.80 — so "the model was more sure here
    than there" is still meaningful and correctly ordered. Only the
    absolute scale changes, from "almost always 97-100%" to a range a
    reader will find credible.

    This is a presentation-layer adjustment (a simple form of what's
    called "probability calibration" in ML), not a change to what the
    model actually predicts (risk_level and predicted_crime are
    completely unaffected). Worth stating plainly in your report as a
    deliberate design choice, rather than presenting the calibrated
    number as the model's raw, untouched output.
    """
    raw_prob = min(max(raw_prob, 0.0), 1.0)
    # Raw probability effectively ranges from ~1/3 (a 3-way tie) to 1.0.
    # Normalize that range to 0-1 before rescaling into [low, high].
    normalized = (raw_prob - (1 / 3)) / (1.0 - (1 / 3))
    normalized = min(max(normalized, 0.0), 1.0)
    return round(low + normalized * (high - low), 2)


def predict(
    city: str,
    area: str,
    time_of_day: str,
    day_of_week: str,
    crime_category: str = "",
) -> dict:
    """
    Predict crime risk for given inputs.
    Returns dict with risk_level, probability (0-1), predicted_crime.

    NOTE: crime type is now used as an input feature to the risk model
    (not just as a pass-through display value). Because crime type is
    optional in the request, it is resolved FIRST — either from the
    user's selection, or from the crime-type model — and only then fed
    into the risk-level prediction. Previously the risk model never saw
    crime type at all, so "Karachi/Clifton/Night/Friday" always produced
    the exact same risk level and probability regardless of whether the
    incident was Theft or Assault, which understated how much crime type
    actually affects risk.
    """
    _validate_inputs(city, area, time_of_day, day_of_week)

    model, crime_model, enc_city, enc_area, enc_time, enc_day, enc_crime, enc_risk = _get_model()

    city_clean = str(city).strip()
    area_clean = str(area).strip()
    time_clean = _normalize_time(time_of_day)
    day_clean = str(day_of_week).strip()

    city_idx = _encode(city_clean, enc_city, "city")
    area_idx = _encode(area_clean, enc_area, "area")
    time_idx = _encode(time_clean, enc_time, "time_of_day")
    day_idx = _encode(day_clean, enc_day, "day_of_week")
    report_count = _lookup_report_count(city_clean, area_clean, time_clean)

    # 5-feature vector used for crime-type prediction (unchanged) and as
    # the base of the risk vector below.
    X_base = np.array([[city_idx, area_idx, time_idx, day_idx, report_count]], dtype=np.float32)

    # ---- Step 1: resolve crime type FIRST -----------------------------
    if crime_category and crime_category.strip() in _valid_crime_types:
        predicted_crime = crime_category.strip()
    elif crime_model is not None and enc_crime is not None:
        if hasattr(crime_model, "predict_proba"):
            crime_probs = crime_model.predict_proba(X_base)[0]
            crime_idx_pred = int(np.argmax(crime_probs))
            predicted_crime = _to_crime_type(crime_idx_pred, enc_crime)
        else:
            crime_label = crime_model.predict(X_base)[0]
            predicted_crime = _to_crime_type(crime_label, enc_crime)
    else:
        predicted_crime = "Theft"

    # ---- Step 2: build the risk feature vector, including crime type --
    if _risk_uses_crime_feature and enc_crime is not None:
        crime_idx_for_risk = _encode(predicted_crime, enc_crime, "crime_type")
        X_risk = np.array(
            [[city_idx, area_idx, time_idx, day_idx, report_count, crime_idx_for_risk]],
            dtype=np.float32,
        )
    else:
        # Backward compatibility with a crime_model.pkl trained before this
        # change (5-feature risk model, no crime type input).
        X_risk = X_base

    if hasattr(model, "predict_proba"):
        probs = model.predict_proba(X_risk)[0]
        pred_idx = int(np.argmax(probs))
        prob = float(probs[pred_idx])
        pred_label = model.predict(X_risk)[0]
        if enc_risk is not None:
            risk_level = _to_risk_level(pred_idx, enc_risk)
        else:
            risk_level = _to_risk_level(pred_label, enc_risk)
    else:
        pred_label = model.predict(X_risk)[0]
        prob = 0.5
        risk_level = _to_risk_level(pred_label, enc_risk)

    if risk_level not in VALID_RISK_LEVELS:
        risk_level = "Medium"

    return {
        "risk_level": risk_level,
        "probability": _calibrate_probability(prob),
        "predicted_crime": predicted_crime,
    }
