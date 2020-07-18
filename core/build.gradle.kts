plugins {
    id("com.android.library")
    id("com.github.dcendents.android-maven")
}

group="com.github.topjohnwu.libsu"

android {
    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("androidx.annotation:annotation:1.1.0")
    javadocDeps("androidx.annotation:annotation:1.1.0")
}
