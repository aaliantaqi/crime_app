package com.crimeprediction.app.models;

import com.google.gson.annotations.SerializedName;

/**
 * Model for crime data (e.g. from API or Firestore).
 */
public class CrimeData {
    @SerializedName("location")
    private String location;
    @SerializedName("crime_type")
    private String crimeType;
    @SerializedName("risk_level")
    private String riskLevel;
    @SerializedName("lat")
    private double lat;
    @SerializedName("lng")
    private double lng;
    @SerializedName("date")
    private String date;

    // Firebase filtering fields (also present in API payloads, if any)
    @SerializedName("city")
    private String city;
    @SerializedName("area")
    private String area;

    public CrimeData() {}

    public CrimeData(String location, String crimeType, String riskLevel, double lat, double lng, String date) {
        this.location = location;
        this.crimeType = crimeType;
        this.riskLevel = riskLevel;
        this.lat = lat;
        this.lng = lng;
        this.date = date;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCrimeType() { return crimeType; }
    public void setCrimeType(String crimeType) { this.crimeType = crimeType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
}
