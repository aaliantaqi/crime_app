package com.crimeprediction.app.models;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for /predict-crime API.
 */
public class PredictionResult {
    @SerializedName("risk_level")
    private String riskLevel;
    @SerializedName("probability")
    private double probability;
    @SerializedName("predicted_crime")
    private String predictedCrime;

    public PredictionResult() {}

    public PredictionResult(String riskLevel, double probability, String predictedCrime) {
        this.riskLevel = riskLevel;
        this.probability = probability;
        this.predictedCrime = predictedCrime;
    }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public double getProbability() { return probability; }
    public void setProbability(double probability) { this.probability = probability; }
    public String getPredictedCrime() { return predictedCrime; }
    public void setPredictedCrime(String predictedCrime) { this.predictedCrime = predictedCrime; }
}
