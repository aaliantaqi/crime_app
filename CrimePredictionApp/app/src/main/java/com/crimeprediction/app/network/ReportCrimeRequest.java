package com.crimeprediction.app.network;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for POST /report-crime.
 */
public class ReportCrimeRequest {
    @SerializedName("location")
    private String location;
    @SerializedName("crime_type")
    private String crimeType;
    @SerializedName("description")
    private String description;
    @SerializedName("date")
    private String date;
    @SerializedName("user_id")
    private String userId;

    public ReportCrimeRequest(String location, String crimeType, String description, String date, String userId) {
        this.location = location;
        this.crimeType = crimeType;
        this.description = description;
        this.date = date;
        this.userId = userId;
    }

    public String getLocation() { return location; }
    public String getCrimeType() { return crimeType; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
    public String getUserId() { return userId; }
}
