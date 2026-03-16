package com.crimeprediction.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Crime Statistics: Line chart (monthly trend), Pie chart (category), Bar chart (high risk).
 * Toggle between City and Area statistics.
 */
public class StatisticsActivity extends AppCompatActivity {

    private LineChart chartTrend;
    private PieChart chartPie;
    private BarChart chartBar;
    private ProgressBar progressStats;
    private TextView textStatsEmpty;
    private View layoutCharts;
    private MaterialButtonToggleGroup toggleCity;
    private Spinner spinnerAreaFilter;

    private List<ReportItem> reportList = new ArrayList<>();
    private String selectedCity = "Multan";
    private String selectedArea = "All Areas";

    /** Simple DTO for chart binding (no dependency on API response class). */
    private interface ChartData {
        List<MonthCount> getMonthlyTrend();
        Map<String, Integer> getCategoryDistribution();
        List<AreaRisk> getHighRiskItems();
    }

    private static class MonthCount {
        String month;
        int count;
        MonthCount(String month, int count) { this.month = month; this.count = count; }
    }

    private static class AreaRisk {
        String area;
        double riskScore;
        AreaRisk(String area, double riskScore) { this.area = area; this.riskScore = riskScore; }
    }

    private static class ReportItem {
        String crimeType;
        String city;
        String area;
        long timestamp;
        ReportItem(String crimeType, String city, String area, long timestamp) {
            this.crimeType = crimeType;
            this.city = city != null ? city.trim() : "";
            this.area = area != null ? area.trim() : "";
            this.timestamp = timestamp;
        }
    }

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
        textStatsEmpty = findViewById(R.id.textStatsEmpty);
        layoutCharts = findViewById(R.id.layoutCharts);
        toggleCity = findViewById(R.id.toggleCity);
        spinnerAreaFilter = findViewById(R.id.spinnerAreaFilter);

        // Default selected city: Multan
        toggleCity.check(R.id.btnCityMultan);
        setupCityToggle();
        setupAreaSpinnerForCity(selectedCity);

