# GroundwaterPredictor

Android Studio Java app that sends groundwater tensor data to your FastAPI backend.

## Backend contract

- Endpoint: `POST /predict`
- Body: `{"input": [6912 float values]}`
- Expected tensor shape on the server: `(1, 6, 32, 36, 1)`

## Backend URLs from your report

- Base URL: `http://127.0.0.1:8000`
- Docs: `http://127.0.0.1:8000/docs`
- Prediction endpoint: `POST http://127.0.0.1:8000/predict`

## Where to set the server URL

Two easy options are built in:

1. Edit `BuildConfig.SERVER_BASE_URL` in `app/build.gradle`
2. Paste the URL directly into the app's `Backend base URL` field at runtime

The project now defaults to `http://127.0.0.1:8000/` to match your backend report.

If you test from an Android emulator on the same PC, Android usually cannot reach your Windows localhost through `127.0.0.1`. In that case, change the app URL to `http://10.0.2.2:8000/`.

## Input options

- Paste 6912 comma-separated or space-separated float values
- Tap `Use Sample Data` to send a valid sample tensor

## Open in Android Studio

1. Open the `GroundwaterPredictor` folder
2. Let Gradle sync
3. Replace the backend URL if needed
4. Run on an emulator or Android device
