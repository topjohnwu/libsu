plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api(project(":core"))
}
