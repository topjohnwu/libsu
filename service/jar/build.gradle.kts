import com.android.build.api.artifact.SingleArtifact
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
}

android {
    namespace = "com.topjohnwu.superuser.main"

    defaultConfig {
        applicationId = "com.topjohnwu.superuser.main"
        versionCode = 1
        versionName ="1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
dependencies {
    implementation(project(":service:shared"))
}

abstract class MakeJarTask: DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:OutputFile
    abstract val outJar: RegularFileProperty

    @TaskAction
    fun taskAction() {
        val apk = apkFolder.asFileTree.filter { it.extension == "apk" }.singleFile
        val zf = ZipFile(apk)
        ZipOutputStream(outJar.get().asFile.outputStream().buffered()).use { out ->
            zf.entries().asSequence().filter { it.name.endsWith(".dex") }.forEach { dex ->
                out.putNextEntry(dex)
                zf.getInputStream(dex).use { it.copyTo(out) }
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val variantCapped = variantName.replaceFirstChar { it.uppercase() }

        val apk = variant.artifacts.get(SingleArtifact.APK)
        tasks.register<MakeJarTask>("build${variantCapped}Jar") {
            apkFolder.set(apk)
            outJar.set(project.layout.buildDirectory.file("$variantName/jar/main.jar"))
        }
    }
}