        loadReportsFromFirebase();
    }

    private void setupCityToggle() {
        toggleCity.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnCityMultan) {
                selectedCity = "Multan";
            } else if (checkedId == R.id.btnCityLahore) {
                selectedCity = "Lahore";
            } else if (checkedId == R.id.btnCityKarachi) {
                selectedCity = "Karachi";
            } else if (checkedId == R.id.btnCityIslamabad) {
                selectedCity = "Islamabad";
            }
            setupAreaSpinnerForCity(selectedCity);
            refreshChartsFromReports();
        });
    }

    private void setupAreaSpinnerForCity(String city) {
        String[] areas;
        switch (city) {
            case "Multan":
                areas = new String[]{"All Areas", "Gulgasht", "Bosan Road", "Hussain Agahi"};
                break;
            case "Lahore":
                areas = new String[]{"All Areas", "DHA", "Model Town", "Johar Town"};
                break;
            case "Karachi":
                areas = new String[]{"All Areas", "Saddar", "Clifton", "Defence"};
                break;
            case "Islamabad":
                areas = new String[]{"All Areas", "F-7", "F-8", "G-10"};
                break;
            default:
                areas = new String[]{"All Areas"};
                break;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                areas
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAreaFilter.setAdapter(adapter);
        spinnerAreaFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedArea = areas[position];
                refreshChartsFromReports();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void loadReportsFromFirebase() {
        progressStats.setVisibility(View.VISIBLE);
        layoutCharts.setVisibility(View.GONE);
        textStatsEmpty.setVisibility(View.GONE);

        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Reports");
        reportsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressStats.setVisibility(View.GONE);
                reportList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String crimeType = child.child("crimeType").getValue(String.class);
                    String city = child.child("city").getValue(String.class);
                    if (city == null) {
                        // Fallback: use "location" as city if older schema
                        city = child.child("location").getValue(String.class);
                    }
                    String area = child.child("area").getValue(String.class);
                    Long ts = child.child("timestamp").getValue(Long.class);
                    if (crimeType != null && city != null && !"Select City".equalsIgnoreCase(city)) {
                        reportList.add(new ReportItem(
                                crimeType,
                                city,
                                area != null ? area : "",
                                ts != null ? ts : 0L
                        ));
                    }
                }
                refreshChartsFromReports();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressStats.setVisibility(View.GONE);
                Toast.makeText(StatisticsActivity.this, "Could not load statistics", Toast.LENGTH_SHORT).show();
                showEmptyState();
            }
        });
    }

    private void refreshChartsFromReports() {
        // Filter by current city and area before building chart data
        List<ReportItem> filtered = new ArrayList<>();
        for (ReportItem r : reportList) {
            if (r.city == null) continue;
            if (!r.city.equalsIgnoreCase(selectedCity)) continue;
            if (selectedArea != null
                    && !"All Areas".equalsIgnoreCase(selectedArea)
                    && r.area != null
                    && !r.area.equalsIgnoreCase(selectedArea)) {
                continue;
            }
            filtered.add(r);
        }

        ChartData data = buildChartData(filtered);
        if (isEmpty(data)) {
            showEmptyState();
            return;
        }
        layoutCharts.setVisibility(View.VISIBLE);
        textStatsEmpty.setVisibility(View.GONE);
        bindCharts(data);
    }

    private boolean isEmpty(ChartData data) {
        List<MonthCount> trend = data.getMonthlyTrend();
        Map<String, Integer> cat = data.getCategoryDistribution();
        List<AreaRisk> areas = data.getHighRiskItems();
        return (trend == null || trend.isEmpty())
                && (cat == null || cat.isEmpty())
                && (areas == null || areas.isEmpty());
    }

    private void showEmptyState() {
        layoutCharts.setVisibility(View.GONE);
        textStatsEmpty.setVisibility(View.VISIBLE);
        textStatsEmpty.setText("No data available");
        clearCharts();
    }

    private void clearCharts() {
        chartTrend.setData(null);
        chartTrend.invalidate();
        chartPie.setData(null);
        chartPie.invalidate();
        chartBar.setData(null);
        chartBar.invalidate();
    }

    /**
     * Build chart data from already-filtered reports.
     */
    private ChartData buildChartData(List<ReportItem> reports) {
        // Monthly trend: group by month (same for city/area)
        Calendar cal = Calendar.getInstance();
        Map<String, Integer> monthCounts = new TreeMap<>();
        for (ReportItem r : reports) {
            cal.setTimeInMillis(r.timestamp);
            String monthKey = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
            monthCounts.put(monthKey, monthCounts.getOrDefault(monthKey, 0) + 1);
        }
        List<MonthCount> monthlyTrend = new ArrayList<>();
        for (Map.Entry<String, Integer> e : monthCounts.entrySet()) {
            monthlyTrend.add(new MonthCount(e.getKey(), e.getValue()));
        }

        // Category distribution (same for both modes)
        Map<String, Integer> categoryDistribution = new HashMap<>();
        for (ReportItem r : reports) {
            if (r.crimeType != null && !r.crimeType.isEmpty()) {
                String type = r.crimeType;
                categoryDistribution.put(type, categoryDistribution.getOrDefault(type, 0) + 1);
            }
        }

        // High risk: by area within selected city
        Map<String, Integer> areaCounts = new HashMap<>();
        for (ReportItem r : reports) {
            if (r.area == null || r.area.isEmpty()) continue;
            String key = r.area;
            areaCounts.put(key, areaCounts.getOrDefault(key, 0) + 1);
        }
        List<AreaRisk> highRiskItems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : areaCounts.entrySet()) {
            highRiskItems.add(new AreaRisk(e.getKey(), (double) e.getValue()));
        }
        // Sort by risk descending and take top entries
        highRiskItems.sort((a, b) -> Double.compare(b.riskScore, a.riskScore));
        if (highRiskItems.size() > 10) {
            highRiskItems = highRiskItems.subList(0, 10);
        }

        final List<MonthCount> mt = monthlyTrend;
        final Map<String, Integer> cd = categoryDistribution;
        final List<AreaRisk> hr = highRiskItems;
        return new ChartData() {
            @Override public List<MonthCount> getMonthlyTrend() { return mt; }
            @Override public Map<String, Integer> getCategoryDistribution() { return cd; }
            @Override public List<AreaRisk> getHighRiskItems() { return hr; }
        };
    }

    private void bindCharts(ChartData data) {
        if (data.getMonthlyTrend() != null && !data.getMonthlyTrend().isEmpty()) {
            List<Entry> entries = new ArrayList<>();
            final List<String> labels = new ArrayList<>();
            int i = 0;
            for (MonthCount item : data.getMonthlyTrend()) {
                entries.add(new Entry(i, item.count));
                labels.add(item.month);
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
        } else {
            chartTrend.setData(null);
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
        } else {
            chartPie.setData(null);
            chartPie.invalidate();
        }

        if (data.getHighRiskItems() != null && !data.getHighRiskItems().isEmpty()) {
            List<BarEntry> barEntries = new ArrayList<>();
            final List<String> areaLabels = new ArrayList<>();
            int j = 0;
            for (AreaRisk item : data.getHighRiskItems()) {
                barEntries.add(new BarEntry(j, (float) item.riskScore));
                areaLabels.add(item.area);
                j++;
            }
            BarDataSet set = new BarDataSet(barEntries, "High risk areas");
            set.setColor(Color.parseColor("#EF4444"));
            chartBar.getXAxis().setValueFormatter(new IndexAxisValueFormatter(areaLabels));
            chartBar.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            chartBar.setData(new BarData(set));
            chartBar.animateY(500);
            chartBar.invalidate();
        } else {
            chartBar.setData(null);
            chartBar.invalidate();
        }
    }
}
