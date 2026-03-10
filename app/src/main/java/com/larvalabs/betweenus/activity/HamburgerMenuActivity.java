package com.larvalabs.betweenus.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.client.ServerResponse;
import com.larvalabs.betweenus.client.ServerUtil;
import com.larvalabs.betweenus.utils.TextViewUtil;
import com.larvalabs.betweenus.utils.Utils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 *
 */
public class HamburgerMenuActivity extends Activity {

    private AppSettings appSettings;

    public static void launch(Context context) {
        Intent intent = new Intent(context, HamburgerMenuActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appSettings = new AppSettings(this);

        setContentView(R.layout.activity_hamburgermenu);

        // ABOUT BTN
        TextView aboutButton = (TextView) findViewById(R.id.about_btn);
        if (aboutButton != null) {
            aboutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickedAbout(view);
                }
            });
        }

        // UNITS BTN
        TextView unitsButton = (TextView) findViewById(R.id.units_btn);
        if (unitsButton != null) {
            unitsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickedUnits(view);
                }
            });
        }

        // DISCONNECT BTN
        TextView disconnectButton = (TextView) findViewById(R.id.disconnect_btn);
        if (disconnectButton != null) {
            disconnectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickDisconnect(view);
                }
            });
        }

        TextViewUtil.applyTextViewStyles((ViewGroup) findViewById(R.id.root));
    }

    public void clickedMetric(View view) {
        new AppSettings(this).setUnitsStandard(false);

        Toast.makeText(this, "Will now use metric units.", Toast.LENGTH_SHORT).show();
        Utils.updateAppWidgets(this);
        finish();
    }

    public void clickedStandard(View view) {
        new AppSettings(this).setUnitsStandard(true);

        Toast.makeText(this, "Will now use standard units.", Toast.LENGTH_SHORT).show();
        Utils.updateAppWidgets(this);
        finish();
    }

    private void clickedAbout(View view) {
        startActivity(new Intent(this, AboutActivity.class));
//        overridePendingTransition(R.anim.slide_left_in_faster, R.anim.slide_left_out_slower);
    }

    private void clickedUnits(View view) {
        startActivity(new Intent(this, UnitsActivity.class));
    }

    private void clickDisconnect(View view) {
        ServerUtil.getService().endConversation(appSettings.getServerUserId())
                .enqueue(new Callback<ServerResponse>() {
                    @Override
                    public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                        ServerResponse serverResponse = response.body();
                        if (serverResponse != null) {
                            appSettings.updateFromServerResponse(serverResponse);
                        }
                        finish();
                        TouchPhonesActivity.launch(HamburgerMenuActivity.this);
                        Utils.updateAppWidgets(HamburgerMenuActivity.this);
                    }

                    @Override
                    public void onFailure(Call<ServerResponse> call, Throwable t) {
                        Utils.error("Disconnect failed: " + t.getMessage());
                    }
                });
    }

    public void clickedClose(View view) {
        finish();
    }
}
