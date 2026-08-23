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
import org.junit.Assert.assertFalse
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
        assertNull(vm.state.value.serversErrorText)
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
        assertNull(vm.state.value.serversErrorText)
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
        assertNull(vm.state.value.serversErrorText)
        assertTrue(vm.state.value.servers.isEmpty())

        okGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.serversErrorText)
        assertEquals(listOf(69), vm.state.value.servers.map { it.id })
        assertFalse(vm.state.value.isServersLoading)
    }

    @Test
    fun serversFinish_whileQuotaStillLoading_areIndependent() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        repo.serversResult = listOf(server(69, accessible = true))
        repo.gateQuota = true
        vm.onUserSessionReady()

        val serversGate = repo.lastGetServersGate()
        val quotaGate = repo.lastQuotaGate()

        serversGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isServersLoading)
        assertEquals(listOf(69), vm.state.value.servers.map { it.id })
        assertTrue(vm.state.value.isQuotaLoading)

        quotaGate.complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isQuotaLoading)
        assertFalse(vm.state.value.isTrafficLoading)
        assertEquals("Test Plan", vm.state.value.quota.currentPlanName)
    }

    @Test
    fun refresh_whileServersActive_doesNotRestartServersJob() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        repo.serversResult = listOf(server(69, accessible = true))
        vm.onUserSessionReady()
        assertEquals(1, repo.getServersCalls)

        vm.onEvent(AccessContract.UiEvent.Refresh)
        assertEquals(1, repo.getServersCalls)

        repo.lastGetServersGate().complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isServersLoading)
        assertEquals(listOf(69), vm.state.value.servers.map { it.id })
    }

    @Test
    fun onUserSessionReady_whileActive_restartsServersJob() = runTest(mainDispatcher) {
        val repo = GatedAccessRepository()
        val vm = AccessViewModel(repo, context)

        repo.serversResult = listOf(server(69, accessible = true))
        vm.onUserSessionReady()
        assertEquals(1, repo.getServersCalls)

        vm.onUserSessionReady()
        assertEquals(2, repo.getServersCalls)

        repo.lastGetServersGate().complete(Unit)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(69), vm.state.value.servers.map { it.id })
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
     * Each [getServers] / [loadQuotaPlanUi] awaits its own gate so tests can release older
     * refreshes after newer ones.
     */
    private class GatedAccessRepository : AccessRepository {
        var getServersCalls = 0
            private set
        var serversResult: List<AccessContract.ServerItem> = emptyList()
        var failGetServers = false
        var gateQuota = false

        private val getServersGates = mutableListOf<CompletableDeferred<Unit>>()
        private val quotaGates = mutableListOf<CompletableDeferred<Unit>>()

        fun lastGetServersGate(): CompletableDeferred<Unit> = getServersGates.last()
        fun lastQuotaGate(): CompletableDeferred<Unit> = quotaGates.last()

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

        override suspend fun loadQuotaPlanUi(): AccessContract.QuotaUiState {
            if (gateQuota) {
                val gate = CompletableDeferred<Unit>()
                quotaGates += gate
                gate.await()
            }
            return AccessContract.QuotaUiState(currentPlanName = "Test Plan")
        }

        override suspend fun loadQuotaTrafficUsedBytes(periodIsMonthly: Boolean): Long = -1L
    }
}
