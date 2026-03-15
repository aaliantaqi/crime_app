package com.crimeprediction.app.network;

import com.crimeprediction.app.models.CrimeData;
import com.crimeprediction.app.models.PredictionResult;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Retrofit API interface for Crime Rate Prediction backend.
 */
public interface ApiService {

    @POST("predict-crime")
    Call<PredictionResult> predictCrime(@Body PredictRequest request);

    @GET("crime-statistics")
    Call<CrimeStatisticsResponse> getCrimeStatistics();

    @GET("crime-map-data")
    Call<List<CrimeData>> getCrimeMapData();

    @POST("report-crime")
    Call<ReportCrimeResponse> reportCrime(@Body ReportCrimeRequest request);
}
