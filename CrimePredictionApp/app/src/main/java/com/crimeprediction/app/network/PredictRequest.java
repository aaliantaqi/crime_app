package com.crimeprediction.app.network;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for POST /predict-crime.
 */
public class PredictRequest {
    @SerializedName("city")
    private String city;
    @SerializedName("area")
    private String area;
    @SerializedName("time_of_day")
    private String timeOfDay;
    @SerializedName("day_of_week")
    private String dayOfWeek;
    @SerializedName("crime_category")
    private String crimeCategory; // optional

    public PredictRequest(String city, String area, String timeOfDay, String dayOfWeek) {
        this.city = city;
        this.area = area;
        this.timeOfDay = timeOfDay;
        this.dayOfWeek = dayOfWeek;
    }

    public void setCrimeCategory(String crimeCategory) { this.crimeCategory = crimeCategory; }
    public String getCity() { return city; }
    public String getArea() { return area; }
    public String getTimeOfDay() { return timeOfDay; }
    public String getDayOfWeek() { return dayOfWeek; }
    public String getCrimeCategory() { return crimeCategory; }
}
