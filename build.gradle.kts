import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL

plugins {
    id("java")
    id("maven-publish")
    id("com.android.library") version "9.1.0" apply false
}

val dlPackageList by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        // Merge framework packages with AndroidX packages into the same list
        // so links to Android classes can work properly in Javadoc

        val bos = ByteArrayOutputStream()
        URI("https://developer.android.com/reference/package-list").toURL()
                .openStream().use { src -> src.copyTo(bos) }
        URI("https://developer.android.com/reference/androidx/package-list").toURL()
                .openStream().use { src -> src.copyTo(bos) }

        // Strip out empty lines
        val packageList = bos.toString("UTF-8").replace("\n+".toRegex(), "\n")

        rootProject.layout.buildDirectory.asFile.get().mkdirs()
        rootProject.layout.buildDirectory.file("package-list").get().asFile.outputStream().use {
            it.writer().write(packageList)
            it.write("\n".toByteArray())
        }
    }
}

val javadoc = (tasks["javadoc"] as Javadoc).apply {
    dependsOn(dlPackageList)
    isFailOnError = false
    title = "libsu API"
    exclude("**/internal/**")
    (options as StandardJavadocDocletOptions).apply {
        linksOffline("https://developer.android.com/reference/",
            rootProject.layout.buildDirectory.get().asFile.path)
        isNoDeprecated = true
        addBooleanOption("-ignore-source-errors").value = true
    }
    destinationDir = rootProject.layout.buildDirectory.dir("javadoc").get().asFile
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(javadoc)
    archiveClassifier.set("javadoc")
    from(javadoc.destinationDir)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(javadocJar.get())
            groupId = "com.github.topjohnwu"
            artifactId = "docs"
        }
    }
}

private fun Project.android(configure: Action<CommonExtension>) =
    extensions.configure("android", configure)

private fun Project.androidLibrary(configure: Action<LibraryExtension>) =
    extensions.configure("android", configure)

private fun Project.androidComponents(configure: Action<AndroidComponentsExtension<*, *, *>>) =
    extensions.configure(AndroidComponentsExtension::class.java, configure)

allprojects {
    if (this == rootProject)
        return@allprojects

    configurations.create("javadocDeps")
    afterEvaluate {
        android {
            compileSdk {
                version = release(36) {
                    minorApiLevel = 1
                }
            }
            buildToolsVersion = "36.1.0"

            defaultConfig.apply {
                if (minSdk == null)
                    minSdk = 19
            }

            compileOptions.apply {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (plugins.hasPlugin("com.android.library")) {
            apply(plugin = "maven-publish")

            androidComponents {
                onVariants {
                    javadoc.apply {
                        classpath += project.files(sdkComponents.bootClasspath)
                    }
                }
            }

            androidLibrary {
                buildFeatures {
                    buildConfig = false
                }

                val sources = project.files(
                    *sourceSets.getByName("main").java.directories.toTypedArray()).asFileTree
                javadoc.apply {
                    source += sources
                    classpath += configurations.getByName("javadocDeps")
                }

                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }

            publishing {
                publications {
                    register<MavenPublication>("libsu") {
                        afterEvaluate {
                            from(components["release"])
                        }
                        groupId = "com.github.topjohnwu.libsu"
                        artifactId = project.name
                    }
                }
            }
        }
    }
}
