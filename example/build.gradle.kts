plugins {
    id("com.android.application")
}

android {
    namespace = "com.topjohnwu.libsuexample"

    defaultConfig {
        minSdk = 21
        applicationId = "com.topjohnwu.libsuexample"
        versionCode = 1
        versionName ="1.0"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.6.0")
    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":io"))
    implementation(project(":nio"))
}
