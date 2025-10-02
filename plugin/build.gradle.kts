plugins {
    id("io.github.goooler.shadow") version "8.1.8"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("com.modrinth.minotaur") version "2.+"
}

repositories {
    mavenCentral()
    maven ("https://maven.maxhenkel.de/repository/public")
    maven ("https://repo.codemc.io/repository/maven-public/")
    maven ("https://jitpack.io")
    maven ("https://maven.lavalink.dev/releases")
    maven {
        name = "arbjergDevSnapshots"
        url = uri("https://maven.lavalink.dev/snapshots")
    }
    maven {
        name = "TarsosDSP repository"
        url = uri("https://mvn.0110.be/releases")
    }
    maven {
        name = "reposiliteRepositoryReleases"
        url = uri("https://maven.maxhenkel.de/repository/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    implementation("de.maxhenkel.voicechat:voicechat-api:2.6.0")
    implementation("dev.arbjerg:lavaplayer:2.2.4")
    implementation("dev.lavalink.youtube:v2:1.14.0")

    implementation("dev.jorel:commandapi-bukkit-shade:10.1.2")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("be.tarsos.dsp:core:2.5")
    implementation("be.tarsos.dsp:jvm:2.5")
    implementation("de.maxhenkel.opus4j:opus4j:2.0.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    api(project(":api"))
}

tasks {
    shadowJar {
        destinationDirectory.set(rootProject.buildDir.resolve("libs"))
        archiveBaseName.set(rootProject.name)

        relocate("dev.jorel.commandapi", "net.somewhatcity.mixer.commandapi")
        relocate("de.tr7zw.changeme.nbtapi", "net.somewhatcity.mixer.item-nbt-api")
        dependencies {
            exclude(dependency("de.maxhenkel.voicechat:voicechat-api:2.6.0"))
        }
    }

    assemble {
        dependsOn(shadowJar)
    }
}

bukkit {
    main = "$group.mixer.core.MixerPlugin"
    apiVersion = "1.21.9"
    authors = listOf("mrmrmystery", "Andromedov")
    name = rootProject.name
    depend = listOf("voicechat")
    version = rootProject.version.toString()
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("ThaMLsde")
    versionNumber.set(rootProject.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(listOf("1.21.8"))
    loaders.addAll(listOf("paper", "purpur"))
    dependencies {
        required.project("9eGKb6K1")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}