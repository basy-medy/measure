package com.larvalabs.betweenus.nearby;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import com.larvalabs.betweenus.utils.Utils;

/**
 * Manages Nearby Connections API for peer-to-peer device pairing.
 * Replaces the deprecated Android Beam/NFC implementation.
 * Uses P2P_POINT_TO_POINT strategy — fully offline, no server required for discovery.
 */
public class NearbyConnectionManager {

    private static final String SERVICE_ID = "com.larvalabs.betweenus.PAIR";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;

    private final ConnectionsClient connectionsClient;
    private final NearbyListener listener;
    private String connectedEndpointId;

    public interface NearbyListener {
        void onPartnerFound(String endpointId, String endpointName);
        void onConnected(String endpointId);
        void onConnectionFailed(String endpointId, int statusCode);
        void onDisconnected(String endpointId);
        void onPayloadReceived(String endpointId, String payloadData);
    }

    public NearbyConnectionManager(Context context, NearbyListener listener) {
        this.connectionsClient = Nearby.getConnectionsClient(context);
        this.listener = listener;
    }

    /**
     * Start advertising this device so nearby devices can discover it.
     * @param localName A human-readable name for this device (e.g. the username).
     */
    public void startAdvertising(String localName) {
        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> Utils.log("Nearby: advertising started"))
                .addOnFailureListener(e -> Utils.error("Nearby: advertising failed", e));
    }

    /**
     * Start discovering nearby advertising devices.
     */
    public void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> Utils.log("Nearby: discovery started"))
                .addOnFailureListener(e -> Utils.error("Nearby: discovery failed", e));
    }

    /**
     * Request a connection to a discovered endpoint.
     */
    public void requestConnection(String localName, String endpointId) {
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(unused -> Utils.log("Nearby: connection requested to " + endpointId))
                .addOnFailureListener(e -> Utils.error("Nearby: requestConnection failed", e));
    }

    /**
     * Send a string payload to the connected endpoint.
     */
    public void sendPayload(String data) {
        if (connectedEndpointId != null) {
            Payload payload = Payload.fromBytes(data.getBytes());
            connectionsClient.sendPayload(connectedEndpointId, payload);
            Utils.log("Nearby: payload sent to " + connectedEndpointId);
        }
    }

    /**
     * Stop all advertising, discovery, and disconnect from any connected endpoint.
     */
    public void stopAll() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedEndpointId = null;
        Utils.log("Nearby: stopped all connections");
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            Utils.log("Nearby: endpoint found: " + endpointId + " (" + info.getEndpointName() + ")");
            listener.onPartnerFound(endpointId, info.getEndpointName());
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Utils.log("Nearby: endpoint lost: " + endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            Utils.log("Nearby: connection initiated with " + endpointId
                    + " (" + connectionInfo.getEndpointName() + ")");
            // Auto-accept the connection (both sides must accept)
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            int status = result.getStatus().getStatusCode();
            if (status == ConnectionsStatusCodes.STATUS_OK) {
                Utils.log("Nearby: connected to " + endpointId);
                connectedEndpointId = endpointId;
                connectionsClient.stopAdvertising();
                connectionsClient.stopDiscovery();
                listener.onConnected(endpointId);
            } else {
                Utils.error("Nearby: connection failed to " + endpointId + " status=" + status);
                listener.onConnectionFailed(endpointId, status);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Utils.log("Nearby: disconnected from " + endpointId);
            connectedEndpointId = null;
            listener.onDisconnected(endpointId);
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            byte[] bytes = payload.asBytes();
            if (bytes != null) {
                String data = new String(bytes);
                Utils.log("Nearby: payload received from " + endpointId + ": " + data);
                listener.onPayloadReceived(endpointId, data);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
            // Not needed for small byte payloads
        }
    };
}
