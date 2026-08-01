# Android Setup

## 1. Local Android configuration

Copy `local.properties.example` to `local.properties`. Keep the Android SDK path written by Android Studio and add:

```properties
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_UPLOAD_PRESET=your_unsigned_preset
GIPHY_API_KEY=your_key
GOOGLE_WEB_CLIENT_ID=your_firebase_web_oauth_client_id
```

Only client-safe identifiers belong in this file. Never place Cloudinary API secrets or Firebase Admin credentials in the Android project.

## 2. Firebase Android app

In Firebase project `nextbench-a11ed`, register an Android application with package name `com.nextbench.app`. Download its `google-services.json` and place it at:

```text
app/google-services.json
```

The example JSON documents the expected shape but cannot replace the file downloaded from Firebase. Debug builds compile without it so UI work remains unblocked; Firebase-backed features report that configuration is unavailable. Release builds require it.

Enable the same Firebase products used by the website:

- Authentication: email/password and Google
- Firestore
- Storage
- Cloud Functions
- Cloud Messaging

The Android app consumes the existing deployed backend. Do not deploy different Firestore rules or change collection schemas from this repository.

## 3. Build and test

```bash
./gradlew test assembleDebug
```

Use JDK 17. Android Studio's embedded JDK is also supported when it is version 17 or newer and compatible with the configured Android Gradle Plugin.
