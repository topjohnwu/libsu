import com.android.tools.r8.R8
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser.ipc"
    buildFeatures {
        aidl = true
    }
}

android.libraryVariants.all {
    val variantName = name
    val variantCapped = variantName.replaceFirstChar { it.uppercaseChar() }
    val jarTask = tasks.register("create${variantCapped}MainJar") {
        doLast {
            val classDir = Paths.get(layout.buildDirectory.get().asFile.path, "intermediates",
                "javac", variantName, "compile${variantCapped}JavaWithJavac", "classes",
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

            val pgConf = layout.buildDirectory.file("mainJar.pro").get().asFile

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
    compileOnly("androidx.annotation:annotation:1.6.0")
    api(project(":core"))
}
