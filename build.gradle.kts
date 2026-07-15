import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("net.fabricmc.fabric-loom") version "1.17.1" apply false
}

val targets = mapOf(
    "mc26_1_2" to Target("26.1.2", "0.152.1+26.1.2", "modern-26.1", "18.0.0"),
    "mc26_2" to Target("26.2", "0.154.2+26.2", "modern-26.2", "20.0.1"),
)

allprojects {
    group = "org.hypixelskyblockmods.skyhud"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.notenoughupdates.org/releases/")
        maven("https://api.modrinth.com/maven")
    }
}

subprojects {
    val target = targets[name] ?: return@subprojects
    val artifactVersion = "${rootProject.version}+mc${target.minecraft}"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "net.fabricmc.fabric-loom")

    version = artifactVersion

    dependencies {
        add("minecraft", "com.mojang:minecraft:${target.minecraft}")
        add("implementation", "net.fabricmc:fabric-loader:0.19.3")
        add("implementation", "net.fabricmc.fabric-api:fabric-api:${target.fabricApi}")
        add("implementation", "net.fabricmc:fabric-language-kotlin:1.13.12+kotlin.2.4.0")
        add("implementation", "org.notenoughupdates.moulconfig:${target.moulConfig}:4.7.2")
        add("include", "org.notenoughupdates.moulconfig:${target.moulConfig}:4.7.2")
        add("compileOnly", "maven.modrinth:modmenu:${target.modMenu}")
    }

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
        clientOnlyMinecraftJar()
        runs {
            named("client") {
                ideConfigGenerated(true)
                runDir("run/${target.minecraft}")
            }
        }
    }

    extensions.configure<org.gradle.api.tasks.SourceSetContainer> {
        named("main") {
            java.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(listOf(rootProject.file("src/main/resources")))
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
        sourceSets.named("main") {
            kotlin.srcDir(rootProject.file("src/main/kotlin"))
            kotlin.srcDir(rootProject.file("src/${target.minecraft}/kotlin"))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    }

    tasks.named<ProcessResources>("processResources") {
        inputs.property("version", artifactVersion)
        inputs.property("minecraft_version", target.minecraft)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to artifactVersion,
                "minecraft_version" to target.minecraft,
            )
        }
    }

    tasks.named<Jar>("jar") {
        archiveBaseName.set("SkyHUD")
        archiveVersion.set(project.version.toString())
    }
}

data class Target(
    val minecraft: String,
    val fabricApi: String,
    val moulConfig: String,
    val modMenu: String,
)
