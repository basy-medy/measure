package com.larvalabs.betweenus.client;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit 2 service interface for the BetweenUs backend.
 */
public interface BetweenUsService {

    @POST("/application/registerUser")
    Call<ServerResponse> registerUser(@Query("username") String username, @Query("latitude") double latitude, @Query("longitude") double longitude);

    @POST("/application/setUsername")
    Call<ServerResponse> setUsername(@Query("userId") Long userId, @Query("username") String username);

    @POST("/application/connect")
    Call<ServerResponse> connect(@Query("userId1") Long userId1, @Query("userId2") Long userId2);

    @POST("/application/updateLocation")
    Call<ServerResponse> updateLocation(@Query("userId") Long userId, @Query("latitude") double latitude, @Query("longitude") double longitude);

    @POST("/application/getinfo")
    Call<ServerResponse> getInfoSync(@Query("userId") Long userId);

    @POST("/application/endConversation")
    Call<ServerResponse> endConversation(@Query("userId") Long userId);

}
