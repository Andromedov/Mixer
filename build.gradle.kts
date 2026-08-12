plugins {
    id("java-library")
    id("maven-publish")

    id("com.gradleup.shadow") version "9.2.2"
}

val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion")
    .map(String::toInt)
    .orElse(21)
val javaReleaseVersion = providers.gradleProperty("javaReleaseVersion")
    .map(String::toInt)
    .orElse(21)

tasks["jar"].enabled = false

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "me.andromedov"
    version = "2.4.0-SNAPSHOT"

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(javaReleaseVersion)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion.get()))
    }
}

subprojects {
    publishing {
        publications.create<MavenPublication>("maven${project.name}") {
            artifactId = "${rootProject.name}-${project.name}".lowercase()
            from(components["java"])
        }
        repositories {
            mavenLocal()
        }
    }
}
