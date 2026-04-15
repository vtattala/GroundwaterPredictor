package com.waterproj.groundwaterpredictor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class HeatmapGridView extends View {
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[][] data = new float[0][0];

    public HeatmapGridView(Context context) {
        super(context);
        init();
    }

    public HeatmapGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeatmapGridView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(Color.argb(40, 16, 42, 42));
    }

    public void setData(float[][] heatmapData) {
        data = heatmapData == null ? new float[0][0] : heatmapData;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data.length == 0 || data[0].length == 0) {
            return;
        }

        int rows = data.length;
        int columns = data[0].length;
        float cellWidth = getWidth() / (float) columns;
        float cellHeight = getHeight() / (float) rows;

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] row : data) {
            for (float value : row) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        float range = Math.max(0.0001f, max - min);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float normalized = (data[row][column] - min) / range;
                cellPaint.setStyle(Paint.Style.FILL);
                cellPaint.setColor(interpolateColor(normalized));

                float left = column * cellWidth;
                float top = row * cellHeight;
                float right = left + cellWidth;
                float bottom = top + cellHeight;

                canvas.drawRect(left, top, right, bottom, cellPaint);
                canvas.drawRect(left, top, right, bottom, borderPaint);
            }
        }
    }

    private int interpolateColor(float normalized) {
        float clamped = Math.max(0f, Math.min(1f, normalized));
        int red = (int) (48 + (231 - 48) * clamped);
        int green = (int) (117 + (111 - 117) * clamped);
        int blue = (int) (184 + (81 - 184) * clamped);
        return Color.rgb(red, green, blue);
    }
}
