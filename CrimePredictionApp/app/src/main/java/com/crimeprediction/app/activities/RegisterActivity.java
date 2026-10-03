package com.crimeprediction.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Register screen: Firebase Auth with Name, Email, Password, Confirm Password.
 * Validates and stores user profile in Firestore.
 * Handles duplicate email with redirect to login.
 *
 * Email verification flow:
 * After the account is created and the profile is saved, a verification
 * email is sent and the user is immediately signed out. The account is
 * NOT usable until the email is verified — the user is routed to Login
 * (not Dashboard) and must verify before they can sign in successfully.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText editName, editEmail, editPassword, editConfirmPassword;
    private TextView textError;
    private MaterialButton btnRegister;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        progressBar = findViewById(R.id.progressBar);
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
            passwordLayout.setErrorEnabled(true);
            passwordLayout.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return;
        }

        btnRegister.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("email", email);
                        user.put("createdAt", System.currentTimeMillis());

                        db.collection("users").document(uid).set(user)
                                .addOnSuccessListener(aVoid -> {
                                    sendVerificationAndGoToLogin(auth.getCurrentUser(), email);
                                })
                                .addOnFailureListener(e -> {
                                    // Profile save failed, but the account itself was created.
                                    // Still enforce the verify-before-use rule rather than
                                    // leaving the user signed in with an unsaved profile.
                                    sendVerificationAndGoToLogin(auth.getCurrentUser(), email);
                                });
                    } else {
                        btnRegister.setEnabled(true);
                        String errorMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";

                        // Check if email already in use
                        if (errorMsg.contains("email address is already in use")) {
                            Toast.makeText(RegisterActivity.this,
                                    "You are already registered! Please login.",
                                    Toast.LENGTH_LONG).show();

                            // Redirect to Login after 1.5 seconds
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                finish();
                            }, 1500);
                        } else {
                            textError.setVisibility(View.VISIBLE);
                            textError.setText(errorMsg);
                        }
                    }
                });
    }

    /**
     * Sends the verification email, signs the (unverified) account back out
     * so it cannot be used yet, then routes to Login with the signup email
     * pre-filled and the "resend verification" link made visible.
     */
    private void sendVerificationAndGoToLogin(FirebaseUser user, String email) {
        if (user == null) {
            goToLoginAfterSignup(email);
            return;
        }

        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    // Whether or not the send itself succeeded, the account
                    // must not stay usable — sign out and send to Login.
                    auth.signOut();
                    if (!task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this,
                                "Account created, but the verification email could not be sent. Use \"Resend\" on the login screen.",
                                Toast.LENGTH_LONG).show();
                    }
                    goToLoginAfterSignup(email);
                });
    }

    private void goToLoginAfterSignup(String email) {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.putExtra("justSignedUp", true);
        intent.putExtra("signupEmail", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        nameLayout.setErrorEnabled(false);
        emailLayout.setErrorEnabled(false);
        passwordLayout.setErrorEnabled(false);
        confirmPasswordLayout.setErrorEnabled(false);

        textError.setVisibility(View.GONE);
    }
}