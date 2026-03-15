package com.crimeprediction.app.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
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
    private Spinner spinnerCity, spinnerCrimeType;
    private TextView textReportError;
    private MaterialButton btnSubmit;

    private DatabaseReference reportsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Realtime Database reference — "Reports" node
        reportsRef = FirebaseDatabase.getInstance().getReference("Reports");

        spinnerCity = findViewById(R.id.spinnerCity);
        spinnerCrimeType = findViewById(R.id.spinnerCrimeType);
        editDescription = findViewById(R.id.editDescription);
        editDate = findViewById(R.id.editDate);
        textReportError = findViewById(R.id.textReportError);
        btnSubmit = findViewById(R.id.btnSubmitReport);

        // Cities dropdown (Location)
        String[] cities = {"Select City", "Karachi", "Lahore", "Islamabad", "Multan"};

        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cities
        );

        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(cityAdapter);

        // Crime types dropdown
        String[] crimeTypes = {"Select Crime Type", "Theft", "Assault", "Fraud", "Vandalism"};

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
        String crimeType = spinnerCrimeType.getSelectedItem() != null
                ? spinnerCrimeType.getSelectedItem().toString().trim() : "";
        String description = editDescription.getText() != null
                ? editDescription.getText().toString().trim() : "";

        textReportError.setVisibility(View.GONE);

        // Require Crime Type, Description, and Location (no "Select..." placeholders)
        if (TextUtils.isEmpty(location) || "Select City".equalsIgnoreCase(location)
                || TextUtils.isEmpty(crimeType) || "Select Crime Type".equalsIgnoreCase(crimeType)
                || TextUtils.isEmpty(description)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);

        String reportId = reportsRef.push().getKey();
        if (reportId == null) {
            Toast.makeText(this, "Failed to submit report", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }

        Map<String, Object> report = new HashMap<>();
        report.put("crimeType", crimeType);
        report.put("description", description);
        report.put("location", location);
        report.put("timestamp", System.currentTimeMillis());

        reportsRef.child(reportId).setValue(report)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(ReportCrimeActivity.this, "Report submitted successfully", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                    btnSubmit.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ReportCrimeActivity.this, "Failed to submit report", Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                });
    }

    private void clearInputFields() {
        editDescription.setText("");
        spinnerCity.setSelection(0);
        spinnerCrimeType.setSelection(0);
        editDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        textReportError.setVisibility(View.GONE);
    }
}