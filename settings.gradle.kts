pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "SkyHUD"

include("versions:mc26_1_2")
include("versions:mc26_2")
