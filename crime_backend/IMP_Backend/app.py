"""
Crime Rate Prediction - FastAPI Backend.
Endpoints: /predict-crime, /crime-statistics, /crime-map-data, /report-crime.
"""

import datetime
import json
import logging
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator

try:
    from model import predict as ml_predict

    MODEL_AVAILABLE = True
except Exception as e:
    MODEL_AVAILABLE = False
    _model_error = str(e)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

SCRIPT_DIR = Path(__file__).resolve().parent
REPORTS_PATH = SCRIPT_DIR / "reports.json"

VALID_CRIME_TYPES = {"Theft", "Assault", "Fraud", "Vandalism"}
VALID_TIMES = {"Morning", "Afternoon", "Night"}
WEEKEND_DAYS = {"Friday", "Saturday"}

app = FastAPI(title="Crime Rate Prediction API", version="1.1")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

reported_crimes: List[Dict[str, Any]] = []
_reports_cache: Optional[List[Dict[str, Any]]] = None


class PredictRequest(BaseModel):
    city: str
    area: str
    time_of_day: str
    day_of_week: str
    crime_category: Optional[str] = ""

    @field_validator("city", "area", "time_of_day", "day_of_week")
    @classmethod
    def not_empty(cls, v: str) -> str:
        if not v or not str(v).strip():
            raise ValueError("Field cannot be empty")
        return str(v).strip()

    @field_validator("time_of_day")
    @classmethod
    def valid_time(cls, v: str) -> str:
        normalized = v.strip().lower()
        mapping = {"morning": "Morning", "afternoon": "Afternoon", "night": "Night"}
        if normalized in mapping:
            return mapping[normalized]
        if v.strip() not in VALID_TIMES:
            raise ValueError(f"time_of_day must be one of {sorted(VALID_TIMES)}")
        return v.strip()


class PredictResponse(BaseModel):
    risk_level: str
    probability: float
    predicted_crime: str


class ReportCrimeRequest(BaseModel):
    location: str
    crime_type: str
    description: str
    date: str
    user_id: Optional[str] = None


class ReportCrimeResponse(BaseModel):
    success: bool
    message: str


def _normalize_crime_type(raw: str) -> str:
    if not raw:
        return ""
    r = raw.strip().lower()
    if "theft" in r:
        return "Theft"
    if "assault" in r:
        return "Assault"
    if "fraud" in r:
        return "Fraud"
    if "vandalism" in r:
        return "Vandalism"
    return ""


def load_reports() -> List[Dict[str, Any]]:
    global _reports_cache
    if _reports_cache is not None:
        return _reports_cache

    if not REPORTS_PATH.exists():
        _reports_cache = []
        return _reports_cache

    with open(REPORTS_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    reports_dict = data.get("reports", data)
    reports = []
    for _, report in reports_dict.items():
        crime_type = _normalize_crime_type(report.get("crimeType", ""))
        if crime_type not in VALID_CRIME_TYPES:
            continue
        reports.append(
            {
                "city": (report.get("city") or report.get("location") or "").strip(),
                "area": (report.get("area") or "").strip(),
                "crime_type": crime_type,
                "lat": report.get("lat"),
                "lng": report.get("lng"),
                "timestamp": report.get("timestamp"),
            }
        )

    _reports_cache = reports
    return reports


def _month_label(timestamp_ms) -> str:
    if not timestamp_ms:
        return "Unknown"
    dt = datetime.datetime.fromtimestamp(timestamp_ms / 1000)
    return dt.strftime("%b")


def _area_risk_score(area: str, reports: List[Dict[str, Any]]) -> float:
    """Compute a 0-1 risk score from report volume and night/weekend share."""
    area_reports = [r for r in reports if r.get("area") == area]
    if not area_reports:
        return 0.0

    night_count = 0
    weekend_night = 0
    for r in area_reports:
        ts = r.get("timestamp")
        if not ts:
            continue
        dt = datetime.datetime.fromtimestamp(ts / 1000)
        hour = dt.hour
        day = dt.strftime("%A")
        is_night = hour >= 18 or hour < 5
        if is_night:
            night_count += 1
            if day in WEEKEND_DAYS:
                weekend_night += 1

    volume_score = min(len(area_reports) / 15.0, 1.0)
    night_ratio = night_count / len(area_reports)
    weekend_ratio = weekend_night / max(night_count, 1)
    return round(min(1.0, 0.4 * volume_score + 0.35 * night_ratio + 0.25 * weekend_ratio), 2)


@app.get("/")
def root():
    return {
        "message": "Crime Rate Prediction API",
        "docs": "/docs",
        "model_loaded": MODEL_AVAILABLE,
    }


@app.post("/predict-crime", response_model=PredictResponse)
def predict_crime(req: PredictRequest):
    if not MODEL_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail=f"ML model not available: {_model_error if not MODEL_AVAILABLE else 'unknown error'}",
        )
    try:
        result = ml_predict(
            city=req.city,
            area=req.area,
            time_of_day=req.time_of_day,
            day_of_week=req.day_of_week,
            crime_category=req.crime_category or "",
        )
        return PredictResponse(
            risk_level=result["risk_level"],
            probability=result["probability"],
            predicted_crime=result["predicted_crime"],
        )
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        logger.exception("Prediction failed")
        raise HTTPException(status_code=500, detail=f"Prediction error: {str(e)}")

