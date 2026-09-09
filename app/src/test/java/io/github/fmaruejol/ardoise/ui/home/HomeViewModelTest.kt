package io.github.fmaruejol.ardoise.ui.home

import android.app.Application
import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val repository = ServerRepository(preferences, SpliitApiFactory { api })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opens on the default instance until one is chosen`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        assertEquals(GroupPreferences.DEFAULT_BASE_URL, viewModel.state.value.baseUrl)
    }

    @Test
    fun `follows the stored address, so coming back needs no reload`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        repository.setBaseUrl("https://spliit.example.com")
        advanceUntilIdle()

        assertEquals("https://spliit.example.com/", viewModel.state.value.baseUrl)
    }
}
