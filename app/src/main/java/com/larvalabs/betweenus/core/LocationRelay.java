package com.larvalabs.betweenus.core;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.utils.Utils;

/**
 * Handles P2P location sharing via Firebase Realtime Database.
 *
 * Data structure:
 *   /pairs/{pairRoomId}/{userId}/lat
 *   /pairs/{pairRoomId}/{userId}/lng
 *   /pairs/{pairRoomId}/{userId}/timestamp
 *
 * Each device writes its own location and listens for the partner's.
 * Distance is calculated locally using the Haversine formula.
 */
public class LocationRelay {

    private static final String PAIRS_ROOT = "pairs";

    private final DatabaseReference dbRef;
    private ValueEventListener partnerListener;
    private DatabaseReference partnerRef;

    public interface DistanceCallback {
        void onDistanceUpdated(double distanceKm);
        void onPartnerOffline();
    }

    public LocationRelay() {
        this.dbRef = FirebaseDatabase.getInstance().getReference(PAIRS_ROOT);
    }

    /**
     * Push this device's current location to Firebase.
     */
    public void pushLocation(String pairRoomId, String localUserId,
                             double latitude, double longitude) {
        if (pairRoomId == null) return;

        DatabaseReference myRef = dbRef.child(pairRoomId).child(localUserId);
        myRef.child("lat").setValue(latitude);
        myRef.child("lng").setValue(longitude);
        myRef.child("timestamp").setValue(System.currentTimeMillis());

        Utils.log("LocationRelay: pushed " + latitude + "," + longitude
                + " to room " + pairRoomId);
    }

    /**
     * Start listening for the partner's location updates.
     * Calculates distance locally via Haversine and calls back.
     */
    public void startListening(String pairRoomId, String localUserId,
                               String partnerUserId, Context context,
                               DistanceCallback callback) {
        stopListening();

        if (pairRoomId == null || partnerUserId == null) return;

        partnerRef = dbRef.child(pairRoomId).child(partnerUserId);
        partnerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("lat").getValue(Double.class);
                Double lng = snapshot.child("lng").getValue(Double.class);

                if (lat != null && lng != null) {
                    AppSettings settings = new AppSettings(context);
                    double myLat = settings.getLastLatitude();
                    double myLng = settings.getLastLongitude();

                    double distKm = haversine(myLat, myLng, lat, lng);
                    settings.setLastDistance(distKm);
                    Utils.log("LocationRelay: partner at " + lat + "," + lng
                            + " — distance " + distKm + " km");

                    callback.onDistanceUpdated(distKm);
                } else {
                    callback.onPartnerOffline();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Utils.error("LocationRelay: listener cancelled: " + error.getMessage());
            }
        };

        partnerRef.addValueEventListener(partnerListener);
        Utils.log("LocationRelay: listening for partner " + partnerUserId
                + " in room " + pairRoomId);
    }

    /** Stop listening for partner location changes. */
    public void stopListening() {
        if (partnerRef != null && partnerListener != null) {
            partnerRef.removeEventListener(partnerListener);
            partnerRef = null;
            partnerListener = null;
            Utils.log("LocationRelay: stopped listening");
        }
    }

    /** Remove this device's location data from the pair room. */
    public void removeSelf(String pairRoomId, String localUserId) {
        if (pairRoomId == null) return;
        dbRef.child(pairRoomId).child(localUserId).removeValue();
        Utils.log("LocationRelay: removed self from room " + pairRoomId);
    }

    /**
     * Convenience: get device location, push to Firebase, and notify partner.
     * Replaces the old DeviceLocation.refresh() → server flow.
     */
    public static void refreshAndPush(Context context) {
        AppSettings settings = new AppSettings(context);
        String pairRoomId = settings.getPairRoomId();
        String localUserId = settings.getLocalUserId();
        if (pairRoomId == null) {
            Utils.log("LocationRelay: no pair room — skipping push");
            return;
        }

        Location loc = DeviceLocation.getLocation(context);
        if (loc != null) {
            settings.updateFromLocation(loc);
            new LocationRelay().pushLocation(pairRoomId, localUserId,
                    loc.getLatitude(), loc.getLongitude());
        }
    }

    // ── Haversine distance (km) ──────────────────────────────────────────

    private static final double EARTH_RADIUS_KM = 6371.0;

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
