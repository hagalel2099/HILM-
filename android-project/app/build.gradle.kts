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
        val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 2 + runNum
        versionName = "1.0.$runNum"


        val updateApiUrl = ((project.findProperty("NOSSO_UNIVERSO_UPDATE_URL") as? String) ?: "")
            .trim()
            .trimEnd('/')
            .replace("\"", "\\\"")
        buildConfigField("String", "UPDATE_API_BASE_URL", "\"$updateApiUrl\"")
    }

    val releaseStorePath = System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("RELEASE_STORE_FILE") as? String)?.takeIf { it.isNotBlank() }
    val releaseStoreFileObj = releaseStorePath?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseStoreFileObj != null) {
            create("release") {
                storeFile = releaseStoreFileObj
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    ?: (project.findProperty("RELEASE_STORE_PASSWORD") as? String)
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: (project.findProperty("RELEASE_KEY_ALIAS") as? String)
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFileObj != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
