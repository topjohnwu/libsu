plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser.io"
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.6.0")
    api(project(":core"))
    api(project(":nio"))
}
