package com.larvalabs.betweenus.nearby;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.larvalabs.betweenus.utils.Utils;

/**
 * Manages Nearby Connections API for peer-to-peer device pairing.
 * Modeled after the 3x3x3 reference implementation.
 * Uses P2P_CLUSTER strategy — fully offline, no server required for discovery.
 */
public class NearbyConnectionManager {

    private static final String SERVICE_ID = "com.larvalabs.betweenus";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private final ConnectionsClient connectionsClient;
    private final Set<String> connectedEndpoints = new HashSet<>();
    private ConnectionListener connectionListener;
    private boolean started = false;

    public interface ConnectionListener {
        /** Called when a connection is being initiated — provides auth token for user confirmation. */
        void onConnectionInitiated(String endpointId, String endpointName, String authToken);
        void onPeerConnected(String endpointId, int totalConnected);
        void onPeerDisconnected(String endpointId, int totalConnected);
        void onConnectionFailed(String endpointId);
        void onPayloadReceived(String endpointId, String data);
    }

    public NearbyConnectionManager(Context context) {
        this.connectionsClient = Nearby.getConnectionsClient(context);
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /** Start advertising + discovery simultaneously. */
    public void start() {
        if (started) return;
        started = true;
        startAdvertising();
        startDiscovery();
    }

    /** Stop all advertising, discovery, and disconnect from all endpoints. */
    public void stop() {
        started = false;
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedEndpoints.clear();
        Utils.log("Nearby: stopped all connections");
    }

    /** Accept a pending connection (call after user confirms the auth token). */
    public void acceptConnection(String endpointId) {
        connectionsClient.acceptConnection(endpointId, payloadCallback);
        Utils.log("Nearby: accepted connection to " + endpointId);
    }

    /** Reject a pending connection. */
    public void rejectConnection(String endpointId) {
        connectionsClient.rejectConnection(endpointId);
        Utils.log("Nearby: rejected connection to " + endpointId);
    }

    /** Send a UTF-8 string payload to all connected endpoints. */
    public void sendPayload(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        Payload payload = Payload.fromBytes(bytes);
        for (String endpoint : connectedEndpoints) {
            connectionsClient.sendPayload(endpoint, payload);
        }
        Utils.log("Nearby: payload sent to " + connectedEndpoints.size() + " endpoint(s)");
    }

    public int getConnectedCount() {
        return connectedEndpoints.size();
    }

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startAdvertising(
                Build.MODEL, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> Utils.log("Nearby: advertising started"))
                .addOnFailureListener(e -> Utils.error("Nearby: advertising failed", e));
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> Utils.log("Nearby: discovery started"))
                .addOnFailureListener(e -> Utils.error("Nearby: discovery failed", e));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId,
                                            @NonNull DiscoveredEndpointInfo info) {
                    Utils.log("Nearby: endpoint found: " + endpointId
                            + " (" + info.getEndpointName() + ")");
                    connectionsClient.requestConnection(
                            Build.MODEL, endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    Utils.log("Nearby: endpoint lost: " + endpointId);
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId,
                                                  @NonNull ConnectionInfo info) {
                    Utils.log("Nearby: connection initiated with " + endpointId
                            + " (" + info.getEndpointName() + ")"
                            + " token=" + info.getAuthenticationDigits());

                    if (connectionListener != null) {
                        // Notify the UI so it can show the confirmation dialog
                        connectionListener.onConnectionInitiated(
                                endpointId,
                                info.getEndpointName(),
                                info.getAuthenticationDigits());
                    } else {
                        // No listener — auto-accept (fallback)
                        connectionsClient.acceptConnection(endpointId, payloadCallback);
                    }
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                               @NonNull ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        connectedEndpoints.add(endpointId);
                        Utils.log("Nearby: connected to " + endpointId
                                + " (total: " + connectedEndpoints.size() + ")");
                        if (connectionListener != null) {
                            connectionListener.onPeerConnected(
                                    endpointId, connectedEndpoints.size());
                        }
                    } else {
                        Utils.error("Nearby: connection failed to " + endpointId
                                + " status=" + result.getStatus());
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed(endpointId);
                        }
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    connectedEndpoints.remove(endpointId);
                    Utils.log("Nearby: disconnected from " + endpointId
                            + " (total: " + connectedEndpoints.size() + ")");
                    if (connectionListener != null) {
                        connectionListener.onPeerDisconnected(
                                endpointId, connectedEndpoints.size());
                    }
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                if (bytes != null) {
                    String data = new String(bytes, StandardCharsets.UTF_8);
                    Utils.log("Nearby: payload received from " + endpointId + ": " + data);
                    if (connectionListener != null) {
                        connectionListener.onPayloadReceived(endpointId, data);
                    }
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
            // Not needed for byte payloads
        }
    };
}
