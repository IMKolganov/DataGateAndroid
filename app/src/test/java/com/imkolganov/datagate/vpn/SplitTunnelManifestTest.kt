package com.imkolganov.datagate.vpn

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker can only list arbitrary networked packages if broad package visibility is declared;
 * on Android 11+ the launcher `<queries>` filter alone hides apps without a launcher entry.
 */
class SplitTunnelManifestTest {

    private val manifest: String by lazy { findMainManifest().readText() }

    @Test
    fun queryAllPackagesPermissionIsDeclared() {
        assertTrue(
            "Split tunneling needs QUERY_ALL_PACKAGES to enumerate installed apps",
            manifest.contains("android.permission.QUERY_ALL_PACKAGES"),
        )
    }

    @Test
    fun launcherQueriesFilterIsDeclaredAsFallback() {
        assertTrue(
            "Keep the <queries> launcher filter so the picker degrades to launchable apps",
            manifest.contains("<queries>") &&
                manifest.contains("android.intent.category.LAUNCHER"),
        )
    }

    private fun findMainManifest(): File {
        var dir: File? = File(checkNotNull(System.getProperty("user.dir")))
        while (dir != null) {
            val manifest = File(dir, "app/src/main/AndroidManifest.xml")
            if (manifest.isFile) return manifest
            dir = dir.parentFile
        }
        error("Could not find app/src/main/AndroidManifest.xml")
    }
}
