package com.waterproj.groundwaterpredictor;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoilMoistureFragment extends Fragment {

    private static final String MODEL_FILE_NAME = "soil_moisture_model.tflite";
    private static final int INPUT_TIME_STEPS = 5;
    private static final int GRID_SIZE = 32;
    private static final int CHANNELS = 1;
    private static final int HEATMAP_SCALE = 18;

    private static final float NORMALIZATION_MEAN = 1.2838430e-07f;
    private static final float NORMALIZATION_STD = 1.0f;
    private static final float DEFAULT_TEMPERATURE_C = 20.0f;
    private static final float TEMPERATURE_FACTOR = 0.0045f;

    private final DecimalFormat decimalFormat = new DecimalFormat("0.000");

    private final List<String> regionOptions = Arrays.asList("California", "Michigan");
    private final List<String> soilTypeOptions = Arrays.asList("Loamy", "Sandy", "Clay");

    private Interpreter interpreter;
    private ExecutorService executorService;

    private MaterialAutoCompleteTextView regionDropdown;
    private TextInputEditText day1Input;
    private TextInputEditText day2Input;
    private TextInputEditText day3Input;
    private TextInputEditText day4Input;
    private TextInputEditText day5Input;
    private MaterialAutoCompleteTextView soilTypeDropdown;
    private TextInputEditText temperatureInput;
    private Button runButton;
    private ProgressBar loadingBar;
    private MaterialCardView resultCard;
    private TextView statusText;
    private TextView helperText;
    private TextView detailText;
    private ImageView heatmapImage;

    public SoilMoistureFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_soil_moisture_prediction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        regionDropdown = view.findViewById(R.id.regionDropdown);
        day1Input = view.findViewById(R.id.day1Input);
        day2Input = view.findViewById(R.id.day2Input);
        day3Input = view.findViewById(R.id.day3Input);
        day4Input = view.findViewById(R.id.day4Input);
        day5Input = view.findViewById(R.id.day5Input);
        soilTypeDropdown = view.findViewById(R.id.soilTypeDropdown);
        temperatureInput = view.findViewById(R.id.temperatureInput);
        runButton = view.findViewById(R.id.runModelButton);
        loadingBar = view.findViewById(R.id.loadingBar);
        resultCard = view.findViewById(R.id.resultCard);
        statusText = view.findViewById(R.id.statusText);
        helperText = view.findViewById(R.id.helperText);
        detailText = view.findViewById(R.id.detailText);
        heatmapImage = view.findViewById(R.id.heatmapImage);

        executorService = Executors.newSingleThreadExecutor();
        resultCard.setVisibility(View.INVISIBLE);

        setupDropdowns();
        setupDefaults();
        initializeInterpreter();

        runButton.setOnClickListener(v -> runSoilMoistureInference());
    }

    private void setupDropdowns() {
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                regionOptions
        );
        regionDropdown.setAdapter(regionAdapter);

        ArrayAdapter<String> soilAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                soilTypeOptions
        );
        soilTypeDropdown.setAdapter(soilAdapter);
    }

    private void setupDefaults() {
        regionDropdown.setText(regionOptions.get(0), false);
        soilTypeDropdown.setText(soilTypeOptions.get(0), false);
        temperatureInput.setText("20");
        day1Input.setText("5");
        day2Input.setText("0");
        day3Input.setText("10");
        day4Input.setText("2");
        day5Input.setText("0");
    }

    private void initializeInterpreter() {
        try {
            interpreter = new Interpreter(loadModelFile(requireContext()));
            statusText.setText("Model ready.");
            helperText.setText("Choose a region and recent conditions to generate the soil moisture forecast.");
            detailText.setText("Input shape: [1, 5, 32, 32, 1]  Output shape: [1, 32, 32, 1]");
            showResultCard();
        } catch (IOException e) {
            showError("Model could not be loaded.", e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE_NAME);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    private void runSoilMoistureInference() {
        if (interpreter == null) {
            showError("Model is not ready.", "The Soil Moisture model has not been initialized yet.");
            return;
        }

        SoilUserInput userInput = readUserInput();
        if (userInput == null) {
            return;
        }

        setLoading(true);

        executorService.execute(() -> {
            try {
                float[][][][][] input = buildInputTensor(userInput);
                float[][][][] output = new float[1][GRID_SIZE][GRID_SIZE][CHANNELS];

                interpreter.run(input, output);

                float[][][] result = output[0];
                Bitmap heatmapBitmap = createHeatmapBitmap(result);
                SoilStats stats = extractStats(result);

                if (!isAdded()) {
                    return;
                }

                requireActivity().runOnUiThread(() -> {
                    heatmapImage.setImageBitmap(heatmapBitmap);
                    statusText.setText(buildStatusLine(stats.average));
                    helperText.setText(String.format(
                            Locale.US,
                            "%s region using rainfall %.1f / %.1f / %.1f / %.1f / %.1f mm, %s soil, %.1f C.",
                            userInput.region,
                            userInput.rainfallByDay[0],
                            userInput.rainfallByDay[1],
                            userInput.rainfallByDay[2],
                            userInput.rainfallByDay[3],
                            userInput.rainfallByDay[4],
                            userInput.soilType,
                            userInput.temperatureC
                    ));
                    detailText.setText(
                            "Min: " + decimalFormat.format(stats.min)
                                    + "   Max: " + decimalFormat.format(stats.max)
                                    + "   Avg: " + decimalFormat.format(stats.average)
                                    + "   Norm: (value - " + decimalFormat.format(NORMALIZATION_MEAN)
                                    + ") / " + decimalFormat.format(safeStd())
                    );
                    showResultCard();
                    setLoading(false);
                });
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    showError("Inference failed.", e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    @Nullable
    private SoilUserInput readUserInput() {
        String region = String.valueOf(regionDropdown.getText()).trim();
        if (!regionOptions.contains(region)) {
            Toast.makeText(requireContext(), "Choose California or Michigan.", Toast.LENGTH_SHORT).show();
            return null;
        }

        String soilType = String.valueOf(soilTypeDropdown.getText()).trim();
        if (!soilTypeOptions.contains(soilType)) {
            soilType = "Loamy";
        }

        float[] rainfallByDay = new float[INPUT_TIME_STEPS];
        TextInputEditText[] rainfallInputs = new TextInputEditText[]{day1Input, day2Input, day3Input, day4Input, day5Input};

        for (int index = 0; index < rainfallInputs.length; index++) {
            Float rainfall = parseFloat(rainfallInputs[index], "Enter rainfall for all 5 days.");
            if (rainfall == null) {
                return null;
            }
            rainfallByDay[index] = rainfall;
        }

        String temperatureText = String.valueOf(temperatureInput.getText()).trim();
        float temperature = DEFAULT_TEMPERATURE_C;
        if (!temperatureText.isEmpty()) {
            Float parsedTemperature = parseFloat(temperatureInput, "Temperature must be a valid number.");
            if (parsedTemperature == null) {
                return null;
            }
            temperature = parsedTemperature;
        }

        return new SoilUserInput(region, rainfallByDay, soilType, temperature);
    }

    @Nullable
    private Float parseFloat(TextInputEditText input, String errorMessage) {
        try {
            return Float.parseFloat(String.valueOf(input.getText()).trim());
        } catch (Exception ignored) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private float[][][][][] buildInputTensor(SoilUserInput userInput) {
        float[][][][][] input = new float[1][INPUT_TIME_STEPS][GRID_SIZE][GRID_SIZE][CHANNELS];
        float std = safeStd();
        float soilOffset = soilTypeOffset(userInput.soilType);

        for (int t = 0; t < INPUT_TIME_STEPS; t++) {
            float rainfallEffect = userInput.rainfallByDay[t] * rainfallFactor(userInput.region);
            float evaporationEffect = userInput.temperatureC * TEMPERATURE_FACTOR;
            float timeDecay = (INPUT_TIME_STEPS - 1 - t) * 0.018f;

            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    float templateValue = regionTemplateValue(userInput.region, t, row, col);
                    float adjustedValue = templateValue + rainfallEffect + soilOffset - evaporationEffect - timeDecay;
                    input[0][t][row][col][0] = (adjustedValue - NORMALIZATION_MEAN) / std;
                }
            }
        }

        return input;
    }

    private float rainfallFactor(String region) {
        if ("Michigan".equals(region)) {
            return 0.014f;
        }
        return 0.011f;
    }

    private float soilTypeOffset(String soilType) {
        if ("Sandy".equals(soilType)) {
            return -0.08f;
        }
        if ("Clay".equals(soilType)) {
            return 0.09f;
        }
        return 0.0f;
    }

    private float regionTemplateValue(String region, int timeStep, int row, int col) {
        float northSouthGradient = row / (float) (GRID_SIZE - 1);
        float eastWestGradient = col / (float) (GRID_SIZE - 1);
        float waveComponent;

        if ("Michigan".equals(region)) {
            waveComponent = (float) Math.sin((row * 0.22f) + (col * 0.11f) + timeStep * 0.35f) * 0.06f;
            return 0.42f + (northSouthGradient * 0.22f) + (eastWestGradient * 0.08f) + waveComponent;
        }

        waveComponent = (float) Math.cos((row * 0.18f) - (col * 0.09f) + timeStep * 0.28f) * 0.05f;
        return 0.24f + (northSouthGradient * 0.12f) + (eastWestGradient * 0.18f) + waveComponent;
    }

    private float safeStd() {
        return NORMALIZATION_STD > 1e-8f ? NORMALIZATION_STD : 1.0f;
    }

    private Bitmap createHeatmapBitmap(float[][][] result) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                float value = result[row][col][0];
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
        }

        float range = Math.max(max - min, 1e-6f);
        Bitmap bitmap = Bitmap.createBitmap(GRID_SIZE, GRID_SIZE, Bitmap.Config.ARGB_8888);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                float normalized = (result[row][col][0] - min) / range;
                bitmap.setPixel(col, row, moistureColor(normalized));
            }
        }

        return Bitmap.createScaledBitmap(bitmap, GRID_SIZE * HEATMAP_SCALE, GRID_SIZE * HEATMAP_SCALE, false);
    }

    private int moistureColor(float normalizedValue) {
        float clamped = Math.max(0f, Math.min(1f, normalizedValue));
        int startColor = Color.rgb(210, 170, 108);
        int midColor = Color.rgb(111, 181, 132);
        int endColor = Color.rgb(31, 119, 180);

        if (clamped < 0.5f) {
            return blendColors(startColor, midColor, clamped / 0.5f);
        }
        return blendColors(midColor, endColor, (clamped - 0.5f) / 0.5f);
    }

    private int blendColors(int startColor, int endColor, float ratio) {
        float clampedRatio = Math.max(0f, Math.min(1f, ratio));
        int red = (int) (Color.red(startColor) + ((Color.red(endColor) - Color.red(startColor)) * clampedRatio));
        int green = (int) (Color.green(startColor) + ((Color.green(endColor) - Color.green(startColor)) * clampedRatio));
        int blue = (int) (Color.blue(startColor) + ((Color.blue(endColor) - Color.blue(startColor)) * clampedRatio));
        return Color.rgb(red, green, blue);
    }

    private SoilStats extractStats(float[][][] result) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float total = 0f;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                float value = result[row][col][0];
                min = Math.min(min, value);
                max = Math.max(max, value);
                total += value;
            }
        }

        return new SoilStats(min, max, total / (GRID_SIZE * GRID_SIZE));
    }

    private String buildStatusLine(float averageValue) {
        if (averageValue >= 0.6f) {
            return "Soil moisture outlook: High";
        }
        if (averageValue >= 0.25f) {
            return "Soil moisture outlook: Moderate";
        }
        return "Soil moisture outlook: Low";
    }

    private void setLoading(boolean isLoading) {
        runButton.setEnabled(!isLoading);
        runButton.setText(isLoading ? "Running model..." : "Predict Soil Moisture");
        loadingBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String title, @Nullable String detail) {
        statusText.setText(title);
        helperText.setText("Check the model asset and the entered conditions, then try again.");
        detailText.setText(detail != null ? detail : "Unknown error");
        heatmapImage.setImageDrawable(null);
        showResultCard();
    }

    private void showResultCard() {
        if (resultCard.getVisibility() != View.VISIBLE) {
            resultCard.setAlpha(0f);
            resultCard.setTranslationY(30f);
            resultCard.setVisibility(View.VISIBLE);
            resultCard.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
    }

    @Override
    public void onDestroyView() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }

        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }

        super.onDestroyView();
    }

    private static class SoilStats {
        final float min;
        final float max;
        final float average;

        SoilStats(float min, float max, float average) {
            this.min = min;
            this.max = max;
            this.average = average;
        }
    }

    private static class SoilUserInput {
        final String region;
        final float[] rainfallByDay;
        final String soilType;
        final float temperatureC;

        SoilUserInput(String region, float[] rainfallByDay, String soilType, float temperatureC) {
            this.region = region;
            this.rainfallByDay = rainfallByDay;
            this.soilType = soilType;
            this.temperatureC = temperatureC;
        }
    }
}
