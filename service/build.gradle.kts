plugins {
    id("com.android.library")
}

group="com.github.topjohnwu.libsu"

android {
    namespace = "com.topjohnwu.superuser.ipc"
}

abstract class CopyJarBuild: Copy() {
    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val variantCapped = variantName.replaceFirstChar { it.uppercase() }

        val buildJar = tasks.getByPath(":service:jar:build${variantCapped}Jar")
        val copyJar = tasks.register<CopyJarBuild>("copy${variantCapped}MainJar") {
            outputFolder.set(layout.buildDirectory.dir("$variantName/assets"))
            from(buildJar)
            into(outputFolder)
        }

        variant.sources.assets?.let {
            it.addGeneratedSourceDirectory(copyJar, CopyJarBuild::outputFolder)
        }
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.6.0")
    api(project(":core"))
    implementation(project(":service:shared"))
}
