package com.larvalabs.betweenus.activity;

import android.Manifest;
import android.app.Activity;
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
 * Simultaneously advertises and discovers so either device can initiate.
 * Replaces the former NFC/Android Beam implementation.
 */
public class TouchPhonesActivity extends Activity implements NearbyConnectionManager.NearbyListener {

    private static final int REQUEST_PERMISSIONS = 1001;

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

        nearbyManager = new NearbyConnectionManager(this, this);

        if (checkAndRequestPermissions()) {
            startNearby();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (nearbyManager != null) {
            nearbyManager.stopAll();
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────

    private boolean checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();

        // Location is required on all API levels for Nearby Connections
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ Bluetooth permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ Wi-Fi permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startNearby();
            } else {
                Toast.makeText(this,
                        "Permissions are required to find nearby devices.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── Nearby Connections ───────────────────────────────────────────────

    private void startNearby() {
        String localName = appSettings.getUsername();
        Utils.log("Starting Nearby advertising + discovery as: " + localName);
        nearbyManager.startAdvertising(localName);
        nearbyManager.startDiscovery();
    }

    // ── NearbyListener callbacks ─────────────────────────────────────────

    @Override
    public void onPartnerFound(String endpointId, String endpointName) {
        Utils.log("Partner found: " + endpointName + " — requesting connection");
        nearbyManager.requestConnection(appSettings.getUsername(), endpointId);
    }

    @Override
    public void onConnected(String endpointId) {
        if (hasConnected) return;
        hasConnected = true;
        Utils.log("Connected to endpoint, sending our userId");

        // Send our server user ID to the partner
        Long myId = appSettings.getServerUserId();
        nearbyManager.sendPayload(myId.toString());

        // Navigate to FindingPartnerActivity to wait for server pairing confirmation
        FindingPartnerActivity.launchActivity(this, true);
    }

    @Override
    public void onConnectionFailed(String endpointId, int statusCode) {
        Utils.error("Connection to " + endpointId + " failed, code=" + statusCode);
    }

    @Override
    public void onDisconnected(String endpointId) {
        Utils.log("Disconnected from " + endpointId);
    }

    @Override
    public void onPayloadReceived(String endpointId, String payloadData) {
        // If we receive the partner's ID before we navigate away, handle it here
        Utils.log("Received partner payload in TouchPhones: " + payloadData);
        try {
            Long otherUserId = Long.parseLong(payloadData);
            FindingPartnerActivity.launchWithPartnerId(this, otherUserId);
        } catch (NumberFormatException e) {
            Utils.error("Invalid partner ID received", e);
        }
    }
}

