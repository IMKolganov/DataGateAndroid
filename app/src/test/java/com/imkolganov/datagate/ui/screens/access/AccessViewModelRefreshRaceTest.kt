package com.imkolganov.datagate.ui.screens.access

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.vpn.VpnServerSelectionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AccessViewModelRefreshRaceTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        VpnServerSelectionStore.clear(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        VpnServerSelectionStore.clear(context)
    }

    @Test
    fun construct_doesNotAutoRefresh() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        AccessViewModel(repo, context)
        assertEquals(0, repo.getServersCalls)
    }

    @Test
    fun staleSuccess_afterReset_doesNotRepublishProServersOrSelection() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        VpnServerSelectionStore.setMode(context, ServerSelectionMode.MANUAL)
        VpnServerSelectionStore.setSelectedServerId(context, 75)
        repo.serversResult = listOf(server(75, accessible = true), server(69, accessible = true))
        vm.onUserSessionReady()
        val proGate = repo.lastGetServersGate()
        assertEquals(1, repo.getServersCalls)

        // Same order as AppRoot logout: clear prefs, then invalidate in-flight refresh.
        VpnServerSelectionStore.clear(context)
        vm.resetSessionLocalState()

        proGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.servers.isEmpty())
        assertNull(vm.state.value.selectedServerId)
        assertNull(VpnServerSelectionStore.getSelectedServerId(context))
        assertEquals(ServerSelectionMode.AUTO, VpnServerSelectionStore.getMode(context))
        assertNull(vm.state.value.errorText)
    }

    @Test
    fun staleSuccess_afterNewSession_appliesOnlyFreeRefresh() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        VpnServerSelectionStore.setMode(context, ServerSelectionMode.MANUAL)
        VpnServerSelectionStore.setSelectedServerId(context, 75)
        repo.serversResult = listOf(server(75, accessible = true), server(69, accessible = true))
        vm.onUserSessionReady()
        val proGate = repo.lastGetServersGate()
        assertEquals(1, repo.getServersCalls)

        VpnServerSelectionStore.clear(context)
        vm.resetSessionLocalState()

        repo.serversResult = listOf(
            server(69, accessible = true),
            server(75, accessible = false),
        )
        // Free login with MANUAL: prefer first allowed online server (69), never blocked 75.
        VpnServerSelectionStore.setMode(context, ServerSelectionMode.MANUAL)
        vm.onUserSessionReady()
        val freeGate = repo.lastGetServersGate()
        assertEquals(2, repo.getServersCalls)

        proGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.servers.isEmpty())
        assertNull(VpnServerSelectionStore.getSelectedServerId(context))

        freeGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(69, 75), vm.state.value.servers.map { it.id })
        assertEquals(false, vm.state.value.servers.first { it.id == 75 }.isAccessibleForQuotaPlan)
        assertEquals(69, vm.state.value.selectedServerId)
        assertEquals(69, VpnServerSelectionStore.getSelectedServerId(context))
        assertNull(vm.state.value.errorText)
    }

    @Test
    fun staleError_afterSuccessfulRefresh_doesNotOverwriteUi() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        repo.failGetServers = true
        vm.onUserSessionReady()
        val failGate = repo.lastGetServersGate()
        assertEquals(1, repo.getServersCalls)

        repo.failGetServers = false
        repo.serversResult = listOf(server(69, accessible = true))
        vm.onUserSessionReady()
        val okGate = repo.lastGetServersGate()
        assertEquals(2, repo.getServersCalls)

        failGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.errorText)
        assertTrue(vm.state.value.servers.isEmpty())

        okGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.errorText)
        assertEquals(listOf(69), vm.state.value.servers.map { it.id })
        assertEquals(false, vm.state.value.isLoading)
    }

    private fun server(
        id: Int,
        accessible: Boolean,
        online: Boolean = true,
    ): AccessContract.ServerItem = AccessContract.ServerItem(
        id = id,
        name = "Server $id",
        protocol = "UDP",
        isOnline = online,
        isEnableWss = true,
        serverType = VpnServerType.OpenVpn,
        uptimeText = null,
        openVpnVersionText = null,
        totalInText = null,
        totalOutText = null,
        isAccessibleForQuotaPlan = accessible,
    )

    /**
     * Each [getServers] awaits its own gate so tests can release older refreshes after newer ones.
     */
    private class GatedAccessRepository : AccessRepository {
        var getServersCalls = 0
            private set
        var serversResult: List<AccessContract.ServerItem> = emptyList()
        var failGetServers = false

        private val getServersGates = mutableListOf<CompletableDeferred<Unit>>()

        fun lastGetServersGate(): CompletableDeferred<Unit> = getServersGates.last()

        override suspend fun getServers(): List<AccessContract.ServerItem> {
            getServersCalls++
            val gate = CompletableDeferred<Unit>()
            getServersGates += gate
            gate.await()
            if (failGetServers) throw IllegalStateException("simulated refresh failure")
            return serversResult
        }

        override suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem> =
            emptyList()

        override suspend fun loadQuotaUi(): AccessContract.QuotaUiState =
            AccessContract.QuotaUiState()
    }
}
