plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.topjohnwu.libsuexample"
        minSdkVersion(16)
        versionCode = 1
        versionName ="1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":busybox"))
}
