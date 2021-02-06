import org.gradle.internal.os.OperatingSystem
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    id("com.android.library")
    id("com.github.dcendents.android-maven")
}

group="com.github.topjohnwu.libsu"

android {
    defaultConfig {
        minSdkVersion(18)
    }
}

android.libraryVariants.all {
    val jarTask = tasks.register("create${name.capitalize()}MainJar") {
        doLast {
            val d8Command = if (OperatingSystem.current().isWindows) "d8.bat" else "d8"
            val d8 = Paths.get(android.sdkDirectory.path,
                "build-tools", android.buildToolsVersion, d8Command)
            val classFile = Paths.get(buildDir.path, "intermediates",
                "javac", this@all.name, "classes",
                "com", "topjohnwu", "superuser", "internal", "IPCMain.class")
            val output = Paths.get(
                android.sourceSets.getByName("main").assets.srcDirs.first().path,
                "main.jar")

            if (Files.notExists(output.parent))
                Files.createDirectories(output.parent)

            val dummy = object : OutputStream() {
                override fun write(b: Int) {}
                override fun write(bytes: ByteArray, off: Int, len: Int) {}
            }

            exec {
                commandLine(d8, "--release", "--output", output, classFile)
                standardOutput = dummy
                errorOutput = dummy
            }
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
