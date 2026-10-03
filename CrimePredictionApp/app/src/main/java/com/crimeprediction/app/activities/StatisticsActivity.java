package com.crimeprediction.app.activities;

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
import androidx.viewpager2.widget.ViewPager2;

import com.crimeprediction.app.R;
import com.crimeprediction.app.activities.stats.StatsPagerAdapter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
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
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.FirebaseApp;
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

    private ProgressBar progressStats;
    private TextView textStatsEmpty;
    private Spinner spinnerCity, spinnerAreaFilter;
    private ViewPager2 pagerCharts;
    private TabLayout tabsCharts;
    private ChartData currentChartData;
    private boolean reportsLoaded = false;

    private List<ReportItem> reportList = new ArrayList<>();
    private String selectedCity = null;
    private String selectedArea = null;

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

        spinnerCity = findViewById(R.id.spinnerCity);
        pagerCharts = findViewById(R.id.pagerCharts);
        tabsCharts = findViewById(R.id.tabsCharts);
        progressStats = findViewById(R.id.progressStats);
        textStatsEmpty = findViewById(R.id.textStatsEmpty);
        spinnerAreaFilter = findViewById(R.id.spinnerAreaFilter);

        pagerCharts.setAdapter(new StatsPagerAdapter(this));
        pagerCharts.setOffscreenPageLimit(3);
        pagerCharts.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderVisibleCharts();
            }
        });

        new TabLayoutMediator(tabsCharts, pagerCharts, (tab, position) -> {
            if (position == 0) tab.setText("City Trend");
            else if (position == 1) tab.setText("Area Risk");
            else tab.setText("Crime Types");
        }).attach();

        setupCitySpinner();
        setupAreaSpinnerForCity(null);
        spinnerAreaFilter.setEnabled(false);

        showSelectionRequiredState();
    }
    private void setupCitySpinner() {
        String[] cities = {"Select City", "Multan", "Lahore", "Karachi", "Islamabad"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cities
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);

        // default selection = Select City
        spinnerCity.setSelection(0);

        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedCity = null;
                    selectedArea = null;
                    spinnerAreaFilter.setEnabled(false);
                    setupAreaSpinnerForCity(null);
                    showSelectionRequiredState();
                    return;
                }

                selectedCity = cities[position];
                spinnerAreaFilter.setEnabled(true);

                setupAreaSpinnerForCity(selectedCity);
                if (!reportsLoaded) {
                    loadReportsFromFirebase();
                } else {
                    refreshChartsFromReports();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupAreaSpinnerForCity(String city) {
        String[] areas;
        if (city == null) {
            areas = new String[]{"Select Area"};
        } else switch (city) {
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
                areas = new String[]{"Select Area"};
                break;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                areas
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAreaFilter.setAdapter(adapter);
        spinnerAreaFilter.setSelection(0);
        selectedArea = areas[0];
        spinnerAreaFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedArea = areas[position];
                if (selectedCity == null) {
                    showSelectionRequiredState();
                    return;
                }
                if (!reportsLoaded) {
                    loadReportsFromFirebase();
                } else {
                    refreshChartsFromReports();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void loadReportsFromFirebase() {
        progressStats.setVisibility(View.VISIBLE);
        pagerCharts.setVisibility(View.GONE);
        tabsCharts.setVisibility(View.GONE);
        textStatsEmpty.setVisibility(View.GONE);
        reportsLoaded = false;

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        // Uses configured firebase_database_url (see AndroidManifest/strings).
        DatabaseReference reportsRef = FirebaseDatabase.getInstance()
                .getReference("reports");
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reportsLoaded = true; // set FIRST
                progressStats.setVisibility(View.GONE);
                reportList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String crimeType = normalizeCrimeType(child.child("crimeType").getValue(String.class));
                    String city = child.child("city").getValue(String.class);
                    if (city == null || city.isEmpty()) {
                        city = child.child("location").getValue(String.class);
                    }
                    String area = child.child("area").getValue(String.class);
                    Long ts = child.child("timestamp").getValue(Long.class);
                    if (crimeType != null && !crimeType.isEmpty() && city != null && !"Select City".equalsIgnoreCase(city)) {
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
                reportsLoaded = true;
                Toast.makeText(StatisticsActivity.this, "Could not load statistics", Toast.LENGTH_SHORT).show();
                showEmptyState();
            }
        });
    }
    private String normalizeCrimeType(String raw) {
        if (raw == null) return "";
        String r = raw.trim().toLowerCase();
        if (r.contains("theft")) return "Theft";
        if (r.contains("assault")) return "Assault";
        if (r.contains("fraud")) return "Fraud";
        if (r.contains("vandalism")) return "Vandalism";
        return "";
    }
    private void refreshChartsFromReports() {
        if (selectedCity == null) {
            showSelectionRequiredState();
            return;
        }
        if (reportList.isEmpty()) {
            showEmptyState();
            return;
        }
        // Filter by current city and area before building chart data
        List<ReportItem> filtered = new ArrayList<>();
        for (ReportItem r : reportList) {
            if (r.city == null || !r.city.trim().equalsIgnoreCase(selectedCity.trim()))
                continue;
            // "Select Area" behaves like "All Areas"
            if (selectedArea != null
                    && !"All Areas".equalsIgnoreCase(selectedArea)
                    && !"Select Area".equalsIgnoreCase(selectedArea)) {
                if (r.area == null || !r.area.trim().equalsIgnoreCase(selectedArea.trim())) {
                    continue;
                }
            }
            filtered.add(r);
        }

        ChartData data = buildChartData(filtered);
        currentChartData = data;
        if (isEmpty(data)) {
            showEmptyState();
            return;
        }
        pagerCharts.setVisibility(View.VISIBLE);
        tabsCharts.setVisibility(View.VISIBLE);
        textStatsEmpty.setVisibility(View.GONE);
        renderVisibleCharts();
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
        pagerCharts.setVisibility(View.GONE);
        tabsCharts.setVisibility(View.GONE);
        textStatsEmpty.setVisibility(View.VISIBLE);
        textStatsEmpty.setText("No reports found for selected filters");
        currentChartData = null;
    }

    private void showSelectionRequiredState() {
        progressStats.setVisibility(View.GONE);
        pagerCharts.setVisibility(View.GONE);
        tabsCharts.setVisibility(View.GONE);
        textStatsEmpty.setVisibility(View.VISIBLE);
        textStatsEmpty.setText("Select a city to view statistics");
        currentChartData = null;
    }

    /**
     * Build chart data from already-filtered reports.
     */
    private ChartData buildChartData(List<ReportItem> reports) {
        // ------------------------------------------------------------------
        // Monthly trend: group by month, sorted CHRONOLOGICALLY.
        //
        // FIX: the previous version built a String key like "Apr 26" and put
        // it into a TreeMap<String, Integer>. TreeMap sorts its keys using
        // String.compareTo(), which is ALPHABETICAL, not chronological — so
        // "Apr 26" (2026) would sort before "Mar 24" (2024) simply because
        // 'A' < 'M'. That is why the line chart zig-zagged: the points were
        // never in real date order. We now sort by a numeric key
        // (year * 12 + month), which always sorts chronologically, and we
        // show the full 4-digit year in the label so it reads unambiguously
        // as "Apr 2026" instead of the easily-misread "Apr 26".
        // ------------------------------------------------------------------
        Calendar cal = Calendar.getInstance();
        String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

        Map<Integer, Integer> countsBySortKey = new TreeMap<>(); // numeric key -> chronological order
        Map<Integer, String> labelsBySortKey = new HashMap<>();

        for (ReportItem r : reports) {
            if (r.timestamp == 0) continue;
            cal.setTimeInMillis(r.timestamp);

            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH); // 0-11
            int sortKey = year * 12 + month;
            String label = monthNames[month] + " " + year; // e.g. "Apr 2026"

            countsBySortKey.put(sortKey, countsBySortKey.getOrDefault(sortKey, 0) + 1);
            labelsBySortKey.put(sortKey, label);
        }

        List<MonthCount> monthlyTrend = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : countsBySortKey.entrySet()) {
            monthlyTrend.add(new MonthCount(labelsBySortKey.get(e.getKey()), e.getValue()));
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


    private void renderVisibleCharts() {
        for (androidx.fragment.app.Fragment f : getSupportFragmentManager().getFragments()) {
            if (f == null || f.getView() == null) continue;
            if (f instanceof com.crimeprediction.app.activities.stats.CityGraphFragment) {
                View v = f.getView();
                if (v != null) renderCityTrendChart(v.findViewById(R.id.chartTrend));
            } else if (f instanceof com.crimeprediction.app.activities.stats.AreaGraphFragment) {
                View v = f.getView();
                if (v != null) renderAreaRiskChart(v.findViewById(R.id.chartBar));
            } else if (f instanceof com.crimeprediction.app.activities.stats.CrimeTypeGraphFragment) {
                View v = f.getView();
                if (v != null) renderCrimeTypeChart(v.findViewById(R.id.chartPie));
            }
        }
    }

    public void renderCityTrendChart(LineChart chart) {
        if (chart == null) return;
        ChartData data = currentChartData;
        if (data == null || data.getMonthlyTrend() == null || data.getMonthlyTrend().isEmpty()) {
            chart.setData(null);
            chart.invalidate();
            return;
        }

        // UX: readability + avoid overlap
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(12f, 20f, 12f, 24f);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextSize(12f);
        chart.getLegend().setTextColor(android.graphics.Color.BLACK);

        List<Entry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        float maxY = 0f;
        int i = 0;
        for (MonthCount item : data.getMonthlyTrend()) {
            entries.add(new Entry(i, item.count));
            labels.add(item.month);
            if (item.count > maxY) maxY = item.count;
            i++;
        }

        LineDataSet set = new LineDataSet(entries, "Crime count");
        set.setColor(android.graphics.Color.parseColor("#1E3A8A"));
        set.setCircleColor(android.graphics.Color.parseColor("#3B82F6"));
        set.setLineWidth(2.5f);
        set.setCircleRadius(5f);
        set.setDrawCircleHole(true);
        set.setCircleHoleColor(android.graphics.Color.WHITE);

        // Light fill under the line so the trend shape reads clearly at a glance
        set.setDrawFilled(true);
        set.setFillColor(android.graphics.Color.parseColor("#3B82F6"));
        set.setFillAlpha(40);

        set.setValueTextSize(12f);
        set.setValueTextColor(android.graphics.Color.parseColor("#111111"));
        set.setDrawValues(true);
        set.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        com.github.mikephil.charting.components.XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setAvoidFirstLastClipping(true);
        x.setTextSize(12f);
        x.setTextColor(android.graphics.Color.parseColor("#111111"));
        // Rotate so full "MMM yyyy" labels never overlap each other
        x.setLabelRotationAngle(-45f);
        // Let every month get its own label instead of forcing a sparse count
        x.setLabelCount(labels.size(), false);

        chart.getAxisLeft().setAxisMinimum(0f);
        // Headroom above the tallest point so its value label is never clipped
        chart.getAxisLeft().setAxisMaximum(maxY + 3f);
        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisLeft().setGranularityEnabled(true);
        chart.getAxisLeft().setTextSize(12f);
        chart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        chart.getAxisRight().setEnabled(false);

        // Chart title
        chart.getDescription().setEnabled(true);
        chart.getDescription().setText("Monthly Crime Reports");
        chart.getDescription().setTextSize(13f);
        chart.getDescription().setTextColor(android.graphics.Color.BLACK);

        // Show a clear, readable window instead of squeezing every month into
        // one screen: display up to 6 months at a time, auto-scrolled to the
        // most recent, with the rest reachable by dragging left.
        chart.setDragEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        float visibleRange = Math.min(6f, entries.size());
        chart.setVisibleXRangeMaximum(visibleRange);

        chart.setData(new LineData(set));
        chart.moveViewToX(entries.size() - 1); // jump to most recent month by default
        chart.animateX(400);
        chart.invalidate();
    }

    public void renderCrimeTypeChart(PieChart chart) {
        if (chart == null) return;

        ChartData data = currentChartData;
        if (data == null || data.getCategoryDistribution() == null || data.getCategoryDistribution().isEmpty()) {
            chart.setData(null);
            chart.invalidate();
            return;
        }

        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(12f, 12f, 12f, 12f);
        chart.setEntryLabelTextSize(12f);
        chart.setEntryLabelColor(android.graphics.Color.BLACK);
        chart.setDrawEntryLabels(false);

        List<PieEntry> pieEntries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : data.getCategoryDistribution().entrySet()) {
            pieEntries.add(new PieEntry(e.getValue(), e.getKey()));
        }

        int[] colors = {
                android.graphics.Color.parseColor("#1E3A8A"),
                android.graphics.Color.parseColor("#3B82F6"),
                android.graphics.Color.parseColor("#F97316"),
                android.graphics.Color.parseColor("#22C55E")
        };

        PieDataSet set = new PieDataSet(pieEntries, "");
        set.setColors(colors);

        // ✅ CHANGE: value text color to BLACK
        set.setValueTextSize(12f);
        set.setValueTextColor(android.graphics.Color.BLACK);

        set.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        set.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        // ✅ CHANGE: connector lines (arrows) to BLACK
        set.setValueLineColor(android.graphics.Color.BLACK);
        set.setValueLineWidth(1f);

        set.setValueLinePart1Length(0.4f);
        set.setValueLinePart2Length(0.4f);
        set.setValueLinePart1OffsetPercentage(80f);

        chart.getLegend().setWordWrapEnabled(true);

        // optional but recommended for consistency
        chart.getLegend().setTextSize(12f);
        chart.getLegend().setTextColor(android.graphics.Color.BLACK);

        chart.setHoleColor(android.graphics.Color.TRANSPARENT);
        chart.setTransparentCircleColor(android.graphics.Color.TRANSPARENT);

        // Show percentage instead of raw count
        PieData pieData = new PieData(set);
        chart.setUsePercentValues(true);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + "%";
            }
        });
        chart.setData(pieData);

        chart.animateY(400);
        chart.invalidate();
    }

    public void renderAreaRiskChart(BarChart chart) {
        if (chart == null) return;
        ChartData data = currentChartData;
        if (data == null || data.getHighRiskItems() == null || data.getHighRiskItems().isEmpty()) {
            chart.setData(null);
            chart.invalidate();
            return;
        }

        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(12f, 12f, 12f, 18f);
        chart.getLegend().setEnabled(false);

        List<BarEntry> barEntries = new ArrayList<>();
        final List<String> areaLabels = new ArrayList<>();
        int j = 0;
        for (AreaRisk item : data.getHighRiskItems()) {
            barEntries.add(new BarEntry(j, (float) item.riskScore));
            areaLabels.add(item.area);
            j++;
        }

        BarDataSet set = new BarDataSet(barEntries, "Reports per Area");
        set.setColor(android.graphics.Color.parseColor("#EF4444"));
        set.setValueTextSize(12f);
        set.setValueTextColor(android.graphics.Color.BLACK);
        set.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisLeft().setGranularityEnabled(true);
        chart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        com.github.mikephil.charting.components.XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(areaLabels));
        x.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setLabelRotationAngle(45f);
        x.setTextSize(11f);
        x.setAvoidFirstLastClipping(true);

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setTextSize(11f);

        chart.setData(new BarData(set));
        chart.animateY(400);
        chart.invalidate();
    }
}