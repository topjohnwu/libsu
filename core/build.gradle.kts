plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser"
    defaultConfig {
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("androidx.annotation:annotation:1.3.0")
    javadocDeps("androidx.annotation:annotation:1.3.0")
}
