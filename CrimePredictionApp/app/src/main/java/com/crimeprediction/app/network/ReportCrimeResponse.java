package com.crimeprediction.app.network;

import com.google.gson.annotations.SerializedName;

/**
 * Response for POST /report-crime.
 */
public class ReportCrimeResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
