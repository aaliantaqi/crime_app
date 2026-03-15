"""
Crime Rate Prediction - FastAPI Backend.
Endpoints: /predict-crime, /crime-statistics, /crime-map-data, /report-crime.
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict, Any

# Import model after model.py is set up
try:
    from model import predict as ml_predict
    MODEL_AVAILABLE = True
except Exception as e:
    MODEL_AVAILABLE = False
    _model_error = str(e)

app = FastAPI(title="Crime Rate Prediction API", version="1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Request/Response models ---

class PredictRequest(BaseModel):
    city: str
    area: str
    time_of_day: str
    day_of_week: str
    crime_category: Optional[str] = ""


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


# --- In-memory storage for demo (replace with DB/Firestore in production) ---
reported_crimes: List[Dict[str, Any]] = []


@app.get("/")
def root():
    return {"message": "Crime Rate Prediction API", "docs": "/docs"}


@app.post("/predict-crime", response_model=PredictResponse)
def predict_crime(req: PredictRequest):
    """Predict crime risk for given city, area, time and day."""
    if not MODEL_AVAILABLE:
        # Fallback when model not trained yet
        return PredictResponse(
            risk_level="Medium",
            probability=0.5,
            predicted_crime="Theft",
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
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction error: {str(e)}")


@app.get("/crime-statistics")
def crime_statistics():
    """Return aggregated statistics for charts (monthly trend, category distribution, high risk areas)."""
    # Sample data; in production load from database or ML aggregates
    return {
        "monthly_trend": [
            {"month": "Jan", "count": 45},
            {"month": "Feb", "count": 52},
            {"month": "Mar", "count": 48},
            {"month": "Apr", "count": 61},
            {"month": "May", "count": 55},
            {"month": "Jun", "count": 70},
        ],
        "category_distribution": {
            "Robbery": 25,
            "Theft": 40,
            "Assault": 20,
            "Burglary": 15,
        },
        "high_risk_areas": [
            {"area": "Gulgasht", "risk_score": 0.82},
            {"area": "Hussain Agahi", "risk_score": 0.75},
            {"area": "Bosan Road", "risk_score": 0.68},
        ],
    }


@app.get("/crime-map-data")
def crime_map_data():
    """Return coordinates and crime data for map markers."""
    # Sample markers; in production load from DB
    return [
        {"location": "Gulgasht", "crime_type": "Robbery", "risk_level": "High", "lat": 30.1575, "lng": 71.5249},
        {"location": "Hussain Agahi", "crime_type": "Theft", "risk_level": "Medium", "lat": 30.1986, "lng": 71.4689},
        {"location": "Bosan Road", "crime_type": "Burglary", "risk_level": "High", "lat": 30.1790, "lng": 71.5090},
    ]


@app.post("/report-crime", response_model=ReportCrimeResponse)
def report_crime(req: ReportCrimeRequest):
    """Store user-reported crime (in production save to Firestore/DB)."""
    reported_crimes.append({
        "location": req.location,
        "crime_type": req.crime_type,
        "description": req.description,
        "date": req.date,
        "user_id": req.user_id,
    })
    return ReportCrimeResponse(success=True, message="Report saved successfully.")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
