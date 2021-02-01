plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.topjohnwu.libsuexample"
        minSdkVersion(18)
        versionCode = 1
        versionName ="1.0"
    }

    buildFeatures {
        viewBinding = true
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
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":busybox"))
    implementation(project(":service"))
}
