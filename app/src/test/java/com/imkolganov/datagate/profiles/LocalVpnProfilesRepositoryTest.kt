package com.imkolganov.datagate.profiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.imkolganov.datagate.model.servers.VpnServerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LocalVpnProfilesRepositoryTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repo = LocalVpnProfilesRepository(app)

    @Test
    fun importOpenVpnContent_persistsFileAndIndex() = runBlocking {
        val profile = repo.importOpenVpnContent(
            content = "client\nremote vpn.example.com 1194\nproto udp\n",
            displayName = "work.ovpn",
            username = "alice",
            password = "secret",
        )

        assertEquals("work", profile.name)
        assertEquals(VpnServerType.OpenVpn, profile.type)
        assertTrue(profile.hasUsername)
        assertTrue(profile.hasPassword)
        assertTrue(File(app.filesDir, "profiles/${profile.configFileName}").isFile)

        val listed = repo.list()
        assertTrue(listed.any { it.id == profile.id })

        val creds = repo.getCredentials(profile.id)
        assertEquals("alice", creds.username)
        assertEquals("secret", creds.password)

        val text = repo.readConfigText(profile)
        assertTrue(text.contains("remote vpn.example.com 1194"))
    }

    @Test
    fun delete_removesFileCredsAndIndex() = runBlocking {
        val profile = repo.importOpenVpnContent(
            content = "remote example.com 443\n",
            displayName = "temp",
            username = "u",
            password = "p",
        )
        val path = File(app.filesDir, "profiles/${profile.configFileName}")
        assertTrue(path.exists())

        repo.delete(profile.id)

        assertFalse(path.exists())
        assertTrue(repo.list().none { it.id == profile.id })
        val creds = repo.getCredentials(profile.id)
        assertEquals("", creds.username)
        assertEquals("", creds.password)
    }

    @Test
    fun importXrayContent_jsonOutbounds_persists() = runBlocking {
        val json = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}"""
        val profile = repo.importXrayContent(
            content = json,
            displayName = "my-node",
            normalize = { it },
        )
        assertEquals(VpnServerType.Xray, profile.type)
        assertEquals("my-node", profile.name)
        assertTrue(profile.configFileName.endsWith(".json"))
        assertTrue(File(app.filesDir, "profiles/${profile.configFileName}").isFile)
        assertTrue(repo.readConfigText(profile).contains("\"outbounds\""))
        assertTrue(repo.profiles.first().any { it.id == profile.id })
    }
}
