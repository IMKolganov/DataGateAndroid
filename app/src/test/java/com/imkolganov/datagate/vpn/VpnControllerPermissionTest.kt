package com.imkolganov.datagate.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, shadows = [ShadowVpnService::class])
class VpnControllerPermissionTest {

    private val noopLauncher = object : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()
    }

    @Test
    fun onStart_updatesPermissionState_whenGranted() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = false)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onStart()

        assertTrue(state.hasVpnPermission)
    }

    @Test
    fun onStart_updatesPermissionState_whenDenied() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = true)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onStart()

        assertFalse(state.hasVpnPermission)
    }

    @Test
    fun onPermissionGranted_updatesPermissionState() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = false)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onPermissionGranted()

        assertTrue(state.hasVpnPermission)
    }

    @Test
    fun onPermissionDenied_updatesPermissionState() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = true)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onPermissionDenied()

        assertFalse(state.hasVpnPermission)
    }
}
