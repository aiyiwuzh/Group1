
package com.example.splitsmart.network;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {

    @GET("api/health")
    Call<String> checkHealth();

}
