package com.sharifmia.devicechecker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import android.provider.Settings;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class DeviceCheckUtil {

    private static final String TAG = "DeviceCheckUtil";

    // --- Firestore Collections ---
    // 1. "deviceLinks" -> Maps UID to DeviceID
    //    (Document ID: uid, Field: "deviceId")
    // 2. "deviceLocks" -> Maps DeviceID to UID
    //    (Document ID: deviceId, Field: "uid")
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final CollectionReference linksRef = db.collection("deviceLinks");
    private static final CollectionReference locksRef = db.collection("deviceLocks");

    // Interface to communicate back to the Activity
    public interface DeviceCheckListener {
        void onCheckSuccess(boolean isNewDevice); // Check passed (either existing or new pairing)
        void onCheckFailed(String message);       // Check failed (mismatch)
    }

    /**
     * Gets the unique Android Device ID
     */
    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * The main check method. Runs on app start.
     */
    public static void performDeviceCheck(Activity activity, FirebaseAuth mAuth, DeviceCheckListener listener) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            listener.onCheckFailed("No user logged in.");
            return;
        }

        final String uid = user.getUid();
        final String deviceId = getDeviceId(activity.getApplicationContext());
        Log.d(TAG, "Checking... UID: " + uid + " | DeviceID: " + deviceId);

        // 1. Check if this USER (uid) is already linked to any device
        DocumentReference userLinkDoc = linksRef.document(uid);
        userLinkDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot userLinkSnapshot = task.getResult();
                if (userLinkSnapshot.exists()) {
                    // --- Case A: User exists in our system ---
                    String linkedDeviceId = userLinkSnapshot.getString("deviceId"); // Get field
                    if (deviceId.equals(linkedDeviceId)) {
                        // Correct user, correct device. Allow access.
                        Log.i(TAG, "SUCCESS: User " + uid + " matches device " + deviceId);
                        listener.onCheckSuccess(false); // false = not a new device
                    } else {
                        // User is trying to log in on a DIFFERENT device. Block access.
                        Log.w(TAG, "FAILURE: User " + uid + " is locked to device " + linkedDeviceId + ", but is trying to use " + deviceId);
                        listener.onCheckFailed("This account is locked to another device. To continue, please use your primary associated device.");
                    }
                } else {
                    // --- Case B: User is NOT in our system (first login) ---
                    // Now we must check if this DEVICE is already locked to another user
                    checkIfDeviceIsLocked(uid, deviceId, listener);
                }
            } else {
                // Firestore task failed
                Log.e(TAG, "Firestore error checking user link: ", task.getException());
                listener.onCheckFailed("Database error: " + task.getException().getMessage());
            }
        });
    }

    private static void checkIfDeviceIsLocked(String uid, String deviceId, DeviceCheckListener listener) {
        DocumentReference deviceLockDoc = locksRef.document(deviceId);
        deviceLockDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot deviceLockSnapshot = task.getResult();
                if (deviceLockSnapshot.exists()) {
                    // This device IS locked... but to whom?
                    String lockedUid = deviceLockSnapshot.getString("uid"); // Get field
                    if (lockedUid.equals(uid)) {
                        // This shouldn't happen if logic is correct, but it's a safe check.
                        Log.i(TAG, "SUCCESS: Device " + deviceId + " already locked to this user " + uid);
                        listener.onCheckSuccess(false);
                    } else {
                        // This device is locked to a DIFFERENT user. Block access.
                        Log.w(TAG, "FAILURE: Device " + deviceId + " is already locked to user " + lockedUid);
                        listener.onCheckFailed("This device is already associated with another account.");
                    }
                } else {
                    // --- Case C: New User AND New Device ---
                    // This is a fresh registration. Let's create the link atomically.
                    Log.i(TAG, "NEW DEVICE: Linking user " + uid + " to device " + deviceId);
                    linkDeviceAtomically(uid, deviceId, listener);
                }
            } else {
                // Firestore task failed
                Log.e(TAG, "Firestore error checking device lock: ", task.getException());
                listener.onCheckFailed("Database error: " + task.getException().getMessage());
            }
        });
    }

    /**
     * Creates the two-way link in Firestore using an atomic WriteBatch.
     */
    private static void linkDeviceAtomically(String uid, String deviceId, DeviceCheckListener listener) {
        // Create data for the user link
        Map<String, Object> linkData = new HashMap<>();
        linkData.put("deviceId", deviceId);

        // Create data for the device lock
        Map<String, Object> lockData = new HashMap<>();
        lockData.put("uid", uid);

        // Get document references
        DocumentReference linkDoc = linksRef.document(uid);
        DocumentReference lockDoc = locksRef.document(deviceId);

        // Get a new write batch
        WriteBatch batch = db.batch();

        // Set the value for both documents
        batch.set(linkDoc, linkData);
        batch.set(lockDoc, lockData);

        // Commit the batch
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "Atomic device link successful.");
                listener.onCheckSuccess(true); // true = is a new device
            } else {
                Log.e(TAG, "Failed to create atomic device link: ", task.getException());
                listener.onCheckFailed("Failed to register this device. Please try again.");
            }
        });
    }

    /**
     * Shows a final, non-cancelable dialog and logs the user out.
     * (No changes needed here)
     */
    public static void showFailedCheckDialog(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Security Alert")
                .setMessage(message + "\nYou will be logged out.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    // Log the user out
                    FirebaseAuth.getInstance().signOut();

                    // TODO: Replace 'LoginActivity.class' with your actual login activity
                    // This sends the user back to the login screen.
                    // Intent intent = new Intent(activity, LoginActivity.class);
                    // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    // activity.startActivity(intent);

                    // For now, just finish the app
                    activity.finishAffinity();
                })
                .show();
    }
}
