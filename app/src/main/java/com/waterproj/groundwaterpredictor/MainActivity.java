package com.waterproj.groundwaterpredictor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int EXPECTED_VALUE_COUNT = 6 * 32 * 36;

    private EditText serverUrlInput;
    private EditText inputValuesEditText;
    private TextView statusTextView;
    private TextView resultTextView;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PredictionClient predictionClient = new PredictionClient();

    private float[] sampleInput;
    private boolean usingSampleInput = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverUrlInput = findViewById(R.id.serverUrlInput);
        inputValuesEditText = findViewById(R.id.inputValuesEditText);
        statusTextView = findViewById(R.id.statusTextView);
        resultTextView = findViewById(R.id.resultTextView);
        Button sampleButton = findViewById(R.id.sampleButton);
        Button clearButton = findViewById(R.id.clearButton);
        Button predictButton = findViewById(R.id.predictButton);

        serverUrlInput.setText(BuildConfig.SERVER_BASE_URL);

        sampleButton.setOnClickListener(view -> loadSampleInput());
        clearButton.setOnClickListener(view -> clearInputs());
        predictButton.setOnClickListener(view -> runPrediction());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }

    private void loadSampleInput() {
        sampleInput = new float[EXPECTED_VALUE_COUNT];
        for (int index = 0; index < sampleInput.length; index++) {
            sampleInput[index] = (index % 100) / 100.0f;
        }

        usingSampleInput = true;
        inputValuesEditText.setText("");
        statusTextView.setText("Sample tensor loaded with 6912 values.");
        resultTextView.setText("Prediction output will appear here.");
    }

    private void clearInputs() {
        usingSampleInput = false;
        sampleInput = null;
        inputValuesEditText.setText("");
        resultTextView.setText("Prediction output will appear here.");
        statusTextView.setText("Ready");
    }

    private void runPrediction() {
        String baseUrl = serverUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(baseUrl)) {
            Toast.makeText(this, "Enter your backend base URL first.", Toast.LENGTH_SHORT).show();
            return;
        }

        float[] valuesToSend;
        if (usingSampleInput && sampleInput != null) {
            valuesToSend = sampleInput;
        } else {
            try {
                valuesToSend = parseInputValues(inputValuesEditText.getText().toString());
            } catch (IllegalArgumentException exception) {
                statusTextView.setText(exception.getMessage());
                return;
            }
        }

        statusTextView.setText(String.format(
                Locale.US,
                "Sending %d values to %s",
                valuesToSend.length,
                baseUrl
        ));
        resultTextView.setText("Waiting for model response...");

        executorService.execute(() -> predictionClient.requestPrediction(baseUrl, valuesToSend, new PredictionClient.Callback() {
            @Override
            public void onSuccess(String responseText) {
                mainHandler.post(() -> showPredictionResult(responseText));
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    statusTextView.setText("Prediction failed.");
                    resultTextView.setText(message == null ? "Unknown error" : message);
                });
            }
        }));
    }

    private float[] parseInputValues(String rawInput) {
        String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Paste 6912 float values or tap Use Sample Data."
            );
        }

        String normalized = trimmed
                .replace("[", " ")
                .replace("]", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace(",", " ");

        String[] parts = normalized.trim().split("\\s+");
        if (parts.length != EXPECTED_VALUE_COUNT) {
            throw new IllegalArgumentException(
                    "Expected 6912 values, but found " + parts.length + "."
            );
        }

        float[] parsed = new float[EXPECTED_VALUE_COUNT];
        for (int index = 0; index < parts.length; index++) {
            try {
                parsed[index] = Float.parseFloat(parts[index]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid number at position " + (index + 1) + ": " + parts[index]
                );
            }
        }
        usingSampleInput = false;
        return parsed;
    }

    private void showPredictionResult(String responseText) {
        try {
            JSONObject responseJson = new JSONObject(responseText);
            if (responseJson.has("error")) {
                statusTextView.setText("Server returned an error.");
                resultTextView.setText(responseJson.getString("error"));
                return;
            }

            statusTextView.setText("Prediction complete.");
            if (responseJson.has("prediction")) {
                JSONArray predictionArray = responseJson.getJSONArray("prediction");
                resultTextView.setText(predictionArray.toString(2));
            } else {
                resultTextView.setText(responseJson.toString(2));
            }
        } catch (Exception exception) {
            statusTextView.setText("Prediction complete.");
            resultTextView.setText(responseText);
        }
    }
}