@app.get("/crime-statistics")
def crime_statistics(city: Optional[str] = None, area: Optional[str] = None):
    reports = load_reports()

    if city:
        reports = [r for r in reports if r.get("city", "").strip().lower() == city.strip().lower()]
    if area and area.lower() not in ("all areas", "select area"):
        reports = [r for r in reports if r.get("area", "").strip().lower() == area.strip().lower()]

    if not reports:
        
        return {
            "monthly_trend": [],
            "category_distribution": {t: 0 for t in sorted(VALID_CRIME_TYPES)},
            "high_risk_areas": [],
        }

    month_counts: Counter = Counter()
    for r in reports:
        month_counts[_month_label(r.get("timestamp"))] += 1

    month_order = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    monthly_trend = [
        {"month": m, "count": month_counts.get(m, 0)}
        for m in month_order
        if month_counts.get(m, 0) > 0
    ]

    category_distribution = Counter(r["crime_type"] for r in reports)
    for crime in VALID_CRIME_TYPES:
        category_distribution.setdefault(crime, 0)

    area_scores = defaultdict(float)
    for r in reports:
        area = r.get("area")
        if area:
            area_scores[area] = _area_risk_score(area, reports)

    high_risk_areas = [
        {"area": area, "risk_score": score}
        for area, score in sorted(area_scores.items(), key=lambda x: x[1], reverse=True)[:5]
    ]

    return {
        "monthly_trend": monthly_trend,
        "category_distribution": dict(category_distribution),
        "high_risk_areas": high_risk_areas,
    }


@app.get("/crime-map-data")
def crime_map_data():
    reports = load_reports()
    area_seen = set()
    markers = []

    for r in reports:
        area = r.get("area")
        if not area or area in area_seen:
            continue
        if r.get("lat") is None or r.get("lng") is None:
            continue

        area_seen.add(area)
        score = _area_risk_score(area, reports)
        if score >= 0.7:
            risk_level = "High"
        elif score >= 0.4:
            risk_level = "Medium"
        else:
            risk_level = "Low"

        markers.append(
            {
                "location": area,
                "crime_type": r["crime_type"],
                "risk_level": risk_level,
                "lat": r["lat"],
                "lng": r["lng"],
            }
        )

    return markers


@app.post("/report-crime", response_model=ReportCrimeResponse)
def report_crime(req: ReportCrimeRequest):
    reported_crimes.append(
        {
            "location": req.location,
            "crime_type": req.crime_type,
            "description": req.description,
            "date": req.date,
            "user_id": req.user_id,
        }
    )
    return ReportCrimeResponse(success=True, message="Report saved successfully.")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
