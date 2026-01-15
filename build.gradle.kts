plugins {
    id("java-library")
    id("maven-publish")

    id("com.gradleup.shadow") version "9.2.2"
}

tasks["jar"].enabled = false

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "net.somewhatcity"
    version = "2.2.0"

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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
