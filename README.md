[![](https://jitpack.io/v/SharifMia/DeviceCheckUtil.svg)](https://jitpack.io/#SharifMia/DeviceCheckUtil)
 
DeviceCheckUtil is a lightweight, secure Android library that enforces strict 1-to-1 hardware locking for your Firebase apps.

It seamlessly checks if a user's account is already linked to a specific physical device, preventing users from logging into the same account across multiple devices or sharing accounts. It uses Firebase Auth and Firestore with atomic batch writes to ensure secure, two-way verification.

🚀 Features:-

Automatic Device ID Generation: Securely fetches the unique Android Hardware ID.

Two-Way Verification: Ensures the user matches the device, and the device matches the user.

Atomic Link Creation: Safely binds new devices using Firestore WriteBatch.

Built-in Security Dialog: Automatically handles mismatch scenarios by showing an un-cancelable alert and logging the user out.

    
🛠 Prerequisites

Since this library relies on Firebase, your app must have the following already set up:

    Firebase Authentication

    Firebase Firestore Your Firestore database will automatically populate with two collections when the library runs:

    deviceLinks (Maps User UID -> Device ID)

    deviceLocks (Maps Device ID -> User UID)

 📦 Installation

Step 1: Add the JitPack repository to your settings.gradle (or project-level build.gradle for older projects): 

        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
                maven { url 'https://jitpack.io' } // <-- Add this line
            }
        }
Step 2: Add the dependency to your app-level build.gradle file:

    dependencies {
        implementation 'com.github.SharifMia:DeviceCheckUtil:1.0.0'
    }

💻 How to Use

Call the utility right after your user successfully logs in, or on your app's main startup screen (like MainActivity).

Pass in your Activity context, your FirebaseAuth instance, and the listener to handle the result:

    import com.sharifmia.devicechecker.DeviceCheckUtil;
    import com.google.firebase.auth.FirebaseAuth;
    
    // ... inside your Activity ...
    
    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    
    DeviceCheckUtil.performDeviceCheck(this, mAuth, new DeviceCheckUtil.DeviceCheckListener() {
        @Override
        public void onCheckSuccess(boolean isNewDevice) {
            // The check passed! The user is allowed to proceed.
            Log.d("App", "Device check successful.");

        if (isNewDevice) {
            Toast.makeText(MainActivity.this, "Device registered successfully!", Toast.LENGTH_SHORT).show();
        }

        // TODO: Proceed with loading your user data
        // userAccount();
        // checkDailyIncome();
    }

    @Override
    public void onCheckFailed(String message) {
        // Mismatch detected! The user is trying to use an unauthorized device.
        Log.e("App", "Device check failed: " + message);
        
        // This will show an un-cancelable dialog and log the user out
        DeviceCheckUtil.showFailedCheckDialog(MainActivity.this, message);
    }
      });


🔒 Recommended Firestore Rules

To keep your hardware locking secure, it is highly recommended to restrict read/write access to these collections in your Firebase Console. Users should only be able to read or write their own documents.

    rules_version = '2';
    service cloud.firestore {
      match /databases/{database}/documents {
        match /deviceLinks/{uid} {
          allow read, write: if request.auth != null && request.auth.uid == uid;
        }
        match /deviceLocks/{deviceId} {
          allow read, write: if request.auth != null; 
        }
      }
    }


📝 License

Distributed under the MIT License. Feel free to use, modify, and distribute this library in your own projects!
