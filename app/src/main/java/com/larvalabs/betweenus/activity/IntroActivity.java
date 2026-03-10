package com.larvalabs.betweenus.activity;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.client.ServerResponse;
import com.larvalabs.betweenus.client.ServerUtil;
import com.larvalabs.betweenus.core.DeviceLocation;
import com.larvalabs.betweenus.utils.Utils;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Splash / entry-point activity.
 * Requests location permission first, then attempts server registration.
 * Proceeds to the next screen regardless of server availability.
 */
public class IntroActivity extends Activity {

    public static final long INTRO_DELAY = TimeUnit.SECONDS.toMillis(2);
    private static final int REQUEST_LOCATION_PERMISSION = 2001;
    private static final int REQUEST_BACKGROUND_LOCATION = 2002;

    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startTime = System.currentTimeMillis();
        setContentView(R.layout.activity_welcome);

        // Request location permission before anything else
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION);
        } else {
            // Foreground location already granted — request background location
            requestBackgroundLocationThenProceed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Foreground granted — now request background ("Allow all the time")
                requestBackgroundLocationThenProceed();
            } else {
                Utils.log("Location permission denied, proceeding without location.");
                proceedToNextScreen();
            }
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            // Proceed regardless of result
            refreshLocationAndProceed();
        }
    }

    /**
     * Request ACCESS_BACKGROUND_LOCATION separately (Android requires foreground
     * location to be granted first). This prompts the "Allow all the time" option.
     */
    private void requestBackgroundLocationThenProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        REQUEST_BACKGROUND_LOCATION);
                return;
            }
        }
        refreshLocationAndProceed();
    }

    private void refreshLocationAndProceed() {
        // Now that we have location permission, schedule background updates
        Utils.scheduleAlarmForLocationUpdates(this);

        try {
            android.location.Location location = DeviceLocation.getLocation(this);
            AppSettings appSettings = new AppSettings(this);
            appSettings.updateFromLocation(location);
        } catch (Exception e) {
            Utils.error("Failed to get location on startup", e);
        }
        proceedToNextScreen();
    }

    private void proceedToNextScreen() {
        final AppSettings appSettings = new AppSettings(this);

        if (appSettings.getServerUserId() == -1) {
            Utils.log("No user id in settings, registering with server.");
            try {
                ServerUtil.getService().registerUser(null, appSettings.getLastLatitude(), appSettings.getLastLongitude())
                        .enqueue(new Callback<ServerResponse>() {
                            @Override
                            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                                ServerResponse serverResponse = response.body();
                                if (serverResponse != null && serverResponse.yourUserId != null) {
                                    appSettings.setServerUserId(serverResponse.yourUserId);
                                    Utils.log("Your user id is now " + serverResponse.yourUserId);
                                }
                                navigateWithDelay(UserInfoActivity.class);
                            }

                            @Override
                            public void onFailure(Call<ServerResponse> call, Throwable t) {
                                Utils.error("Error registering user: " + t.getMessage());
                                // Proceed anyway — server may be unavailable
                                navigateWithDelay(UserInfoActivity.class);
                            }
                        });
            } catch (Exception e) {
                Utils.error("Failed to call server", e);
                navigateWithDelay(UserInfoActivity.class);
            }
        } else if (!appSettings.hasSetUsername()) {
            navigateWithDelay(UserInfoActivity.class);
        } else if (!appSettings.getUsersConnected()) {
            navigateDelayThen(new Runnable() {
                @Override
                public void run() {
                    finish();
                    TouchPhonesActivity.launch(IntroActivity.this);
                }
            });
        } else {
            navigateDelayThen(new Runnable() {
                @Override
                public void run() {
                    UserConnectedActivity.launch(IntroActivity.this);
                }
            });
        }
    }

    private void navigateWithDelay(final Class<?> activityClass) {
        navigateDelayThen(new Runnable() {
            @Override
            public void run() {
                finish();
                Utils.launchActivity(IntroActivity.this, activityClass);
            }
        });
    }

    private void navigateDelayThen(Runnable action) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, INTRO_DELAY - elapsed);
        new Handler().postDelayed(action, remaining);
    }
}
