package com.crimeprediction.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.crimeprediction.app.R;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Splash screen: displays app logo and title for 2 seconds,
 * then routes based on authentication state.
 *
 * Logged in users → Dashboard
 * New/returning users → RegisterActivity
 *
 * Uses existing activity_splash layout (unchanged).
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2000;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);  // Your existing splash layout

        auth = FirebaseAuth.getInstance();

        // Show splash for 2 seconds, then route based on login state
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (auth.getCurrentUser() != null) {
                // User is logged in → skip auth, go straight to Dashboard
                startActivity(new Intent(SplashActivity.this, DashboardActivity.class));
            } else {
                // No user logged in → show RegisterActivity (first-time friendly)
                startActivity(new Intent(SplashActivity.this, RegisterActivity.class));
            }
            finish();
        }, SPLASH_DELAY_MS);
    }
}