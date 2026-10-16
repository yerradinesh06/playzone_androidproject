# PS 22 Cloud Deployment & Setup Guide

This directory contains the production server scripts and security configurations required to connect live cloud services to the PS 22 Android application.

## 1. Google Sheets & Apps Script Backend Setup

1. Open [Google Sheets](https://sheets.google.com) and create a new Spreadsheet.
2. Create 5 sheets with the exact names:
   - `Teams`
   - `Players`
   - `Fixtures`
   - `Results`
   - `Standings`
3. Click **Extensions > Apps Script**.
4. Replace the default code with the contents of `cloud_backend/apps_script/Code.gs`.
5. Click **Deploy > New Deployment**.
   - Select **Web app**.
   - Execute as: **Me**.
   - Who has access: **Anyone**.
6. Copy the Web App URL (this is your `appsScriptUrl`).
7. Get the Spreadsheet ID from the URL bar (`https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit`).

---

## 2. Firebase Authentication & Custom Claims Setup

1. Open the [Firebase Console](https://console.firebase.google.com) and create a project.
2. Add an Android app with the package name `com.ps22.tournament`.
3. Enable **Email/Password** in **Authentication > Sign-in method**.
4. Download `google-services.json` or copy the API key, App ID, Project ID, and Sender ID from Project Settings.
5. Create user accounts in Firebase Authentication for your Organisers and Team Managers.
6. Run `cloud_backend/firebase/set_custom_claims.js` to issue custom claims:
   ```bash
   npm install firebase-admin
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
   
   # Set Organiser Role
   node set_custom_claims.js "<ORGANISER_UID>" "ORGANISER"
   
   # Set Team Manager Role
   node set_custom_claims.js "<MANAGER_UID>" "TEAM_MANAGER" "TEAM-HBR"
   ```

---

## 3. Local Properties Configuration

Copy `local.properties.example` to `local.properties` in the project root and fill in your deployed URLs and keys:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk

# Google Sheets & Apps Script
spreadsheetId=YOUR_SPREADSHEET_ID
publicCsvUrl=https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/pub?output=csv
appsScriptUrl=https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec
googleWebClientId=YOUR_OAUTH_CLIENT_ID.apps.googleusercontent.com

# Firebase Authentication
firebaseApiKey=YOUR_FIREBASE_API_KEY
firebaseAppId=YOUR_FIREBASE_APP_ID
firebaseProjectId=YOUR_FIREBASE_PROJECT_ID
firebaseSenderId=YOUR_FIREBASE_SENDER_ID
```

When these keys are present in `local.properties`, rebuild the app with `./gradlew.bat :app:assembleRelease` to switch from Local Demo mode to Live Cloud operations.
