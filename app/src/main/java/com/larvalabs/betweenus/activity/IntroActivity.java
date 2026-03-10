package com.larvalabs.betweenus.activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.client.ServerResponse;
import com.larvalabs.betweenus.client.ServerUtil;
import com.larvalabs.betweenus.utils.Utils;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 *
 */
public class IntroActivity extends Activity {

    public static final long INTRO_DELAY = TimeUnit.SECONDS.toMillis(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final long startTime = System.currentTimeMillis();

        setContentView(R.layout.activity_welcome);

        final AppSettings appSettings = new AppSettings(this);
        if (appSettings.getServerUserId() == -1) {
            Utils.log("No user id in settings, registering with server.");
            ServerUtil.getService().registerUser(null, appSettings.getLastLatitude(), appSettings.getLastLongitude())
                    .enqueue(new Callback<ServerResponse>() {
                        @Override
                        public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                            ServerResponse serverResponse = response.body();
                            if (serverResponse != null) {
                                appSettings.setServerUserId(serverResponse.yourUserId);

                                Utils.log("Your user id is now " + serverResponse.yourUserId);

                                long timeToDelayMinusServerResponseTime = INTRO_DELAY - (System.currentTimeMillis() - startTime);
                                Utils.log("Starting username activity with delay time " + timeToDelayMinusServerResponseTime);
                                runDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        finish();
                                        Utils.launchActivity(IntroActivity.this, UserInfoActivity.class);
                                    }
                                }, timeToDelayMinusServerResponseTime);
                            } else {
                                Utils.error("Error registering user: null response");
                                finish();
                            }
                        }

                        @Override
                        public void onFailure(Call<ServerResponse> call, Throwable t) {
                            Utils.error("Error registering user: " + t.getMessage());
                            finish();
                        }
                    });

        } else if (!appSettings.hasSetUsername()) {
            runDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                    Utils.launchActivity(IntroActivity.this, UserInfoActivity.class);
//                    overridePendingTransition(0, 0);
                }
            }, INTRO_DELAY);
        } else if (!appSettings.getUsersConnected()) {
            runDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                    TouchPhonesActivity.launch(IntroActivity.this);
//                    overridePendingTransition(0, 0);
                }
            }, INTRO_DELAY);
        } else {
            runDelayed(new Runnable() {
                @Override
                public void run() {
                    UserConnectedActivity.launch(IntroActivity.this);
//                    overridePendingTransition(0, 0);
                }
            }, INTRO_DELAY);
        }
    }

    private void runDelayed(Runnable runnable, long delay) {
        new Handler().postDelayed(runnable, delay);
    }
}
