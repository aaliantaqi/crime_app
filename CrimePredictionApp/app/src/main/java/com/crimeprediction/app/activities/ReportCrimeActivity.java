package com.crimeprediction.app.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ReportCrimeActivity extends AppCompatActivity {

    private TextInputEditText editDescription, editDate;
    private Spinner spinnerCity, spinnerArea, spinnerCrimeType;
    private TextView textReportError;
    private MaterialButton btnSubmit;
    private ProgressBar progressReport;

    private DatabaseReference reportsRef;
    private static final String REPORTS_PATH = "reports";
    private TextInputEditText editLat, editLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        editLat = findViewById(R.id.editLat);
        editLng = findViewById(R.id.editLng);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Realtime Database reference — "/reports" node
        reportsRef = FirebaseDatabase.getInstance().getReference(REPORTS_PATH);

        spinnerCity = findViewById(R.id.spinnerCity);
        spinnerCrimeType = findViewById(R.id.spinnerCrimeType);
        spinnerArea = findViewById(R.id.spinnerArea);
        editDescription = findViewById(R.id.editDescription);
        editDate = findViewById(R.id.editDate);
        textReportError = findViewById(R.id.textReportError);
        btnSubmit = findViewById(R.id.btnSubmitReport);
        progressReport = findViewById(R.id.progressReport);

        // Cities dropdown (Location)
        String[] cities = {"Select City", "Karachi", "Lahore", "Islamabad", "Multan"};

        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cities
        );

        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(cityAdapter);

        // Area dropdown depends on selected city
        spinnerCity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String city = cities[position];
                String[] areas;
                switch (city) {
                    case "Multan":
                        areas = new String[]{"Select Area", "Gulgasht", "Bosan Road", "Hussain Agahi"};
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
                        break;
                }
                ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(
                        ReportCrimeActivity.this,
                        android.R.layout.simple_spinner_item,
                        areas
                );
                areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerArea.setAdapter(areaAdapter);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // no-op
            }
        });

        // Crime types dropdown
        String[] crimeTypes = {
                "Select Crime Type",
                "Theft",
                "Assault",
                "Fraud",
                "Vandalism"
        };

        ArrayAdapter<String> crimeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                crimeTypes
        );

        crimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCrimeType.setAdapter(crimeAdapter);

        // Default date
        editDate.setText(
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date())
        );

        // Date picker
        editDate.setOnClickListener(v -> {

            Calendar calendar = Calendar.getInstance();

            DatePickerDialog dialog = new DatePickerDialog(
                    ReportCrimeActivity.this,
                    (view, year, month, day) ->
                            editDate.setText(
                                    String.format(
                                            Locale.getDefault(),
                                            "%04d-%02d-%02d",
                                            year,
                                            month + 1,
                                            day
                                    )
                            ),
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );

            dialog.show();
        });

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        String location = spinnerCity.getSelectedItem() != null
                ? spinnerCity.getSelectedItem().toString().trim() : "";
        String area = spinnerArea.getSelectedItem() != null
                ? spinnerArea.getSelectedItem().toString().trim() : "";
        String crimeType = spinnerCrimeType.getSelectedItem() != null
                ? spinnerCrimeType.getSelectedItem().toString().trim() : "";
        String description = editDescription.getText() != null
                ? editDescription.getText().toString().trim() : "";
        String date = editDate.getText() != null
                ? editDate.getText().toString().trim() : "";
        String latStr = editLat.getText() != null ? editLat.getText().toString().trim() : "";
        String lngStr = editLng.getText() != null ? editLng.getText().toString().trim() : "";

        // Validate all fields first
        if (TextUtils.isEmpty(location) || "Select City".equalsIgnoreCase(location)) {
            Toast.makeText(this, "Please select a city", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(area) || "Select Area".equalsIgnoreCase(area)) {
            Toast.makeText(this, "Please select an area", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(crimeType) || "Select Crime Type".equalsIgnoreCase(crimeType)) {
            Toast.makeText(this, "Please select a crime type", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(description)) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(date)) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
            return;
        }

            if (TextUtils.isEmpty(latStr) || TextUtils.isEmpty(lngStr)) {
                Toast.makeText(this, "Please enter latitude and longitude", Toast.LENGTH_SHORT).show();
                return;
            }

// Resolve coordinates
            double lat = 0.0;
            double lng = 0.0;

            try {
                lat = Double.parseDouble(latStr);
                lng = Double.parseDouble(lngStr);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show();
                return;
            }

            if (lat == 0.0 && lng == 0.0) {
                Toast.makeText(this, "Coordinates cannot be zero", Toast.LENGTH_SHORT).show();
                return;
            }


        textReportError.setVisibility(View.GONE);
        btnSubmit.setEnabled(false);
        progressReport.setVisibility(View.VISIBLE);

        String reportId = reportsRef.push().getKey();
        if (reportId == null) {
            Toast.makeText(this, "Failed to submit report", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }

        Map<String, Object> report = new HashMap<>();
        report.put("crimeType", crimeType);
        report.put("description", description);
        report.put("city", location);
        report.put("area", area);
        report.put("date", date);
        report.put("lat", lat);
        report.put("lng", lng);
        report.put("timestamp", System.currentTimeMillis());

        reportsRef.child(reportId).setValue(report)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(ReportCrimeActivity.this, "Report submitted successfully", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                    btnSubmit.setEnabled(true);
                    progressReport.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ReportCrimeActivity.this, "Failed to submit report", Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    progressReport.setVisibility(View.GONE);
                });
    }
    private void clearInputFields() {
        editDescription.setText("");
        spinnerCity.setSelection(0);
        if (spinnerArea.getAdapter() != null) {
            spinnerArea.setSelection(0);
        }
        spinnerCrimeType.setSelection(0);
        editDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        textReportError.setVisibility(View.GONE);
        progressReport.setVisibility(View.GONE);
    }

}