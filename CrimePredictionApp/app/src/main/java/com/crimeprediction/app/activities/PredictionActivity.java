package com.crimeprediction.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.crimeprediction.app.models.PredictionResult;
import com.crimeprediction.app.network.ApiClient;
import com.crimeprediction.app.network.ApiService;
import com.crimeprediction.app.network.PredictRequest;
import com.crimeprediction.app.utils.FirebaseHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Crime Prediction screen: user inputs city, area, time, day; calls backend API;
 * displays risk level, probability, predicted crime type.
 */
public class PredictionActivity extends AppCompatActivity {
    
    private Spinner spinnerTimeOfDay, spinnerDayOfWeek, spinnerCrimeCategory,spinnerCity,spinnerArea;
    private MaterialButton btnPredict;
    private ProgressBar progressPredict;
    private MaterialCardView cardResult;
    private TextView textRiskLevel, textProbability, textPredictedCrime;

    private String selectedTime = "Morning";
    private String selectedDay = "Monday";
    private String selectedCategory = "";

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        apiService = ApiClient.getApiService();

        spinnerCity = findViewById(R.id.spinnerCity);
        spinnerArea = findViewById(R.id.spinnerArea);

        String[] cities = {"Select City", "Multan", "Lahore", "Karachi", "Islamabad"};
        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(cityAdapter);

        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCity = cities[position];
                if (!selectedCity.equals("Select City")) {
                    updateAreaSpinner(selectedCity);
                } else {
                    // Show empty area spinner if city not selected
                    updateAreaSpinner("");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTimeOfDay = findViewById(R.id.spinnerTimeOfDay);
        spinnerDayOfWeek = findViewById(R.id.spinnerDayOfWeek);
        spinnerCrimeCategory = findViewById(R.id.spinnerCrimeCategory);
        btnPredict = findViewById(R.id.btnPredict);
        progressPredict = findViewById(R.id.progressPredict);
        cardResult = findViewById(R.id.cardResult);
        textRiskLevel = findViewById(R.id.textRiskLevel);
        textProbability = findViewById(R.id.textProbability);
        textPredictedCrime = findViewById(R.id.textPredictedCrime);

        setupSpinners();
        btnPredict.setOnClickListener(v -> performPrediction());
    }
    private void updateAreaSpinner(String city) {
        String[] areas;
        switch(city) {
            case "Multan":
                areas = new String[]{"Select Area", "Gulgasht", "Hussain Agahi", "Bosan Road"};
                break;
            case "Lahore":
                areas = new String[]{"Select Area", "DHA", "Model Town", "Johar Town"};
                break;
            case "Karachi":
                areas = new String[]{"Select Area", "Saddar", "Clifton", "Defence"};
                break;
            case "Islamabad":
                areas = new String[]{"Select Area", "F-7", "F-8", "G-10"};
                break;
            default:
                areas = new String[]{"Select Area"};
        }
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, areas);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(areaAdapter);
    }
    private void setupSpinners() {
        String[] times = {"Morning", "Afternoon", "Night"};
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String[] categories = {"Select Category", "Theft", "Assault", "Fraud", "Vandalism"};

        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, times);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeOfDay.setAdapter(timeAdapter);
        spinnerTimeOfDay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTime = times[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDayOfWeek.setAdapter(dayAdapter);
        spinnerDayOfWeek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDay = days[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCrimeCategory.setAdapter(catAdapter);
        spinnerCrimeCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = position == 0 ? "" : categories[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void performPrediction() {

        String city = spinnerCity.getSelectedItem() != null
                ? spinnerCity.getSelectedItem().toString() : "";
        String area = spinnerArea.getSelectedItem() != null
                ? spinnerArea.getSelectedItem().toString() : "";

        if (city.equals("Select City") || city.isEmpty()) {
            Toast.makeText(this, "Please select a city", Toast.LENGTH_SHORT).show();
            return;
        }
        if (area.equals("Select Area") || area.isEmpty()) {
            Toast.makeText(this, "Please select an area", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCategory == null || selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a crime category", Toast.LENGTH_SHORT).show();
            return;
        }

        // UI loading
        cardResult.setVisibility(View.GONE);
        btnPredict.setEnabled(false);
        progressPredict.setVisibility(View.VISIBLE);

        // Go straight to the backend — it's the single source of truth for risk_level
        callApiPrediction(city, area, 0);
    }

    private void showResult(PredictionResult result) {
        cardResult.setVisibility(View.VISIBLE);
        String risk = result.getRiskLevel() != null ? result.getRiskLevel() : "Unknown";
        textRiskLevel.setText(risk);
        textProbability.setText(String.format("%.0f%%", result.getProbability() * 100));

        String predictedCrime;
        if (selectedCategory != null && !selectedCategory.isEmpty()) {
            predictedCrime = selectedCategory;  // Show what user selected
        } else {
            predictedCrime = result.getPredictedCrime() != null && !result.getPredictedCrime().isEmpty()
                    ? result.getPredictedCrime() : "—";
        }
        textPredictedCrime.setText(predictedCrime);

        int colorRes = R.color.risk_medium;
        if ("High".equalsIgnoreCase(risk)) colorRes = R.color.risk_high;
        else if ("Low".equalsIgnoreCase(risk)) colorRes = R.color.risk_low;
        textRiskLevel.setTextColor(getResources().getColor(colorRes, getTheme()));
    }
    private void callApiPrediction(String city, String area, int count) {

        PredictRequest request = new PredictRequest(city, area, selectedTime, selectedDay);

        if (!selectedCategory.isEmpty()) {
            request.setCrimeCategory(selectedCategory);
        }


        apiService.predictCrime(request).enqueue(new Callback<PredictionResult>() {
            @Override
            public void onResponse(Call<PredictionResult> call, Response<PredictionResult> response) {

                progressPredict.setVisibility(View.GONE);
                btnPredict.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {

                    PredictionResult result = response.body();

                    if (result.getRiskLevel() == null || result.getRiskLevel().isEmpty()) {
                        cardResult.setVisibility(View.VISIBLE);
                        textRiskLevel.setText("—");
                        textProbability.setText("—");
                        textPredictedCrime.setText(result.getPredictedCrime() != null ? result.getPredictedCrime() : "—");

                    } else {
                        showResult(result);
                        savePredictionHistory(city, area, result);
                    }

                } else {
                    Toast.makeText(PredictionActivity.this, "API error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PredictionResult> call, Throwable t) {
                progressPredict.setVisibility(View.GONE);
                btnPredict.setEnabled(true);
                Toast.makeText(PredictionActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void savePredictionHistory(String city, String area, PredictionResult result) {

        String uid = FirebaseHelper.getCurrentUserId();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("city", city);
        data.put("area", area);
        data.put("riskLevel", result.getRiskLevel());
        data.put("probability", result.getProbability());
        data.put("predictedCrime", result.getPredictedCrime());
        data.put("timestamp", System.currentTimeMillis());

        // ✅ SAVE TO REALTIME DATABASE (not Firestore)
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Predictions");
        ref.push().setValue(data);
    }
}
