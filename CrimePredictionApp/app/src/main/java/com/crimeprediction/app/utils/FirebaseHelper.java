package com.crimeprediction.app.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Helper for Firebase Auth and Firestore access.
 */
public class FirebaseHelper {

    private static FirebaseAuth auth;
    private static FirebaseFirestore db;

    public static FirebaseAuth getAuth() {
        if (auth == null) {
            auth = FirebaseAuth.getInstance();
        }
        return auth;
    }

    public static FirebaseFirestore getFirestore() {
        if (db == null) {
            db = FirebaseFirestore.getInstance();
        }
        return db;
    }

    public static String getCurrentUserId() {
        if (getAuth().getCurrentUser() != null) {
            return getAuth().getCurrentUser().getUid();
        }
        return null;
    }
}
