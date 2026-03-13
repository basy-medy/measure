package me.mattlogan.twentyseven.messages;

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

import timber.log.Timber;

public class NearbyConnectionManager {

  private static final String SERVICE_ID = "me.mattlogan.twentyseven";

  private final ConnectionsClient connectionsClient;
  private final IncomingMessageRouter messageRouter;
  private final Set<String> connectedEndpoints = new HashSet<>();
  private ConnectionListener connectionListener;
  private boolean started = false;

  public interface ConnectionListener {
    void onPeerConnected(int totalConnected);
    void onPeerDisconnected(int totalConnected);
    void onNearbyStarted();
  }

  public NearbyConnectionManager(Context context, IncomingMessageRouter messageRouter) {
    this.connectionsClient = Nearby.getConnectionsClient(context);
    this.messageRouter = messageRouter;
  }

  public void setConnectionListener(ConnectionListener listener) {
    this.connectionListener = listener;
  }

  public void start() {
    if (started) return;
    started = true;
    startAdvertising();
    startDiscovery();
  }

  public void stop() {
    started = false;
    connectionsClient.stopAdvertising();
    connectionsClient.stopDiscovery();
    connectionsClient.stopAllEndpoints();
    connectedEndpoints.clear();
  }

  public void sendMessage(String message) {
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    Payload payload = Payload.fromBytes(bytes);
    for (String endpoint : connectedEndpoints) {
      connectionsClient.sendPayload(endpoint, payload);
    }
  }

  public int getConnectedCount() {
    return connectedEndpoints.size();
  }

  private void startAdvertising() {
    AdvertisingOptions options = new AdvertisingOptions.Builder()
        .setStrategy(Strategy.P2P_CLUSTER)
        .build();
    connectionsClient.startAdvertising(
        Build.MODEL, SERVICE_ID, connectionLifecycleCallback, options)
        .addOnSuccessListener(unused -> {
          Timber.d("Advertising started");
          if (connectionListener != null) connectionListener.onNearbyStarted();
        })
        .addOnFailureListener(e -> Timber.e(e, "Advertising failed"));
  }

  private void startDiscovery() {
    DiscoveryOptions options = new DiscoveryOptions.Builder()
        .setStrategy(Strategy.P2P_CLUSTER)
        .build();
    connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
        .addOnSuccessListener(unused -> Timber.d("Discovery started"))
        .addOnFailureListener(e -> Timber.e(e, "Discovery failed"));
  }

  private final EndpointDiscoveryCallback endpointDiscoveryCallback =
      new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId,
            @NonNull DiscoveredEndpointInfo info) {
          Timber.d("Endpoint found: %s (%s)", endpointId, info.getEndpointName());
          connectionsClient.requestConnection(
              Build.MODEL, endpointId, connectionLifecycleCallback);
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
          Timber.d("Endpoint lost: %s", endpointId);
        }
      };

  private final ConnectionLifecycleCallback connectionLifecycleCallback =
      new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId,
            @NonNull ConnectionInfo info) {
          Timber.d("Connection initiated with: %s", info.getEndpointName());
          connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId,
            @NonNull ConnectionResolution result) {
          if (result.getStatus().isSuccess()) {
            connectedEndpoints.add(endpointId);
            Timber.d("Connected to: %s (total: %d)", endpointId, connectedEndpoints.size());
            if (connectionListener != null) {
              connectionListener.onPeerConnected(connectedEndpoints.size());
            }
          } else {
            Timber.d("Connection failed: %s", result.getStatus());
          }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
          connectedEndpoints.remove(endpointId);
          Timber.d("Disconnected from: %s (total: %d)", endpointId, connectedEndpoints.size());
          if (connectionListener != null) {
            connectionListener.onPeerDisconnected(connectedEndpoints.size());
          }
        }
      };

  private final PayloadCallback payloadCallback = new PayloadCallback() {
    @Override
    public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
      if (payload.getType() == Payload.Type.BYTES) {
        byte[] bytes = payload.asBytes();
        if (bytes != null) {
          String message = new String(bytes, StandardCharsets.UTF_8);
          Timber.d("Payload received from %s: %s", endpointId, message);
          messageRouter.onMessageReceived(message);
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
