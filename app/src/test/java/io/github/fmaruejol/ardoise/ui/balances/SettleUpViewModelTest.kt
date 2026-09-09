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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettleUpViewModelTest {
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

    private fun viewModel() = SettleUpViewModel("g1", groups, balances, connectivity)

    private fun state(
        totals: List<Pair<String, Long>>,
        settling: List<Reimbursement>,
    ) {
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(
                balances = totals.map { (id, total) ->
                    Balance(id, paid = 0, paidFor = 0, total = total)
                },
                reimbursements = settling,
            ),
        )
    }

    @Test
    fun `says which side a payment clears`() = runTest(dispatcher) {
        state(
            totals = listOf("p1" to 9000L, "p2" to -6000L, "p3" to -3000L),
            settling = listOf(
                Reimbursement("p2", "p1", 6000L),
                Reimbursement("p3", "p1", 3000L),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val transfers = viewModel.state.value.transfers
        // Ben's 60.00 is the whole of what he owes, and Ana is still owed 30.00
        // after it, so it clears him and not her.
        assertEquals(Clears.From, transfers[0].clears)
    }

    @Test
    fun `reads what a payment clears against what earlier ones already paid`() =
        runTest(dispatcher) {
            state(
                totals = listOf("p1" to 9000L, "p2" to -6000L, "p3" to -3000L),
                settling = listOf(
                    Reimbursement("p2", "p1", 6000L),
                    Reimbursement("p3", "p1", 3000L),
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            // Taken on its own, Cara's 30.00 clears only Cara: Ana is owed
            // 90.00.
            assertEquals(Clears.Both, viewModel.state.value.transfers[1].clears)
        }

    @Test
    fun `writes nothing at all`() = runTest(dispatcher) {
        state(
            totals = listOf("p1" to 6000L, "p2" to -6000L),
            settling = listOf(Reimbursement("p2", "p1", 6000L)),
        )
        viewModel()
        advanceUntilIdle()

        // "Mark as paid" opens the expense form with the payment filled in.
        assertTrue(api.callsTo("createExpense").isEmpty())
    }

    @Test
    fun `knows which side of a payment you are on`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        state(
            totals = listOf("p1" to 6000L, "p2" to -6000L),
            settling = listOf(Reimbursement("p2", "p1", 6000L)),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val transfer = viewModel.state.value.transfers.single()
        assertTrue(transfer.toIsYou)
        assertFalse(transfer.fromIsYou)
    }

    @Test
    fun `nothing to settle is not an error`() = runTest(dispatcher) {
        state(totals = listOf("p1" to 0L, "p2" to 0L), settling = emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSettled)
    }
}
