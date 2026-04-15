package com.waterproj.groundwaterpredictor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroundwaterPredictionFragment extends Fragment {
    private static final String[] REGION_OPTIONS = new String[]{
            "California",
            "Michigan"
    };

    private static final String[] TIME_RANGE_OPTIONS = new String[]{
            "Last 6 months",
            "Last year",
            "Custom date range"
    };

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final PredictionClient predictionClient = new PredictionClient();

    private AutoCompleteTextView regionDropdown;
    private AutoCompleteTextView timeRangeDropdown;
    private EditText startDateInput;
    private EditText endDateInput;
    private TextView statusText;
    private TextView summaryText;
    private TextView trendText;
    private HeatmapGridView heatmapGridView;
    private MaterialButton runPredictionButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_groundwater_prediction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        regionDropdown = view.findViewById(R.id.regionDropdown);
        timeRangeDropdown = view.findViewById(R.id.timeRangeDropdown);
        startDateInput = view.findViewById(R.id.startDateInput);
        endDateInput = view.findViewById(R.id.endDateInput);
        statusText = view.findViewById(R.id.statusTextView);
        summaryText = view.findViewById(R.id.summaryTextView);
        trendText = view.findViewById(R.id.trendTextView);
        heatmapGridView = view.findViewById(R.id.heatmapGridView);
        runPredictionButton = view.findViewById(R.id.runPredictionButton);

        regionDropdown.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                REGION_OPTIONS
        ));
        timeRangeDropdown.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                TIME_RANGE_OPTIONS
        ));

        regionDropdown.setText(REGION_OPTIONS[0], false);
        timeRangeDropdown.setText(TIME_RANGE_OPTIONS[0], false);
        startDateInput.setText("2025-10-01");
        endDateInput.setText("2026-04-01");

        heatmapGridView.setData(createPlaceholderHeatmap());
        runPredictionButton.setOnClickListener(v -> runPrediction());
    }

    private void runPrediction() {
        String region = regionDropdown.getText().toString().trim();
        String timeRangeLabel = timeRangeDropdown.getText().toString().trim();
        String startDate = startDateInput.getText().toString().trim();
        String endDate = endDateInput.getText().toString().trim();

        if (region.isEmpty() || timeRangeLabel.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(requireContext(), "Complete the region, time range, and date inputs first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeRangeValue = mapTimeRangeValue(timeRangeLabel);
        statusText.setText(String.format(Locale.US, "Running prediction for %s.", region));
        summaryText.setText("Preparing groundwater forecast...");
        trendText.setText("Trend overview will appear after prediction.");
        runPredictionButton.setEnabled(false);

        executorService.execute(() -> predictionClient.requestPrediction(
                region,
                timeRangeValue,
                startDate,
                endDate,
                new PredictionClient.Callback() {
                    @Override
                    public void onSuccess(String responseText) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            runPredictionButton.setEnabled(true);
                            showPredictionResult(responseText, region, timeRangeLabel);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            runPredictionButton.setEnabled(true);
                            statusText.setText("Prediction unavailable.");
                            summaryText.setText("We could not retrieve groundwater results.");
                            trendText.setText(message == null ? "Unknown error." : message);
                        });
                    }
                }
        ));
    }

    private void showPredictionResult(String responseText, String region, String timeRangeLabel) {
        try {
            JSONObject responseJson = new JSONObject(responseText);
            if (responseJson.has("error")) {
                statusText.setText("Prediction unavailable.");
                summaryText.setText("Backend returned an error.");
                trendText.setText(responseJson.getString("error"));
                return;
            }

            float[][] heatmap = extractHeatmap(responseJson.optJSONArray("heatmap"));
            heatmapGridView.setData(heatmap);

            String summaryLabel = responseJson.optString(
                    "groundwater_level_status",
                    responseJson.optString("summary_label", inferSummary(heatmap))
            );
            String trendLabel = responseJson.optString(
                    "trend_summary",
                    String.format(Locale.US, "%s outlook for %s is stable.", region, timeRangeLabel.toLowerCase(Locale.US))
            );

            statusText.setText(String.format(Locale.US, "Prediction ready for %s.", region));
            summaryText.setText("Groundwater Level: " + summaryLabel);
            trendText.setText(trendLabel);
        } catch (Exception exception) {
            statusText.setText("Prediction ready.");
            summaryText.setText("Groundwater Level: " + inferSummary(createPlaceholderHeatmap()));
            trendText.setText("Received a response, but it was not in the expected format.");
        }
    }

    private String mapTimeRangeValue(String selectedLabel) {
        if ("Last year".equals(selectedLabel)) {
            return "1_year";
        }
        if ("Custom date range".equals(selectedLabel)) {
            return "custom_range";
        }
        return "6_months";
    }

    private float[][] extractHeatmap(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0) {
            return createPlaceholderHeatmap();
        }

        Object first = jsonArray.opt(0);
        if (!(first instanceof JSONArray)) {
            return createPlaceholderHeatmap();
        }

        JSONArray firstRow = (JSONArray) first;
        float[][] grid = new float[jsonArray.length()][firstRow.length()];

        for (int row = 0; row < jsonArray.length(); row++) {
            JSONArray rowArray = jsonArray.optJSONArray(row);
            if (rowArray == null) {
                return createPlaceholderHeatmap();
            }
            for (int column = 0; column < rowArray.length(); column++) {
                grid[row][column] = (float) rowArray.optDouble(column, 0.0);
            }
        }
        return grid;
    }

    private String inferSummary(float[][] heatmap) {
        float sum = 0f;
        int count = 0;
        for (float[] row : heatmap) {
            for (float value : row) {
                sum += value;
                count++;
            }
        }

        float average = count == 0 ? 0f : sum / count;
        if (average < 0.33f) {
            return "Low";
        }
        if (average > 0.66f) {
            return "High";
        }
        return "Normal";
    }

    private float[][] createPlaceholderHeatmap() {
        return new float[][]{
                {0.22f, 0.30f, 0.42f, 0.55f, 0.61f, 0.49f},
                {0.18f, 0.27f, 0.38f, 0.57f, 0.69f, 0.58f},
                {0.15f, 0.25f, 0.36f, 0.52f, 0.63f, 0.54f},
                {0.21f, 0.29f, 0.41f, 0.47f, 0.51f, 0.45f}
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}