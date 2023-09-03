plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser"
    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.6.0")
    javadocDeps("androidx.annotation:annotation:1.6.0")
 }
