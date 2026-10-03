package com.crimeprediction.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.crimeprediction.app.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MaterialCardView cardPredict = findViewById(R.id.cardPredict);
        MaterialCardView cardMap = findViewById(R.id.cardMap);
        MaterialCardView cardStatistics = findViewById(R.id.cardStatistics);
        MaterialCardView cardReport = findViewById(R.id.cardReport);

        cardPredict.setOnClickListener(v -> startActivity(new Intent(this, PredictionActivity.class)));
        cardMap.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        cardStatistics.setOnClickListener(v -> startActivity(new Intent(this, StatisticsActivity.class)));
        cardReport.setOnClickListener(v -> startActivity(new Intent(this, ReportCrimeActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_logout) {

            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}