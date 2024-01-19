@file:Suppress("DEPRECATION")

plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.birlestirme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.birlestirme"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    packagingOptions {
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/DEPENDENCIES")
        exclude("com/example/DuplicateClass.class")
        exclude("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
        exclude("META-INF/androidx.customview_customview.version")
        exclude("META-INF/androidx.drawerlayout_drawerlayout.version")
        exclude("android/support/v4/app/INotificationSideChannel.class")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

configurations {
    all {
        exclude(group = "com.android.support", module = "support-compat")
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.google.cloud:google-cloud-translate:1.95.3")
    implementation("org.tensorflow:tensorflow-lite:2.7.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.3.0")
}