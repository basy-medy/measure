package com.larvalabs.betweenus.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.nearby.NearbyConnectionManager;
import com.larvalabs.betweenus.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Initiates device pairing via the Nearby Connections API.
 * Simultaneously advertises and discovers, following the 3x3x3 reference pattern.
 * Shows a confirmation dialog with the authentication token before accepting connections.
 */
public class TouchPhonesActivity extends Activity
        implements NearbyConnectionManager.ConnectionListener {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int REQUEST_BACKGROUND_LOCATION = 1002;

    private AppSettings appSettings;
    private NearbyConnectionManager nearbyManager;
    private boolean hasConnected = false;

    public static void launch(Context context) {
        Intent intent = new Intent(context, TouchPhonesActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phones_touch);

        appSettings = new AppSettings(this);

        ImageView img = (ImageView) findViewById(R.id.pairing_image);
        img.setImageResource(R.drawable.pairing);
        AnimationDrawable frameAnimation = (AnimationDrawable) img.getDrawable();
        frameAnimation.setOneShot(false);
        frameAnimation.start();

        nearbyManager = new NearbyConnectionManager(this);
        nearbyManager.setConnectionListener(this);

        requestPermissionsAndStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        nearbyManager.stop();
    }

    // ── Permissions (following 3x3x3 pattern) ────────────────────────────

    private void requestPermissionsAndStart() {
        List<String> needed = new ArrayList<>();

        // Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfNeeded(needed, Manifest.permission.BLUETOOTH_ADVERTISE);
            addIfNeeded(needed, Manifest.permission.BLUETOOTH_CONNECT);
            addIfNeeded(needed, Manifest.permission.BLUETOOTH_SCAN);
        }

        // Nearby Wi-Fi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfNeeded(needed, Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        // Fine location (all versions, required for Nearby)
        addIfNeeded(needed, Manifest.permission.ACCESS_FINE_LOCATION);

        if (needed.isEmpty()) {
            // Foreground permissions granted — now request background location
            requestBackgroundLocationThenStart();
        } else {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void addIfNeeded(List<String> list, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    /**
     * Request ACCESS_BACKGROUND_LOCATION separately (Android requires this
     * to be requested AFTER foreground location is granted). This enables
     * "Allow all the time" location access.
     */
    private void requestBackgroundLocationThenStart() {
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
        startNearby();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            // Log which permissions were denied (proceed anyway, like 3x3x3)
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Utils.log("Permission denied: " + permissions[i]);
                }
            }
            // Now request background location
            requestBackgroundLocationThenStart();
        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            // Proceed regardless — Nearby may still work without background location
            startNearby();
        }
    }

    // ── Nearby Connections ───────────────────────────────────────────────

    private void startNearby() {
        Utils.log("Starting Nearby advertising + discovery");
        nearbyManager.start();
    }

    // ── ConnectionListener callbacks ─────────────────────────────────────

    @Override
    public void onConnectionInitiated(String endpointId, String endpointName, String authToken) {
        // Show confirmation dialog with the authentication token
        // Both devices must see the same token and accept
        runOnUiThread(() -> new AlertDialog.Builder(TouchPhonesActivity.this)
                .setTitle("Confirm Connection")
                .setMessage("Connect to device \"" + endpointName + "\"?\n\n"
                        + "Confirm that both devices show the code:\n\n"
                        + authToken)
                .setPositiveButton("Accept", (dialog, which) -> {
                    nearbyManager.acceptConnection(endpointId);
                })
                .setNegativeButton("Reject", (dialog, which) -> {
                    nearbyManager.rejectConnection(endpointId);
                })
                .setCancelable(false)
                .show());
    }

    @Override
    public void onPeerConnected(String endpointId, int totalConnected) {
        if (hasConnected) return;
        hasConnected = true;
        Utils.log("Peer connected (total: " + totalConnected + "), sending our userId");

        // Send our server user ID to the partner
        Long myId = appSettings.getServerUserId();
        nearbyManager.sendPayload(myId.toString());

        // Navigate to FindingPartnerActivity to wait for server pairing confirmation
        FindingPartnerActivity.launchActivity(this, true);
    }

    @Override
    public void onPeerDisconnected(String endpointId, int totalConnected) {
        Utils.log("Peer disconnected (remaining: " + totalConnected + ")");
    }

    @Override
    public void onConnectionFailed(String endpointId) {
        Utils.error("Connection to " + endpointId + " failed");
        runOnUiThread(() -> Toast.makeText(this,
                "Connection failed. Keep devices close and try again.",
                Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onPayloadReceived(String endpointId, String payloadData) {
        Utils.log("Received partner payload: " + payloadData);
        try {
            Long otherUserId = Long.parseLong(payloadData);
            runOnUiThread(() -> FindingPartnerActivity.launchWithPartnerId(
                    TouchPhonesActivity.this, otherUserId));
        } catch (NumberFormatException e) {
            Utils.error("Invalid partner ID received", e);
        }
    }
}

