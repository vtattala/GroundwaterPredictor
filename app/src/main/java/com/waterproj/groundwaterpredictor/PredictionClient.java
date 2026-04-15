package com.waterproj.groundwaterpredictor;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class PredictionClient {
    private static final String TAG = "PredictionClient";
    private static final String ROOT_URL = "https://water-backend-1-klt6.onrender.com/";
    private static final String PREDICTION_URL = "https://water-backend-1-klt6.onrender.com/predict";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 90000;

    public interface Callback {
        void onSuccess(String responseText);
        void onError(String message);
    }

    public void requestPrediction(
            String region,
            String timeRange,
            String startDate,
            String endDate,
            Callback callback
    ) {
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            warmUpBackend();

            URL url = new URL(PREDICTION_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "GroundwaterPredictor-Android/1.0");

            JSONObject body = new JSONObject();
            body.put("region", region);
            body.put("time_range", timeRange);
            body.put("start_date", startDate);
            body.put("end_date", endDate);

            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
            );
            writer.write(body.toString());
            writer.flush();
            writer.close();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                callback.onError("Server returned no response body.");
                return;
            }

            String response = readStream(stream);
            Log.d(TAG, "HTTP " + responseCode + " response: " + response);
            if (responseCode >= 200 && responseCode < 300) {
                callback.onSuccess(response);
            } else {
                callback.onError("HTTP " + responseCode + ": " + response);
            }
        } catch (Exception exception) {
            Log.e(TAG, "Prediction request failed", exception);
            callback.onError(exception.getMessage());
        } finally {
            closeQuietly(stream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void warmUpBackend() {
        HttpURLConnection warmupConnection = null;
        InputStream warmupStream = null;
        try {
            URL warmupUrl = new URL(ROOT_URL);
            warmupConnection = (HttpURLConnection) warmupUrl.openConnection();
            warmupConnection.setRequestMethod("GET");
            warmupConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            warmupConnection.setReadTimeout(READ_TIMEOUT_MS);
            warmupConnection.setRequestProperty("Accept", "application/json");
            warmupConnection.setRequestProperty("User-Agent", "GroundwaterPredictor-Android/1.0");

            int warmupCode = warmupConnection.getResponseCode();
            warmupStream = warmupCode >= 200 && warmupCode < 300
                    ? warmupConnection.getInputStream()
                    : warmupConnection.getErrorStream();

            if (warmupStream != null) {
                String warmupResponse = readStream(warmupStream);
                Log.d(TAG, "Warmup HTTP " + warmupCode + ": " + warmupResponse);
            }
        } catch (Exception exception) {
            Log.w(TAG, "Backend warmup failed, continuing with predict request", exception);
        } finally {
            closeQuietly(warmupStream);
            if (warmupConnection != null) {
                warmupConnection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            Log.w(TAG, "Failed to close network resource", ignored);
        }
    }
}
