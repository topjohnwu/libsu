import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import com.android.tools.r8.R8

plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    defaultConfig {
        minSdk = 18
    }
}

android.libraryVariants.all {
    val jarTask = tasks.register("create${name.capitalize()}MainJar") {
        doLast {
            val classFile = Paths.get(buildDir.path, "intermediates",
                "javac", this@all.name, "classes",
                "com", "topjohnwu", "superuser", "internal", "RootServerMain.class")

            val androidJar = Paths.get(android.sdkDirectory.path, "platforms",
                    android.compileSdkVersion, "android.jar")

            val output = Paths.get(
                android.sourceSets.getByName("main").assets.srcDirs.first().path, "main.jar")

            if (Files.notExists(output.parent))
                Files.createDirectories(output.parent)

            val pgConf = File(buildDir, "mainJar.pro")

            PrintStream(pgConf.outputStream()).use {
                it.println("-keep class com.topjohnwu.superuser.internal.RootServerMain")
                it.println("{ public static void main(java.lang.String[]); }")
            }

            val args = listOf<Any>(
                "--release", "--output", output,
                "--pg-conf", pgConf,
                "--classpath", androidJar,
                classFile
            )

            R8.main(args.map { it.toString() }.toTypedArray())
        }
    }
    javaCompileProvider {
        finalizedBy(jarTask)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api(project(":core"))
}
