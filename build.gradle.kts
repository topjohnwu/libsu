import com.android.build.gradle.BaseExtension
import java.io.ByteArrayOutputStream
import java.net.URL

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("maven-publish")
    id("java")
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.1.2")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
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

        rootProject.buildDir.mkdirs()
        File(rootProject.buildDir, "package-list").outputStream().use {
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
            "https://developer.android.com/reference/", rootProject.buildDir.path))
        isNoDeprecated = true
        addBooleanOption("-ignore-source-errors").value = true
    }
    setDestinationDir(File(rootProject.buildDir, "javadoc"))
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

subprojects {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
    }

    repositories {
        google()
        mavenCentral()
    }

    configurations.create("javadocDeps")

    afterEvaluate {
        android {
            compileSdkVersion(31)
            buildToolsVersion = "31.0.0"

            defaultConfig {
                if (minSdkVersion == null)
                    minSdk = 19
                targetSdk = 31
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (plugins.hasPlugin("com.android.library")) {
            apply(plugin = "maven-publish")

            android {
                buildFeatures.apply {
                    buildConfig = false
                }

                val sources = sourceSets.getByName("main").java.getSourceFiles()

                javadoc.apply {
                    source += sources
                    classpath += project.files(bootClasspath)
                    classpath += configurations.getByName("javadocDeps")
                }

                val sourcesJar = tasks.register("sourcesJar", Jar::class) {
                    archiveClassifier.set("sources")
                    from(sources)
                }

                afterEvaluate {
                    publishing {
                        publications {
                            create<MavenPublication>("maven") {
                                from(components["release"])
                                groupId = "com.github.topjohnwu"
                                artifactId = project.name
                                artifact(sourcesJar)
                            }
                        }
                    }
                }
            }
        }
    }
}
