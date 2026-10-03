# Running the Crime Prediction Backend

## 1. Setup

Open a terminal in the `crime_backend/IMP_Backend` folder.

Create and activate a virtual environment (optional but recommended):

```bash
python -m venv venv
venv\Scripts\activate    # Windows
# or: source venv/bin/activate   # macOS/Linux
```

Install dependencies:

```bash
pip install -r requirements.txt
```

## 2. Build training data from Firebase export

Convert `reports.json` → `data/sample_crime_data.csv` with pattern-based risk labels:

```bash
python convert_firebase_to_csv.py
```

Risk is assigned from **time_of_day + day_of_week + area density**, not raw report counts.

## 3. Train the ML model

Generates `crime_model.pkl`:

```bash
python train_model.py
```

This prints cross-validation accuracy and a per-class classification report for both risk and crime-type models.

## 4. Start the API server

```bash
python app.py
```

Server runs at **http://localhost:8000**.  
API docs: http://localhost:8000/docs

## 5. Test predictions locally

```bash
curl -X POST http://localhost:8000/predict-crime ^
  -H "Content-Type: application/json" ^
  -d "{\"city\":\"Karachi\",\"area\":\"Clifton\",\"time_of_day\":\"Night\",\"day_of_week\":\"Friday\",\"crime_category\":\"\"}"
```

Expected: `"risk_level":"High"` for weekend night in a high-density area.

```bash
curl -X POST http://localhost:8000/predict-crime ^
  -H "Content-Type: application/json" ^
  -d "{\"city\":\"Islamabad\",\"area\":\"F-7\",\"time_of_day\":\"Morning\",\"day_of_week\":\"Monday\",\"crime_category\":\"\"}"
```

Expected: `"risk_level":"Low"` for weekday morning.

## 6. Deploy to Hugging Face Spaces

Upload these files to your Space repo:

- `app.py`, `model.py`, `train_model.py`, `convert_firebase_to_csv.py`
- `requirements.txt`, `reports.json`
- `data/sample_crime_data.csv` (after running convert script)
- `crime_model.pkl` (after running train script)

Or run convert + train in the Space build step, then commit the generated `.pkl`.

## 7. Android app connectivity

- Emulator: `http://10.0.2.2:8000/`
- Physical device: your computer's LAN IP (e.g. `http://192.168.1.100:8000/`)
- Hugging Face: `https://aaliannnn-crime-backend.hf.space`

Update `ApiClient.BASE_URL` in the Android project accordingly.
