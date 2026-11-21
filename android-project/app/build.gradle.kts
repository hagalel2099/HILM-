plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nossouniverso.launcher"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.nossouniverso.launcher"
        minSdk = 23
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        val updateApiUrl = ((project.findProperty("NOSSO_UNIVERSO_UPDATE_URL") as? String) ?: "")
            .trim()
            .trimEnd('/')
            .replace("\"", "\\\"")
        buildConfigField("String", "UPDATE_API_BASE_URL", "\"$updateApiUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
