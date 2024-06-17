import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import java.io.ByteArrayOutputStream
import java.net.URL

plugins {
    id("java")
    id("maven-publish")
    id("com.android.library") version "8.5.0" apply false
}

val dlPackageList by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        // Merge framework packages with AndroidX packages into the same list
        // so links to Android classes can work properly in Javadoc

        val bos = ByteArrayOutputStream()
        URL("https://developer.android.com/reference/package-list")
                .openStream().use { src -> src.copyTo(bos) }
        URL("https://developer.android.com/reference/androidx/package-list")
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
        linksOffline = listOf(JavadocOfflineLink(
            "https://developer.android.com/reference/",
            rootProject.layout.buildDirectory.asFile.get().path))
        isNoDeprecated = true
        addBooleanOption("-ignore-source-errors").value = true
    }
    setDestinationDir(rootProject.layout.buildDirectory.dir("javadoc").get().asFile)
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

fun Project.android(configuration: BaseExtension.() -> Unit) =
        extensions.getByName<BaseExtension>("android").configuration()

fun Project.androidLibrary(configuration: LibraryExtension.() -> Unit) =
        extensions.getByName<LibraryExtension>("android").configuration()

subprojects {
    configurations.create("javadocDeps")
    afterEvaluate {
        android {
            compileSdkVersion(34)
            buildToolsVersion = "34.0.0"

            defaultConfig {
                if (minSdkVersion == null)
                    minSdk = 19
                targetSdk = 34
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (plugins.hasPlugin("com.android.library")) {
            apply(plugin = "maven-publish")

            androidLibrary {
                buildFeatures {
                    buildConfig = false
                }

                val sources = sourceSets.getByName("main").java.getSourceFiles()

                javadoc.apply {
                    source += sources
                    classpath += project.files(bootClasspath)
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
