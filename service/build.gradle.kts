import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import com.android.tools.r8.R8
import java.util.stream.Collectors

plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser.ipc"
}

android.libraryVariants.all {
    val jarTask = tasks.register("create${name.capitalize()}MainJar") {
        doLast {
            val classDir = Paths.get(buildDir.path, "intermediates",
                "javac", this@all.name, "classes",
                "com", "topjohnwu", "superuser", "internal")

            val classFiles = Files.list(classDir).use { stream ->
                stream.filter {
                    it.fileName.toString().startsWith("RootServerMain")
                        || it.fileName.toString().startsWith("IRootServiceManager")
                }.collect(Collectors.toList())
            }

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

            val args = mutableListOf<Any>(
                "--release", "--output", output,
                "--pg-conf", pgConf,
                "--classpath", androidJar
            ).apply { addAll(classFiles) }
                .map { it.toString() }
                .toTypedArray()

            R8.main(args)
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
