# AGENT.md

## Project Name

Crime Rate Prediction App

## Project Type

Final Year BS-IT Mobile Application with AI Crime Prediction System

## Overview

This project is an Android mobile application that predicts crime risk in different areas using machine learning. The application allows users to view predicted crime levels, visualize crime hotspots on maps, analyze statistics, and report crimes.

The system consists of three main layers:

1. Android Mobile Application (Frontend)
2. Python Backend API
3. Machine Learning Prediction Models

The AI agent must always maintain this architecture.

---

# Technology Stack

Frontend:

* Android Studio
* Java ONLY
* XML Layouts
* Material Design Components

Backend:

* Python
* FastAPI (preferred) or Flask

Machine Learning:

* Scikit-learn
* Random Forest
* XGBoost
* LSTM for time-series forecasting

Database:

* Firebase Authentication
* Firebase Firestore

Networking:

* Retrofit (Android API communication)

Visualization:

* Google Maps API
* MPAndroidChart (charts and graphs)

---

# Project Architecture

The system must follow this flow:

Android App → API Request → Python Backend → Machine Learning Model → Prediction → Response to Android → UI Display

Never bypass the backend when making predictions.

All predictions must be processed on the Python backend.

---

# Android Project Structure

The Android code must follow a modular structure.

com.crimeprediction.app

activities/

* SplashActivity
* LoginActivity
* RegisterActivity
* DashboardActivity
* PredictionActivity
* MapActivity
* StatisticsActivity
* ReportCrimeActivity

models/

* CrimeData
* PredictionResult
* UserModel

network/

* ApiClient
* ApiService
* ApiResponse

utils/

* FirebaseHelper
* LocationHelper

res/layout/

* activity_splash.xml
* activity_login.xml
* activity_register.xml
* activity_dashboard.xml
* activity_prediction.xml
* activity_map.xml
* activity_statistics.xml
* activity_report.xml

---

# Android UI Design Rules

The UI must follow a modern minimal Material design.

Primary Color:
#1E3A8A (Deep Blue)

Secondary Color:
#3B82F6 (Sky Blue)

Accent Color:
#F97316 (Orange)

Background Color:
#F3F4F6 (Light Gray)

Text Color:
#111827 (Dark Gray)

UI components should include:

* Card based layouts
* Rounded corners (16dp)
* Soft shadows
* Smooth animations
* Loading indicators for API requests

---

# Core Application Features

The app must include the following features.

## 1 Splash Screen

Display app logo and title for 2 seconds before navigating to login.

---

## 2 Authentication System

Use Firebase Authentication.

User can:

* Register account
* Login
* Logout

Fields:

Name
Email
Password

Add validation for input fields.

---

## 3 Dashboard

The dashboard is the main navigation screen.

Display 4 cards:

* Predict Crime
* Crime Map
* Crime Statistics
* Report Crime

Each card opens a new activity.

---

## 4 Crime Prediction Module

Users input:

City
Area
Time of Day
Day of Week

When the user presses **Predict Crime Risk**, the app must call the backend API.

Display result:

Risk Level (Low / Medium / High)

Probability Score

Predicted Crime Type

---

## 5 Crime Map Visualization

Use Google Maps API.

Display:

* Crime markers
* High risk locations
* Heatmap visualization

Each marker should display:

Crime type
Risk level
Area name

---

## 6 Crime Statistics

Display charts using MPAndroidChart.

Charts include:

* Crime trend line chart
* Crime category pie chart
* Area risk bar chart

Charts should load data from the backend API.

---

## 7 Crime Reporting

Users can report crimes.

Fields:

Location
Crime Type
Description
Date

Reports must be stored in Firebase Firestore.

---

# Backend API Architecture

Backend folder:

crime_backend/

Files:

app.py
model.py
train_model.py
requirements.txt

---

# API Endpoints

POST /predict-crime

Input JSON:

{
"city": "string",
"area": "string",
"time_of_day": "string",
"day_of_week": "string"
}

Response:

{
"risk_level": "Low | Medium | High",
"probability": 0.0,
"predicted_crime": "string"
}

---

GET /crime-statistics

Returns aggregated crime statistics for charts.

---

GET /crime-map-data

Returns location coordinates and crime data for map markers.

---

POST /report-crime

Stores crime report submitted by user.

---

# Machine Learning Requirements

The system must train crime prediction models using a dataset containing:

Location
Crime type
Date
Time
Area

Algorithms:

Random Forest → classification
XGBoost → high accuracy prediction
LSTM → time-series crime forecasting

The trained model must be saved as:

crime_model.pkl

The backend API must load the model at startup.

---

# Android to Backend Communication

Use Retrofit.

All requests must be sent in JSON format.

Handle errors properly:

* network errors
* API failures
* invalid responses

Show loading indicators while waiting for prediction.

---

# Firebase Database Structure

Firestore collections:

users/
reports/
prediction_history/

Prediction history must store:

User ID
Area
Prediction result
Timestamp

---

# Coding Guidelines

The AI agent must follow these coding rules:

1. Keep classes small and modular.
2. Separate UI logic and network logic.
3. Comment important functions.
4. Use descriptive variable names.
5. Avoid duplicated code.

---

# Development Workflow

The AI agent must generate the project in the following order:

1. Android project structure
2. Splash screen
3. Authentication screens
4. Dashboard
5. Prediction module
6. Backend API
7. Machine learning model
8. Maps integration
9. Charts and statistics
10. Firebase integration

Never generate everything at once.

Build modules step by step.

---

# Expected Output

The AI agent should generate:

* Complete Android Java classes
* XML layout files
* Backend Python API
* Machine learning training script
* Model loading system
* Firebase integration
* API communication layer

Each file should include clear comments explaining its purpose.

The generated code must be clean, modular, and suitable for a university final year project.
