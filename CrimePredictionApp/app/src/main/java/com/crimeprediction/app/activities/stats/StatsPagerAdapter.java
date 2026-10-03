package com.crimeprediction.app.activities.stats;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatsPagerAdapter extends FragmentStateAdapter {
    public StatsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new CityGraphFragment();
        if (position == 1) return new AreaGraphFragment();
        return new CrimeTypeGraphFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}

