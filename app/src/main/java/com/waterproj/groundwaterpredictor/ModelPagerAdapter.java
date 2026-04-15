package com.waterproj.groundwaterpredictor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ModelPagerAdapter extends FragmentStateAdapter {

    public ModelPagerAdapter(@NonNull AppCompatActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new GroundwaterPredictionFragment();
        }

        if (position == 1) {
            return ModelPlaceholderFragment.newInstance(
                    "Rainfall predictions are coming next.",
                    "This tab already uses the same card-based prediction pattern, so a rainfall model can plug in without redesigning the app."
            );
        }

        return ModelPlaceholderFragment.newInstance(
                "Soil moisture predictions are coming next.",
                "This slot is ready for another backend-powered model using the same region, time range, and results workflow."
        );
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
