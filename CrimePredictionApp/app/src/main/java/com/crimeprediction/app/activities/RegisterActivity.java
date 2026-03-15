package com.crimeprediction.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Register screen: Firebase Auth with Name, Email, Password, Confirm Password.
 * Validates and stores user profile in Firestore.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText editName, editEmail, editPassword, editConfirmPassword;
    private TextView textError;
    private MaterialButton btnRegister;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);
        textError = findViewById(R.id.textError);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.btnGoToLogin).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        clearErrors();
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString() : "";
        String confirm = editConfirmPassword.getText() != null ? editConfirmPassword.getText().toString() : "";

        if (TextUtils.isEmpty(name)) {
            nameLayout.setError(getString(R.string.name) + " is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            emailLayout.setError(getString(R.string.email) + " is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError(getString(R.string.password) + " is required");
            return;
        }
        if (password.length() < 6) {
            passwordLayout.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return;
        }

        btnRegister.setEnabled(false);
        textError.setVisibility(View.GONE);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("email", email);
                        user.put("createdAt", System.currentTimeMillis());
                        db.collection("users").document(uid).set(user)
                                .addOnSuccessListener(aVoid -> {
                                    startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    textError.setVisibility(View.VISIBLE);
                                    textError.setText(e.getMessage());
                                    btnRegister.setEnabled(true);
                                });
                    } else {
                        textError.setVisibility(View.VISIBLE);
                        textError.setText(task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed");
                        btnRegister.setEnabled(true);
                    }
                });
    }

    private void clearErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);
        textError.setVisibility(View.GONE);
    }
}
