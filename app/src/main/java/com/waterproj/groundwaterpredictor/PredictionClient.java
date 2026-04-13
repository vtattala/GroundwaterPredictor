package com.waterproj.groundwaterpredictor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class PredictionClient {

    public interface Callback {
        void onSuccess(String responseText);
        void onError(String message);
    }

    public void requestPrediction(String baseUrl, float[] inputValues, Callback callback) {
        HttpURLConnection connection = null;
        try {
            String normalizedUrl = baseUrl.trim();
            if (!normalizedUrl.endsWith("/")) {
                normalizedUrl = normalizedUrl + "/";
            }

            URL url = new URL(normalizedUrl + "predict");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject body = new JSONObject();
            JSONArray inputs = new JSONArray();
            for (float value : inputValues) {
                inputs.put(value);
            }
            body.put("input", inputs);

            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
            );
            writer.write(body.toString());
            writer.flush();
            writer.close();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                callback.onError("Server returned no response body.");
                return;
            }

            String response = readStream(stream);
            if (responseCode >= 200 && responseCode < 300) {
                callback.onSuccess(response);
            } else {
                callback.onError("HTTP " + responseCode + ": " + response);
            }
        } catch (Exception exception) {
            callback.onError(exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
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
}
