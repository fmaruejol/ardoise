package io.github.fmaruejol.ardoise.di

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import io.github.fmaruejol.ardoise.ui.creategroup.CreateGroupViewModel
import io.github.fmaruejol.ardoise.ui.expense.ExpenseFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import org.koin.viewmodel.resolveViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class, KoinInternalApi::class)
class SavedStateInjectionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Application>())
            modules(appModules)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `the create-group form is given a saved state handle`() {
        assertNotNull(resolve(CreateGroupViewModel::class))
    }

    @Test
    fun `the expense form is given a saved state handle`() {
        assertNotNull(
            resolve(ExpenseFormViewModel::class) { parametersOf("g1", null, null, null) },
        )
    }

    private fun <T : ViewModel> resolve(
        type: KClass<T>,
        parameters: ParametersDefinition? = null,
    ): T {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .get()
        return resolveViewModel(
            vmClass = type,
            viewModelStore = activity.viewModelStore,
            extras = activity.defaultViewModelCreationExtras,
            scope = GlobalContext.get().scopeRegistry.rootScope,
            parameters = parameters,
        )
    }
}
