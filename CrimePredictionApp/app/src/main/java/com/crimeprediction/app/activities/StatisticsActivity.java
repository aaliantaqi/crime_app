package com.crimeprediction.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.crimeprediction.app.network.ApiClient;
import com.crimeprediction.app.network.ApiService;
import com.crimeprediction.app.network.CrimeStatisticsResponse;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Crime Statistics: Line chart (monthly trend), Pie chart (category), Bar chart (high risk areas).
 */
public class StatisticsActivity extends AppCompatActivity {

    private LineChart chartTrend;
    private PieChart chartPie;
    private BarChart chartBar;
    private ProgressBar progressStats;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        chartTrend = findViewById(R.id.chartTrend);
        chartPie = findViewById(R.id.chartPie);
        chartBar = findViewById(R.id.chartBar);
        progressStats = findViewById(R.id.progressStats);
        apiService = ApiClient.getApiService();

        progressStats.setVisibility(View.VISIBLE);
        apiService.getCrimeStatistics().enqueue(new Callback<CrimeStatisticsResponse>() {
            @Override
            public void onResponse(Call<CrimeStatisticsResponse> call, Response<CrimeStatisticsResponse> response) {
                progressStats.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    bindCharts(response.body());
                } else {
                    Toast.makeText(StatisticsActivity.this, "Could not load statistics", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CrimeStatisticsResponse> call, Throwable t) {
                progressStats.setVisibility(View.GONE);
                Toast.makeText(StatisticsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindCharts(CrimeStatisticsResponse data) {
        if (data.getMonthlyTrend() != null && !data.getMonthlyTrend().isEmpty()) {
            List<Entry> entries = new ArrayList<>();
            final List<String> labels = new ArrayList<>();
            int i = 0;
            for (CrimeStatisticsResponse.MonthlyTrendItem item : data.getMonthlyTrend()) {
                entries.add(new Entry(i, item.getCount()));
                labels.add(item.getMonth());
                i++;
            }
            LineDataSet set = new LineDataSet(entries, "Crime count");
            set.setColor(Color.parseColor("#1E3A8A"));
            set.setCircleColor(Color.parseColor("#3B82F6"));
            set.setLineWidth(2f);
            chartTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            chartTrend.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            chartTrend.setData(new LineData(set));
            chartTrend.animateX(500);
            chartTrend.invalidate();
        }

        if (data.getCategoryDistribution() != null && !data.getCategoryDistribution().isEmpty()) {
            List<PieEntry> pieEntries = new ArrayList<>();
            int[] colors = {Color.parseColor("#1E3A8A"), Color.parseColor("#3B82F6"), Color.parseColor("#F97316"), Color.parseColor("#22C55E")};
            int c = 0;
            for (Map.Entry<String, Integer> e : data.getCategoryDistribution().entrySet()) {
                pieEntries.add(new PieEntry(e.getValue(), e.getKey()));
                c++;
            }
            PieDataSet set = new PieDataSet(pieEntries, "");
            set.setColors(colors);
            chartPie.setData(new PieData(set));
            chartPie.animateY(500);
            chartPie.invalidate();
        }

        if (data.getHighRiskAreas() != null && !data.getHighRiskAreas().isEmpty()) {
            List<BarEntry> barEntries = new ArrayList<>();
            final List<String> areaLabels = new ArrayList<>();
            int j = 0;
            for (CrimeStatisticsResponse.AreaRiskItem item : data.getHighRiskAreas()) {
                barEntries.add(new BarEntry(j, (float) item.getRiskScore()));
                areaLabels.add(item.getArea());
                j++;
            }
            BarDataSet set = new BarDataSet(barEntries, "Risk score");
            set.setColor(Color.parseColor("#EF4444"));
            chartBar.getXAxis().setValueFormatter(new IndexAxisValueFormatter(areaLabels));
            chartBar.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            chartBar.setData(new BarData(set));
            chartBar.animateY(500);
            chartBar.invalidate();
        }
    }
}
