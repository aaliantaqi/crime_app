package com.crimeprediction.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import com.crimeprediction.app.R;
import com.crimeprediction.app.models.CrimeData;
import com.crimeprediction.app.network.ApiClient;
import com.crimeprediction.app.network.ApiService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Crime Map: Google Maps with markers for crime locations and risk levels.
 */
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ProgressBar progressMap;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        progressMap = findViewById(R.id.progressMap);
        apiService = ApiClient.getApiService();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        // Default: Multan
        LatLng defaultLatLng = new LatLng(30.1575, 71.5249);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12f));

        progressMap.setVisibility(View.VISIBLE);
        apiService.getCrimeMapData().enqueue(new Callback<List<CrimeData>>() {
            @Override
            public void onResponse(Call<List<CrimeData>> call, Response<List<CrimeData>> response) {
                progressMap.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    addMarkers(response.body());
                } else {
                    Toast.makeText(MapActivity.this, "Could not load map data", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<CrimeData>> call, Throwable t) {
                progressMap.setVisibility(View.GONE);
                Toast.makeText(MapActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMarkers(List<CrimeData> list) {
        if (mMap == null) return;
        for (CrimeData item : list) {
            LatLng pos = new LatLng(item.getLat(), item.getLng());
            float hue = BitmapDescriptorFactory.HUE_RED;
            if ("Medium".equals(item.getRiskLevel())) hue = BitmapDescriptorFactory.HUE_ORANGE;
            if ("Low".equals(item.getRiskLevel())) hue = BitmapDescriptorFactory.HUE_GREEN;
            mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(item.getLocation())
                    .snippet(item.getCrimeType() + " - " + item.getRiskLevel())
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
        }
    }
}
