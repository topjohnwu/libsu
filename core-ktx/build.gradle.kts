plugins {
    id("com.android.library")
    kotlin("android") version "1.6.10"
}

group="com.github.topjohnwu.libsu"

android {
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    api(project(":core"))
}
