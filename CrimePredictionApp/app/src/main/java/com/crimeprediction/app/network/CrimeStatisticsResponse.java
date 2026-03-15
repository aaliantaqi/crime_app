package com.crimeprediction.app.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Response for GET /crime-statistics (for charts).
 */
public class CrimeStatisticsResponse {
    @SerializedName("monthly_trend")
    private List<MonthlyTrendItem> monthlyTrend;
    @SerializedName("category_distribution")
    private Map<String, Integer> categoryDistribution;
    @SerializedName("high_risk_areas")
    private List<AreaRiskItem> highRiskAreas;

    public List<MonthlyTrendItem> getMonthlyTrend() { return monthlyTrend; }
    public Map<String, Integer> getCategoryDistribution() { return categoryDistribution; }
    public List<AreaRiskItem> getHighRiskAreas() { return highRiskAreas; }

    public static class MonthlyTrendItem {
        @SerializedName("month")
        private String month;
        @SerializedName("count")
        private int count;
        public String getMonth() { return month; }
        public int getCount() { return count; }
    }

    public static class AreaRiskItem {
        @SerializedName("area")
        private String area;
        @SerializedName("risk_score")
        private double riskScore;
        public String getArea() { return area; }
        public double getRiskScore() { return riskScore; }
    }
}
