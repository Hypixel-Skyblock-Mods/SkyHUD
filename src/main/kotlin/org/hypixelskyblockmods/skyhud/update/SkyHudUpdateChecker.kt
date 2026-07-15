package org.hypixelskyblockmods.skyhud.update

import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.Version
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object SkyHudUpdateChecker {
    private const val RELEASE_URL = "https://api.github.com/repos/Hypixel-Skyblock-Mods/SkyHUD/releases/latest"
    private const val CACHE_MILLIS = 15 * 60 * 1000L
    private val logger = LoggerFactory.getLogger("SkyHUD Update Checker")
    private val lock = Any()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val installedVersion = FabricLoader.getInstance()
        .getModContainer("skyhud")
        .orElseThrow()
        .metadata
        .version
        .friendlyString
        .substringBefore('+')

    @Volatile
    private var lastCompletedAt = 0L

    @Volatile
    var snapshot = Snapshot(installedVersion, State.NOT_CHECKED)
        private set

    fun refresh(force: Boolean = false) {
        synchronized(lock) {
            if (snapshot.state == State.CHECKING) return
            if (!force && lastCompletedAt != 0L && System.currentTimeMillis() - lastCompletedAt < CACHE_MILLIS) return
            snapshot = Snapshot(installedVersion, State.CHECKING)
        }

        CompletableFuture.runAsync {
            val result = try {
                fetchLatestVersion()
            } catch (exception: Exception) {
                logger.warn("Could not check the latest SkyHUD release", exception)
                Snapshot(installedVersion, State.FAILED)
            }
            synchronized(lock) {
                snapshot = result
                lastCompletedAt = System.currentTimeMillis()
            }
        }
    }

    private fun fetchLatestVersion(): Snapshot {
        val request = HttpRequest.newBuilder(URI.create(RELEASE_URL))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SkyHUD/$installedVersion")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "GitHub returned HTTP ${response.statusCode()}" }

        val latestVersion = JsonParser.parseString(response.body())
            .asJsonObject
            .get("tag_name")
            ?.asString
            ?.removePrefix("v")
            ?.takeIf(String::isNotBlank)
            ?: error("GitHub's latest release did not include tag_name")
        val current: Version = SemanticVersion.parse(installedVersion)
        val latest: Version = SemanticVersion.parse(latestVersion)
        val state = if (current.compareTo(latest) >= 0) State.UP_TO_DATE else State.UPDATE_AVAILABLE
        return Snapshot(installedVersion, state, latestVersion)
    }

    data class Snapshot(
        val currentVersion: String,
        val state: State,
        val latestVersion: String? = null,
    ) {
        val message: String
            get() = when (state) {
                State.NOT_CHECKED -> "Update status not checked"
                State.CHECKING -> "Checking for updates..."
                State.UP_TO_DATE -> "Up to date"
                State.UPDATE_AVAILABLE -> "Update available: $latestVersion"
                State.FAILED -> "Could not check for updates"
            }

        val color: Int
            get() = when (state) {
                State.NOT_CHECKED, State.FAILED -> 0xA0A0A0
                State.CHECKING -> 0xFFD966
                State.UP_TO_DATE -> 0x55FF55
                State.UPDATE_AVAILABLE -> 0xFF5555
            }
    }

    enum class State {
        NOT_CHECKED,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        FAILED,
    }
}
