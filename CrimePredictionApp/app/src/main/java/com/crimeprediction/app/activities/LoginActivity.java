package com.crimeprediction.app.activities;

import android.content.Intent;
import android.os.Bundle;
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


public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText editEmail, editPassword;
    private TextView textError;
    private TextView textResendVerification;
    private MaterialButton btnLogin;
    private FirebaseAuth auth;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        progressBar = findViewById(R.id.progressBar);

        // If already logged in AND verified, go straight to dashboard.
        // A signed-in-but-unverified session (e.g. left over from before
        // this flow existed) must not bypass the verification gate.
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
                return;
            } else {
                auth.signOut();
            }
        }

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        textError = findViewById(R.id.textError);
        btnLogin = findViewById(R.id.btnLogin);
        textResendVerification = findViewById(R.id.textResendVerification);

        btnLogin.setOnClickListener(v -> attemptLogin());
        textResendVerification.setOnClickListener(this::resendVerification);

        findViewById(R.id.btnGoToRegister).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        findViewById(R.id.textForgotPassword).setOnClickListener(v -> {
            String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";

            if (TextUtils.isEmpty(email)) {
                emailLayout.setError("Enter your email first");
                return;
            }

            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Reset link sent to your email", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Error: " + (task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Failed to send reset email"), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        passwordLayout.setErrorIconDrawable(null);

        // Case (a): arriving here right after a successful signup.
        // Show the resend link immediately and pre-fill the email
        // (never the password).
        if (getIntent().getBooleanExtra("justSignedUp", false)) {
            String signupEmail = getIntent().getStringExtra("signupEmail");
            if (!TextUtils.isEmpty(signupEmail)) {
                editEmail.setText(signupEmail);
            }
            textResendVerification.setVisibility(View.VISIBLE);
            textError.setVisibility(View.VISIBLE);
            textError.setText("Please verify your email before logging in.");
        }
    }

    private void attemptLogin() {
        clearErrors();
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString() : "";

        if (TextUtils.isEmpty(email)) {
            emailLayout.setError(getString(R.string.email) + " is required");
            return;
        }
        if (!isValidEmail(email)) {
            emailLayout.setError("Enter a valid email");
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

        btnLogin.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();

                        if (user != null && !user.isEmailVerified()) {
                            // Do not let unverified users proceed past login.
                            auth.signOut();
                            textError.setVisibility(View.VISIBLE);
                            textError.setText("Please verify your email first. Check your inbox.");
                            // Case (b): login failed specifically because the
                            // account is unverified — show the resend link now.
                            textResendVerification.setVisibility(View.VISIBLE);
                            return;
                        }

                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finish();
                    } else {
                        textError.setVisibility(View.VISIBLE);
                        String errorMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";

                        // Better error messages
                        if (errorMsg.contains("user not found")) {
                            textError.setText("No account found with this email. Please register.");
                        } else if (errorMsg.contains("wrong password")) {
                            textError.setText("Incorrect password. Please try again.");
                        } else {
                            textError.setText(errorMsg);
                        }
                    }
                });
    }

    /**
     * Resends the verification email for the account currently entered in
     * the email/password fields. Requires both fields to be filled in,
     * since it needs to sign in (silently) to obtain the FirebaseUser.
     */
    public void resendVerification(View view) {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString() : "";

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter your email and password first", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                        Toast.makeText(LoginActivity.this, "Invalid credentials. Cannot resend.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();

                    if (user.isEmailVerified()) {
                        auth.signOut();
                        Toast.makeText(LoginActivity.this, "Email is already verified. Please login.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    user.sendEmailVerification()
                            .addOnCompleteListener(sendTask -> {
                                auth.signOut();
                                if (sendTask.isSuccessful()) {
                                    Toast.makeText(LoginActivity.this, "Verification email sent! Check your inbox.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(LoginActivity.this, "Could not send verification email. Try again.", Toast.LENGTH_SHORT).show();
                                }
                            });
                });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void clearErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        textError.setVisibility(View.GONE);
    }
}