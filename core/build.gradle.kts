plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("androidx.annotation:annotation:1.3.0")
    javadocDeps("androidx.annotation:annotation:1.3.0")
}
