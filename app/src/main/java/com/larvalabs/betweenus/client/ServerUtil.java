package com.larvalabs.betweenus.client;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Provides a singleton Retrofit 2 client for the BetweenUs backend.
 */
public class ServerUtil {

    private static final String SERVER_DEV = "http://192.168.8.167:9000";
    private static final String SERVER_PROD = "https://betweenusserver.herokuapp.com/";

    private static BetweenUsService service;

    public static BetweenUsService getService() {
        if (service == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(SERVER_PROD)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(BetweenUsService.class);
        }
        return service;
    }

}