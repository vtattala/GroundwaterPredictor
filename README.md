# GroundwaterPredictor

Android Studio Java app that sends regional groundwater prediction requests to the deployed backend API.

## Backend contract

- Endpoint: `POST /predict`
- Body: `{"region": "California_North", "time_range": "6_months", "start_date": "2025-10-01", "end_date": "2026-04-01"}`

## Backend URL

- Prediction endpoint: `https://water-backend-1-klt6.onrender.com/predict`

## App flow

- User selects a region from a dropdown
- User selects a time range from a dropdown
- User selects start and end dates
- App sends a simplified prediction request to the backend
- Results are shown as selected region, groundwater status, and a heatmap placeholder for future rendering

## Open in Android Studio

1. Open the `GroundwaterPredictor` folder
2. Let Gradle sync
3. Run on an emulator or Android device
