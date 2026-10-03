package com.crimeprediction.app.activities.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.crimeprediction.app.R;
import com.crimeprediction.app.activities.StatisticsActivity;
import com.github.mikephil.charting.charts.BarChart;

public class AreaGraphFragment extends Fragment {
    private BarChart chartBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_area_graph, container, false);
        chartBar = v.findViewById(R.id.chartBar);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        StatisticsActivity host = (getActivity() instanceof StatisticsActivity) ? (StatisticsActivity) getActivity() : null;
        if (host != null) host.renderAreaRiskChart(chartBar);
    }

    @Override
    public void onResume() {
        super.onResume();
        StatisticsActivity host = (getActivity() instanceof StatisticsActivity) ? (StatisticsActivity) getActivity() : null;
        if (host != null) host.renderAreaRiskChart(chartBar);
    }
}

