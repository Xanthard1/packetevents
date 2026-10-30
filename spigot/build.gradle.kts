import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.jpenilla.runpaper.RunPaperExtension
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    packetevents.`shadow-conventions`
    packetevents.`library-conventions`
    packetevents.`publish-conventions`
    xyz.jpenilla.`run-paper`
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.viaversion.com/everything/")
}

val bundledGson: Configuration by configurations.creating

dependencies {
    bundledGson(libs.gson)
    compileOnly(libs.netty)
    shadow(libs.bundles.adventure)
    compileShadowOnly(libs.bstats.bukkit)
    shadow(project(":api", "shadow"))
    shadow(project(":netty-common"))

    compileOnly(libs.paper)
    compileOnly(libs.via.version)
}

// we compile against latest paper, which requires a newer java version than we target
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    // 1.7 servers ship netty as net.minecraft.util.io.netty; ServerRuntime picks these copies there.
    val legacyNetty by registering(ShadowJar::class) {
        group = rootProject.name
        archiveFileName = "legacy-netty.jar"
        destinationDirectory = layout.buildDirectory.dir("legacy")
        configurations = emptyList()
        from(sourceSets.main.get().output) {
            include("io/github/retrooper/packetevents/injector/**", "io/github/retrooper/packetevents/netty/**")
        }
        relocate("io.netty", "net.minecraft.util.io.netty")
        relocate("io.github.retrooper.packetevents.injector", "io.github.retrooper.packetevents.legacy.injector")
        relocate("io.github.retrooper.packetevents.netty", "io.github.retrooper.packetevents.legacy.netty")
    }

    shadowJar {
        // Paper doesn't need to map spigot -> mojang since we support both
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        from(legacyNetty.flatMap { it.archiveFile }.map { zipTree(it) })
        from(bundledGson.elements.map { files -> files.map { zipTree(it) } }) {
            exclude("META-INF/**", "module-info.class")
        }
        relocate("com.google.gson", "io.github.retrooper.packetevents.libs.gson")
    }

    // 1.8.8 - 1.16.5   = Java 8
    // 1.17             = Java 16
    // 1.18 - 1.20.4    = Java 17
    // 1.20.5 - 1.21.11 = Java 21
    // 26.1+            = Java 25
    val version = "26.3"
    val javaVersion = JavaLanguageVersion.of(25)

    val jvmArgsExternal = listOf(
        "-Dcom.mojang.eula.agree=true"
    )

    withType<Javadoc> {
        // javadoc generation tries to load some random nms classes and fails
        exclude("io/github/retrooper/packetevents/util/protocolsupport/**")
    }

    named<RunServer>("runServer") {
        minecraftVersion(version)
        runDirectory = rootDir.resolve("run/paper/$version")

        javaLauncher = project.javaToolchains.launcherFor {
            languageVersion = javaVersion
        }

        jvmArgs = jvmArgsExternal
    }

    configure<RunPaperExtension> {
        folia.registerTask {
            minecraftVersion(version)
            runDirectory = rootDir.resolve("run/folia/$version")

            javaLauncher = project.javaToolchains.launcherFor {
                languageVersion = javaVersion
            }

            jvmArgs = jvmArgsExternal
        }
    }
}
