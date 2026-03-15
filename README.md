# Crime Rate Prediction App

Final Year BS-IT project: Android app that predicts crime risk using ML and displays results on maps and charts.

## System Overview

- **Android App (Java)** – UI, auth, prediction form, map, statistics, report crime
- **Python Backend (FastAPI)** – REST API and ML inference
- **Firebase** – Authentication and Firestore (users, reports, prediction_history)
- **Google Maps** – Crime markers and risk visualization

Flow: User enters location/time → App calls backend → ML model predicts risk → Result shown with maps/charts.

---

## 1. Backend (Python FastAPI)

### Location

```
crime_backend/
├── app.py              # FastAPI app and endpoints
├── model.py            # Loads crime_model.pkl, runs prediction
├── train_model.py      # Trains Random Forest + XGBoost, saves model
├── requirements.txt
├── data/
│   └── sample_crime_data.csv
└── crime_model.pkl     # Generated after training
```

### Setup and run

1. **Create virtual environment (recommended)**

   ```bash
   cd "crime_backend"
   python -m venv venv
   # Windows:
   venv\Scripts\activate
   # macOS/Linux:
   source venv/bin/activate
   ```

2. **Install dependencies**

   ```bash
   pip install -r requirements.txt
   ```

3. **Train the model (creates `crime_model.pkl`)**

   ```bash
   python train_model.py
   ```

4. **Start the API server**

   ```bash
   python app.py
   # Or: uvicorn app:app --host 0.0.0.0 --port 8000
   ```

   API will be at: **http://localhost:8000**  
   Docs: http://localhost:8000/docs

### Endpoints

| Method | Endpoint            | Description                    |
|--------|---------------------|--------------------------------|
| POST   | `/predict-crime`    | Predict risk (city, area, time, day) |
| GET    | `/crime-statistics` | Data for charts                |
| GET    | `/crime-map-data`   | Markers for map                |
| POST   | `/report-crime`     | Store reported crime           |

---

## 2. Android App

### Location

```
CrimePredictionApp/
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── java/com/crimeprediction/app/
│   │   │   ├── activities/    # Splash, Login, Register, Dashboard, Prediction, Map, Statistics, ReportCrime
│   │   │   ├── models/        # CrimeData, PredictionResult, UserModel
│   │   │   ├── network/       # ApiClient, ApiService, Retrofit
│   │   │   └── utils/         # FirebaseHelper, LocationHelper
│   │   ├── res/
│   │   │   ├── layout/        # activity_*.xml
│   │   │   ├── values/        # colors, strings, themes
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── google-services.json  # Add your file here (see Firebase setup)
├── build.gradle
└── settings.gradle
```

### Connecting the app to the backend

- **Emulator:** API base URL is set to `http://10.0.2.2:8000/` in `ApiClient.java` (10.0.2.2 = host machine’s localhost).
- **Physical device:** Use your PC’s LAN IP and port 8000, e.g. `http://192.168.1.100:8000/`.  
  Update `BASE_URL` in:

  `app/src/main/java/com/crimeprediction/app/network/ApiClient.java`

  Then run the backend on the same machine and ensure firewall allows port 8000.

### Firebase setup

1. Create a project at [Firebase Console](https://console.firebase.google.com).
2. Add an Android app with package name: **com.crimeprediction.app**.
3. Download `google-services.json` and place it in:

   `CrimePredictionApp/app/google-services.json`

   (Replace or remove `app/google-services.json.example` as needed.)
4. Enable **Authentication** → Email/Password.
5. Create a **Firestore** database (test mode is fine for development).
6. Optional: enable **Google Maps** and add an API key in the Android app (see Maps section below).

### Google Maps (for Map screen)

1. In [Google Cloud Console](https://console.cloud.google.com), enable **Maps SDK for Android** (and optionally Places/Location).
2. Create an API key and restrict it to your app’s package name and SHA-1.
3. In `app/src/main/AndroidManifest.xml`, inside `<application>`, add:

   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_MAPS_API_KEY" />
   ```

   Replace `YOUR_MAPS_API_KEY` with your key.

### Build and run (Android Studio)

1. Open **CrimePredictionApp** in Android Studio.
2. Sync Gradle.
3. Add `google-services.json` and (if needed) Maps API key as above.
4. Run on an emulator or device (minSdk 24).

---

## 3. App screens

| Screen           | Purpose                                      |
|------------------|----------------------------------------------|
| Splash           | Logo and title, 2 s then Login                |
| Login / Register | Firebase Auth, validation                     |
| Dashboard        | 4 cards: Predict, Map, Statistics, Report     |
| Prediction       | City, area, time, day → “Predict” → risk card |
| Map              | Google Map with crime/risk markers           |
| Statistics       | Line, pie, bar charts from API                |
| Report Crime     | Location, type, description, date → Firestore|

---

## 4. Firebase collections (Firestore)

- **users** – User profile (name, email, createdAt).
- **reports** – Crime reports (location, crimeType, description, date, userId).
- **prediction_history** – Each prediction (userId, city, area, riskLevel, probability, predictedCrime, timestamp).

---

## 5. Sample dataset and ML

- **Sample data:** `crime_backend/data/sample_crime_data.csv`  
  Columns: city, area, time_of_day, day_of_week, crime_type, risk_level.

- **Training:** `train_model.py` uses Random Forest and XGBoost (risk level) and a separate classifier for crime type, then saves `crime_model.pkl`.

- **Inference:** `model.py` loads `crime_model.pkl` and exposes `predict(city, area, time_of_day, day_of_week)` used by `app.py` in `/predict-crime`.

---

## 6. Tech stack summary

- **Android:** Java, XML layouts, Material Design, Retrofit, Firebase Auth/Firestore, MPAndroidChart, Google Maps.
- **Backend:** Python, FastAPI, scikit-learn, XGBoost.
- **Database:** Firebase Firestore.

---

## 7. Troubleshooting

- **App cannot reach API:** Check BASE_URL (10.0.2.2:8000 for emulator; LAN IP for device). Ensure backend is running and firewall allows port 8000.
- **Prediction returns generic/fallback:** Run `python train_model.py` in `crime_backend` so `crime_model.pkl` exists.
- **Login/Register fails:** Ensure Firebase Auth (Email/Password) is enabled and `google-services.json` is in `app/`.
- **Map blank or crashes:** Add Maps API key in `AndroidManifest.xml` and enable Maps SDK for Android in Google Cloud.
