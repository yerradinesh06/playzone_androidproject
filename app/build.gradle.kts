import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val ps22Local = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use(::load)
}
fun ps22Config(key: String, fallback: String = ""): String {
    val valFromProp = ps22Local.getProperty(key)?.trim().orEmpty()
    return if (valFromProp.isNotBlank()) valFromProp else fallback
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.ps22.tournament"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.ps22.tournament"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        val defaultSpreadsheetId = ps22Config("spreadsheetId", "1PS22-Tournament-Live-Sheet-ID")
        val defaultPublicCsvUrl = ps22Config("publicCsvUrl", "https://docs.google.com/spreadsheets/d/1PS22-Tournament-Live-Sheet-ID/pub?output=csv")
        val defaultAppsScriptUrl = ps22Config("appsScriptUrl", "https://script.google.com/macros/s/AKfycbxPS22TournamentManager/exec")
        val defaultWebClientId = ps22Config("googleWebClientId", "487952330617-qcp1kenpv91p68k9p3jr6ustdjmduug2.apps.googleusercontent.com")
        val defaultFirebaseApiKey = ps22Config("firebaseApiKey", "AIzaSyDOIjB36aij5oi55scuAAiR8SjXxckEE6U")
        val defaultFirebaseAppId = ps22Config("firebaseAppId", "1:487952330617:android:ae67a6af0a49cabdacc9a8")
        val defaultFirebaseProjectId = ps22Config("firebaseProjectId", "project-f5b11703-a8f9-47cc-a28")
        val defaultFirebaseSenderId = ps22Config("firebaseSenderId", "487952330617")

        buildConfigField("boolean", "DEMO_MODE", "true")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$defaultWebClientId\"")
        buildConfigField("String", "SPREADSHEET_ID", "\"$defaultSpreadsheetId\"")
        buildConfigField("String", "PUBLIC_CSV_URL", "\"$defaultPublicCsvUrl\"")
        buildConfigField("String", "APPS_SCRIPT_URL", "\"$defaultAppsScriptUrl\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$defaultFirebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$defaultFirebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$defaultFirebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$defaultFirebaseSenderId\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", "true")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DEMO_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DEMO_MODE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.room:room-testing:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
