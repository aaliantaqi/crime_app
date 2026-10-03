package com.crimeprediction.app.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Retrofit API client. Set BASE_URL to your backend (e.g. http://10.0.2.2:8000 for emulator).
 */
public class ApiClient {

    // Use 10.0.2.2 for Android emulator to reach host machine's localhost
    public static final String BASE_URL = "https://aaliannnn-crime-backend.hf.space/";

    private static Retrofit retrofit;
    private static ApiService apiService;

    public static ApiService getApiService() {
        if (apiService == null) {
            apiService = getRetrofit().create(ApiService.class);
        }
        return apiService;
    }

    public static void setBaseUrl(String baseUrl) {
        retrofit = null;
        apiService = null;
        // You can store baseUrl and use it in getRetrofit()
    }

    private static Retrofit getRetrofit() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
