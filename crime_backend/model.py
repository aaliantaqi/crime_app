"""
Crime prediction ML model loader and inference.
Loads crime_model.pkl (trained by train_model.py) and predicts risk level, probability, crime type.
"""

import os
import joblib
import numpy as np
from pathlib import Path

# Default path to saved model (same directory as this file)
MODEL_DIR = Path(__file__).resolve().parent
MODEL_PATH = MODEL_DIR / "crime_model.pkl"

_model = None
_crime_model = None
_encoder_city = None
_encoder_area = None
_encoder_time = None
_encoder_day = None
_encoder_crime = None


def _get_model():
    """Load model and encoders from disk if not already loaded."""
    global _model, _crime_model, _encoder_city, _encoder_area, _encoder_time, _encoder_day, _encoder_crime
    if _model is not None:
        return _model, _crime_model, _encoder_city, _encoder_area, _encoder_time, _encoder_day, _encoder_crime
    if not MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Model file not found: {MODEL_PATH}. Run train_model.py first to generate crime_model.pkl."
        )
    data = joblib.load(MODEL_PATH)
    _model = data["model"]
    _crime_model = data.get("crime_model")
    _encoder_city = data.get("encoder_city")
    _encoder_area = data.get("encoder_area")
    _encoder_time = data.get("encoder_time")
    _encoder_day = data.get("encoder_day")
    _encoder_crime = data.get("encoder_crime")
    return _model, _crime_model, _encoder_city, _encoder_area, _encoder_time, _encoder_day, _encoder_crime


def _encode(value, encoder, default_idx=0):
    """Encode a categorical value; return default index if unknown."""
    if encoder is None:
        return default_idx
    try:
        return encoder.transform([value])[0]
    except Exception:
        return default_idx


def predict(city: str, area: str, time_of_day: str, day_of_week: str, crime_category: str = ""):
    """
    Predict crime risk for given inputs.
    Returns dict with risk_level ("Low"|"Medium"|"High"), probability (0-1), predicted_crime (str).
    """
    model, crime_model, enc_city, enc_area, enc_time, enc_day, enc_crime = _get_model()

    city_idx = _encode(city, enc_city)
    area_idx = _encode(area, enc_area)
    time_idx = _encode(time_of_day, enc_time)
    day_idx = _encode(day_of_week, enc_day)

    # Feature vector: [city_idx, area_idx, time_idx, day_idx]
    X = np.array([[city_idx, area_idx, time_idx, day_idx]], dtype=np.float32)

    # Risk: probability from model (if classifier has predict_proba)
    if hasattr(model, "predict_proba"):
        probs = model.predict_proba(X)[0]
        prob = float(np.max(probs))
        pred_label = model.predict(X)[0]
    else:
        pred_label = model.predict(X)[0]
        prob = 0.5

    risk_level = _to_risk_level(pred_label, prob)

    # Predicted crime type from separate crime classifier
    if crime_model is not None and enc_crime is not None:
        crime_label = crime_model.predict(X)[0]
        predicted_crime = _to_crime_type(crime_label, enc_crime)
    else:
        predicted_crime = "Theft"

    return {
        "risk_level": risk_level,
        "probability": round(prob, 2),
        "predicted_crime": predicted_crime,
    }


def _to_risk_level(label, prob):
    """Convert model output and probability to Low/Medium/High."""
    if isinstance(label, (int, float)):
        if label == 0:
            return "Low"
        if label == 1:
            return "Medium"
        if label == 2:
            return "High"
    if prob < 0.4:
        return "Low"
    if prob < 0.7:
        return "Medium"
    return "High"


def _to_crime_type(label, encoder):
    """Inverse transform label to crime type string if encoder has classes."""
    if encoder is None:
        return "Theft"
    try:
        classes = getattr(encoder, "classes_", None)
        if classes is None:
            cats = getattr(encoder, "categories_", None)
            classes = cats[0] if cats is not None and len(cats) else []
        if isinstance(label, (int, np.integer)) and 0 <= label < len(classes):
            return str(classes[label])
    except Exception:
        pass
    return "Theft"
