plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser.nio"
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("androidx.annotation:annotation:1.3.0")
}
