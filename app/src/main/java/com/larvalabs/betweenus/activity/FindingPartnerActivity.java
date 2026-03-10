package com.larvalabs.betweenus.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.client.ServerResponse;
import com.larvalabs.betweenus.client.ServerUtil;
import com.larvalabs.betweenus.utils.Utils;

import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Handles the post-discovery pairing flow.
 * Two launch modes:
 *   1) pollServer=true  — we sent our ID and are polling the server for connection confirmation.
 *   2) partnerId!=null   — we received the partner's ID via Nearby and call connect() on the server.
 */
public class FindingPartnerActivity extends Activity {

    private static final String INTENT_POLL = "poll";
    private static final String INTENT_PARTNER_ID = "partnerId";
    private static final int MAX_SERVER_POLL_ATTEMPTS = 10;

    /** Launch in polling mode (we already sent our ID, waiting for server to reflect connection). */
    public static void launchActivity(Context context, boolean pollServer) {
        Intent intent = new Intent(context, FindingPartnerActivity.class);
        intent.putExtra(INTENT_POLL, pollServer);
        context.startActivity(intent);
    }

    /** Launch with a known partner user ID received via Nearby Connections payload. */
    public static void launchWithPartnerId(Context context, long partnerId) {
        Intent intent = new Intent(context, FindingPartnerActivity.class);
        intent.putExtra(INTENT_PARTNER_ID, partnerId);
        context.startActivity(intent);
    }

    private AppSettings appSettings;
    private boolean pollServer = false;
    private Timer timer = new Timer();
    private int serverPollAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding_partner);

        appSettings = new AppSettings(this);

        ImageView img = (ImageView) findViewById(R.id.finding_partner_image);
        img.setImageResource(R.drawable.finding_partner);
        AnimationDrawable frameAnimation = (AnimationDrawable) img.getDrawable();
        frameAnimation.setOneShot(false);
        frameAnimation.start();

        if (getIntent() != null) {
            pollServer = getIntent().getBooleanExtra(INTENT_POLL, false);

            long partnerId = getIntent().getLongExtra(INTENT_PARTNER_ID, -1);
            if (partnerId != -1) {
                connectWithPartner(partnerId);
                return;
            }
        }

        Utils.log("Should poll server: " + pollServer);

        if (pollServer) {
            startPolling();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        timer.cancel();
    }

    /** Directly connect with the partner whose ID we received via Nearby. */
    private void connectWithPartner(long otherUserId) {
        Utils.log("Attempting to pair with user id " + otherUserId);
        ServerUtil.getService().connect(appSettings.getServerUserId(), otherUserId)
                .enqueue(new Callback<ServerResponse>() {
                    @Override
                    public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                        ServerResponse serverResponse = response.body();
                        if (serverResponse != null) {
                            Utils.log("Users connected.");
                            appSettings.updateFromServerResponse(serverResponse);
                            Utils.updateAppWidgets(FindingPartnerActivity.this);
                            UserConnectedActivity.launch(FindingPartnerActivity.this);
                        } else {
                            Toast.makeText(FindingPartnerActivity.this,
                                    "User connection failed", Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(Call<ServerResponse> call, Throwable t) {
                        Utils.error("User connection failed: " + t.getMessage());
                        Toast.makeText(FindingPartnerActivity.this,
                                "User connection failed", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    /** Poll the server periodically to check if the partner has completed the connection. */
    private void startPolling() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Utils.log("Checking server to see if users connected...");

                if (serverPollAttempts > MAX_SERVER_POLL_ATTEMPTS) {
                    Utils.log("Exceeded max server checks for user connection, aborting.");
                    timer.cancel();
                    FindingPartnerActivity.this.finish();
                    return;
                }

                try {
                    retrofit2.Response<ServerResponse> resp =
                            ServerUtil.getService().getInfoSync(appSettings.getServerUserId()).execute();
                    ServerResponse serverResponse = resp.body();
                    serverPollAttempts++;
                    if (serverResponse != null) {
                        appSettings.updateFromServerResponse(serverResponse);
                        if (serverResponse.isConnected) {
                            Utils.log("Connected to user! Other user is " + serverResponse.otherUsername);
                            Utils.updateAppWidgets(FindingPartnerActivity.this);
                            UserConnectedActivity.launch(FindingPartnerActivity.this);
                            finish();
                            timer.cancel();
                        }
                    } else {
                        Utils.error("Can't connect to user because location unavailable.");
                    }
                } catch (Exception e) {
                    Utils.error("Server poll failed", e);
                    serverPollAttempts++;
                }
            }
        }, 0, 1000);
    }
}

