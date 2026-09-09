package io.github.fmaruejol.ardoise.ui.balances

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Balance
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.Reimbursement
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.BalanceRepository
import io.github.fmaruejol.ardoise.data.FakeConnectivity
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.group
import io.github.fmaruejol.ardoise.data.testCache
import io.github.fmaruejol.ardoise.data.testOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BalancesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val balances = BalanceRepository(api, preferences, cache)

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val cara = Participant("p3", "Cara")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben, cara)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val connectivity = FakeConnectivity()

    private fun viewModel() = BalancesViewModel("g1", groups, balances, connectivity)

    private fun balances(vararg totals: Pair<String, Long>, settling: List<Reimbursement> = emptyList()) {
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(
                balances = totals.map { (id, total) -> Balance(id, paid = 0, paidFor = 0, total = total) },
                reimbursements = settling,
            ),
        )
    }

    @Test
    fun `lists everyone, including whoever is square`() = runTest(dispatcher) {
        balances("p1" to 6000L, "p2" to -6000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        // Cara has no balance row at all, and still belongs on the screen:
        // "who is not in this list" is not a question the user should have to
        // answer for themselves.
        assertEquals(listOf("Ana", "Ben", "Cara"), viewModel.state.value.rows.map { it.name })
        assertEquals(0L, viewModel.state.value.rows.last().total)
    }

    @Test
    fun `draws both sides to the same scale`() = runTest(dispatcher) {
        balances("p1" to 6000L, "p2" to -3000L, "p3" to -3000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        val weights = viewModel.state.value.rows.associate { it.name to it.weight }
        // The widest bar is the largest position either way, so a 30.00 debt
        // reads as half of a 60.00 credit rather than as a full bar of its own.
        assertEquals(1f, weights["Ana"])
        assertEquals(0.5f, weights["Ben"])
    }

    @Test
    fun `has no position until you say who you are`() = runTest(dispatcher) {
        balances("p1" to 6000L, "p2" to -6000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        // A number here would be somebody else's.
        assertNull(viewModel.state.value.yourPosition)
        assertEquals(0, viewModel.state.value.counterparties)
    }

    @Test
    fun `counts only the people on the other side of you`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        balances("p1" to 6000L, "p2" to -4000L, "p3" to -2000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(6000L, viewModel.state.value.yourPosition)
        assertEquals(2, viewModel.state.value.counterparties)
    }

    @Test
    fun `someone up with you is not a counterparty`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        balances("p1" to 4000L, "p2" to 2000L, "p3" to -6000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        // Ben is owed money too; he is not who Ana collects from.
        assertEquals(1, viewModel.state.value.counterparties)
    }

    @Test
    fun `asks who you are once the balances are loaded and nobody has said`() =
        runTest(dispatcher) {
            balances("p1" to 6000L, "p2" to -6000L)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.needsIdentity)
        }

    @Test
    fun `does not ask before they have arrived`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(null)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.needsIdentity)
    }

    @Test
    fun `choosing a name here fills the position in`() = runTest(dispatcher) {
        balances("p1" to 6000L, "p2" to -6000L)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onYouChange(1)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.needsIdentity)
        assertEquals(-6000L, viewModel.state.value.yourPosition)
        // The same one preference the feed and the group settings write.
        assertEquals("p2", preferences.activeParticipantId("g1").first())
    }

    @Test
    fun `offers to settle only when the server suggests a payment`() = runTest(dispatcher) {
        balances("p1" to 0L, "p2" to 0L)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canSettle)
        assertTrue(viewModel.state.value.isSettled)
    }

    @Test
    fun `a settled group is not an empty one`() = runTest(dispatcher) {
        balances("p1" to 0L, "p2" to 0L)
        val viewModel = viewModel()
        advanceUntilIdle()

        // "Everyone is square" and "there is nothing here" are different
        // things to be told.
        assertFalse(viewModel.state.value.isEmpty)
    }
}
