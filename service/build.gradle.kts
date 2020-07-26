plugins {
    id("com.android.library")
}

android {
    defaultConfig {
        minSdkVersion(18)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api(project(":core"))
}
