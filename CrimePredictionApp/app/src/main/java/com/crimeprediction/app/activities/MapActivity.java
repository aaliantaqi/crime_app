package com.crimeprediction.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import com.crimeprediction.app.R;
import com.crimeprediction.app.models.CrimeData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 1001;
    private static final String FIREBASE_CRIME_PATH = "reports";
    private MapView map;
    private ProgressBar progressMap;
    private android.widget.TextView textMapEmpty;

    private Spinner spinnerCity;
    private Spinner spinnerArea;

    private final List<CrimeData> allCrimeData = new ArrayList<>();
    private final Map<String, List<String>> cityToAreas = new HashMap<>();
    private final Map<String, GeoPoint> areaToPoint = new HashMap<>();

    private String selectedCity = null;
    private String selectedArea = null;
    private boolean dataLoaded = false;
    private boolean isActive = false;
    private DatabaseReference crimeRef;
    private ValueEventListener crimeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        initViews();
        setupToolbar();
        setupMap();
        checkLocationPermission();
        setupCityAreaData();
        setupSpinners();
        map.setVisibility(View.GONE);
        textMapEmpty.setVisibility(View.VISIBLE);
        isActive = true;
        loadCrimeDataFromFirebase();
    }

    private void initViews() {
        map = findViewById(R.id.map);
        progressMap = findViewById(R.id.progressMap);
        spinnerCity = findViewById(R.id.spinnerCity);
        spinnerArea = findViewById(R.id.spinnerArea);
        textMapEmpty = findViewById(R.id.textMapEmpty);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupMap() {
        map.setMultiTouchControls(true);

        // Default location (Multan)
        GeoPoint startPoint = new GeoPoint(30.1575, 71.5249);
        map.getController().setZoom(12.0);
        map.getController().setCenter(startPoint);
    }

    private void setupCityAreaData() {
        cityToAreas.put("Multan", asList("Gulgasht", "Bosan Road", "Hussain Agahi"));
        cityToAreas.put("Lahore", asList("DHA", "Model Town", "Johar Town"));
        cityToAreas.put("Karachi", asList("Saddar", "Clifton", "Defence"));
        cityToAreas.put("Islamabad", asList("F-7", "F-8", "G-10"));

        // Fallback coordinates for markers when lat/lng are missing in Firebase records.
        // These are used ONLY if a record has no usable coordinates.
        areaToPoint.put("Gulgasht", new GeoPoint(30.2006, 71.4681));
        areaToPoint.put("Bosan Road", new GeoPoint(30.1846, 71.4705));
        areaToPoint.put("Hussain Agahi", new GeoPoint(30.1985, 71.4734));

        areaToPoint.put("DHA", new GeoPoint(31.4697, 74.4100));
        areaToPoint.put("Model Town", new GeoPoint(31.4816, 74.3246));
        areaToPoint.put("Johar Town", new GeoPoint(31.4690, 74.2728));

        areaToPoint.put("Saddar", new GeoPoint(24.8615, 67.0304));
        areaToPoint.put("Clifton", new GeoPoint(24.8166, 67.0300));
        areaToPoint.put("Defence", new GeoPoint(24.8023, 67.0736));

        areaToPoint.put("F-7", new GeoPoint(33.7295, 73.0436));
        areaToPoint.put("F-8", new GeoPoint(33.7113, 73.0507));
        areaToPoint.put("G-10", new GeoPoint(33.6760, 73.0220));
    }

    private void setupSpinners() {
        List<String> cities = new ArrayList<>();
        cities.add("Select City");
        cities.add("Multan");
        cities.add("Lahore");
        cities.add("Karachi");
        cities.add("Islamabad");

        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cities
        );
        spinnerCity.setAdapter(cityAdapter);

        updateAreaSpinner(null);

        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String city = (String) parent.getItemAtPosition(position);
                if ("Select City".equalsIgnoreCase(city)) {
                    selectedCity = null;
                    selectedArea = null;
                    updateAreaSpinner(null);
                } else {
                    selectedCity = city;
                    selectedArea = null;
                    updateAreaSpinner(city);
                }
                applyFilterAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCity = null;
                selectedArea = null;
                updateAreaSpinner(null);
                applyFilterAndRender();
            }
        });

        spinnerArea.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String area = (String) parent.getItemAtPosition(position);
                if ("Select Area".equalsIgnoreCase(area)) {
                    selectedArea = null;
                } else {
                    selectedArea = area;
                }
                applyFilterAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedArea = null;
                applyFilterAndRender();
            }
        });
    }

    private void updateAreaSpinner(String city) {
        List<String> areas = new ArrayList<>();
        areas.add("Select Area");

        if (city != null && cityToAreas.containsKey(city)) {
            areas.addAll(cityToAreas.get(city));
        }

        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                areas
        );
        spinnerArea.setAdapter(areaAdapter);
    }

    private void loadCrimeDataFromFirebase() {
        progressMap.setVisibility(View.VISIBLE);
        dataLoaded = false;

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        // Uses configured firebase_database_url (see AndroidManifest/strings).
        crimeRef = FirebaseDatabase.getInstance().getReference(FIREBASE_CRIME_PATH);

        crimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isActive || isFinishing() || isDestroyed()) return;
                progressMap.setVisibility(View.GONE);

                allCrimeData.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    CrimeData item = parseCrimeData(child);
                    if (item == null) continue;
                    allCrimeData.add(item);
                }

                // After loading allCrimeData, count reports per city+area, then assign riskLevel
                Map<String, Integer> countMap = new HashMap<>();
                for (CrimeData item : allCrimeData) {
                    String key = safe(item.getCity()) + "|" + safe(item.getArea());
                    countMap.put(key, countMap.getOrDefault(key, 0) + 1);
                }
                for (CrimeData item : allCrimeData) {
                    String key = safe(item.getCity()) + "|" + safe(item.getArea());
                    int count = countMap.get(key);
                    if (count > 10) item.setRiskLevel("High");
                    else if (count > 5) item.setRiskLevel("Medium");
                    else item.setRiskLevel("Low");
                }
                dataLoaded = true;
                if (!allCrimeData.isEmpty()) {
                    applyFilterAndRender();
                } else {
                    Toast.makeText(MapActivity.this, "No crime data available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isActive || isFinishing() || isDestroyed()) return;
                progressMap.setVisibility(View.GONE);
                dataLoaded = true;
                Toast.makeText(MapActivity.this, "Firebase error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        crimeRef.addListenerForSingleValueEvent(crimeListener);
    }

    private void applyFilterAndRender() {
        if (!dataLoaded) return;
        if (!isActive || isFinishing() || isDestroyed()) return;
        if (map == null) return;

        if (selectedCity == null) {
            map.setVisibility(View.GONE);
            textMapEmpty.setVisibility(View.VISIBLE);
            textMapEmpty.setText("Please select a city to view crime map");
            return;
        }

        if (selectedArea == null || selectedArea.equals("Select Area")) {
            map.setVisibility(View.GONE);
            textMapEmpty.setVisibility(View.VISIBLE);
            textMapEmpty.setText("Please select an area to view markers");
            return;
        }

        map.setVisibility(View.VISIBLE);
        textMapEmpty.setVisibility(View.GONE);

        List<CrimeData> filtered = filterCrimeData(allCrimeData, selectedCity, selectedArea);
        addMarkers(filtered);
        focusMap(filtered);
    }

    private List<CrimeData> filterCrimeData(List<CrimeData> source, String city, String area) {
        if (source == null || source.isEmpty()) return new ArrayList<>();

        boolean hasCity = city != null && !city.trim().isEmpty();
        boolean hasArea = area != null && !area.trim().isEmpty();

        if (!hasCity && !hasArea) {
            return new ArrayList<>(source);
        }

        List<CrimeData> out = new ArrayList<>();
        for (CrimeData item : source) {
            if (item == null) continue;

            if (hasCity) {
                String itemCity = safe(item.getCity());
                if (!itemCity.equalsIgnoreCase(city)) continue;
            }

            if (hasArea) {
                String itemArea = safe(item.getArea());
                if (!itemArea.equalsIgnoreCase(area)) continue;
            }

            out.add(item);
        }
        return out;
    }

    private void addMarkers(List<CrimeData> list) {
        if (map == null) return;

        map.getOverlays().clear(); // Clear old markers (performance)

        for (CrimeData item : list) {
            GeoPoint point = resolvePoint(item);
            if (point == null) continue;

            Marker marker = new Marker(map);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // Color markers based on risk level
            String riskBucket = riskBucket(item.getRiskLevel());
            if ("high".equals(riskBucket)) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_marker_red));
            } else if ("medium".equals(riskBucket)) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_marker_orange));
            } else {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_marker_green));
            }

            marker.setOnMarkerClickListener((m, mapView) -> {
                String info = riskLabel(item.getRiskLevel()) +
                        "\nType: " + (item.getCrimeType() != null ? item.getCrimeType() : "Unknown") +
                        "\nArea: " + safe(item.getArea());
                Toast.makeText(MapActivity.this, info, Toast.LENGTH_SHORT).show();
                return true;
            });

            map.getOverlays().add(marker);
        }

        map.invalidate(); // invalidate once after all markers added
    }

    // Focus map on first crime location (better UX)
    private void focusMap(List<CrimeData> list) {
        if (!list.isEmpty()) {
            CrimeData first = list.get(0);
            GeoPoint point = resolvePoint(first);
            if (point == null) return;

            map.getController().animateTo(point);
            map.getController().setZoom(14.0);
        }
    }

    private static List<String> asList(String a, String b, String c) {
        List<String> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        list.add(c);
        return list;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String riskBucket(String riskLevel) {
        String r = safe(riskLevel).toLowerCase(Locale.US);
        if (r.contains("high")) return "high";
        if (r.contains("medium")) return "medium";
        if (r.contains("low")) return "low";
        return "low";
    }

    private static String riskLabel(String riskLevel) {
        String bucket = riskBucket(riskLevel);
        if ("high".equals(bucket)) return "High Risk";
        if ("medium".equals(bucket)) return "Medium Risk";
        return "Low Risk";
    }

    private CrimeData parseCrimeData(DataSnapshot child) {
        if (child == null) return null;

        CrimeData item = new CrimeData();
        item.setLocation(child.child("location").getValue(String.class));
        item.setCrimeType(child.child("crimeType").getValue(String.class));
        item.setRiskLevel(child.child("riskLevel").getValue(String.class));
        item.setCity(child.child("city").getValue(String.class));
        item.setArea(child.child("area").getValue(String.class));
        item.setDate(child.child("date").getValue(String.class));

        Double lat = getDouble(child, "lat");
        Double lng = getDouble(child, "lng");
        if (lat != null) item.setLat(lat);
        if (lng != null) item.setLng(lng);

        return item;
    }

    private static Double getDouble(DataSnapshot node, String key) {
        Object v = node.child(key).getValue();
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private GeoPoint resolvePoint(CrimeData item) {
        if (item == null) return null;

        double lat = item.getLat();
        double lng = item.getLng();
        if (lat != 0.0d || lng != 0.0d) {
            return new GeoPoint(lat, lng);
        }

        String area = safe(item.getArea());
        if (!area.isEmpty()) {
            GeoPoint p = areaToPoint.get(area);
            if (p != null) return p;
        }

        // If area is missing/unknown, try city center fallback
        String city = safe(item.getCity());
        if ("Multan".equalsIgnoreCase(city)) return new GeoPoint(30.1575, 71.5249);
        if ("Lahore".equalsIgnoreCase(city)) return new GeoPoint(31.5204, 74.3587);
        if ("Karachi".equalsIgnoreCase(city)) return new GeoPoint(24.8607, 67.0011);
        if ("Islamabad".equalsIgnoreCase(city)) return new GeoPoint(33.6844, 73.0479);

        return null;
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        isActive = false;
        if (crimeRef != null && crimeListener != null) {
            crimeRef.removeEventListener(crimeListener);
        }
        super.onDestroy();
    }
}