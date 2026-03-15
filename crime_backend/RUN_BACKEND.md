# Running the Crime Prediction Backend

1. Open a terminal in the `crime_backend` folder.

2. Create and activate a virtual environment (optional but recommended):
   ```bash
   python -m venv venv
   venv\Scripts\activate    # Windows
   # or: source venv/bin/activate   # macOS/Linux
   ```

3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

4. Train the ML model (generates `crime_model.pkl`):
   ```bash
   python train_model.py
   ```

5. Start the API server:
   ```bash
   python app.py
   ```
   Server runs at **http://localhost:8000**.  
   API docs: http://localhost:8000/docs

6. For Android emulator, the app uses `http://10.0.2.2:8000/` to reach this server.  
   For a physical device, use your computer's IP (e.g. `http://192.168.1.100:8000/`) and update `ApiClient.BASE_URL` in the Android project.
