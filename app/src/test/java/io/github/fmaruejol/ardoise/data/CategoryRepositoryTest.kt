package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val cache = testCache(api, dispatcher)
    private val repository = CategoryRepository(cache)

    private val categories = listOf(Category(0, "Uncategorized", "General"))

    @Test
    fun `keeps the categories across restarts, not just across screens`() =
        runTest(dispatcher) {
            api.listCategoriesResult = SpliitResult.Success(categories)
            repository.categories().test(timeout = TURBINE_TIMEOUT) {
                assertEquals(categories, (awaitItem() as SpliitResult.Success).value)
                cancelAndIgnoreRemainingEvents()
            }

            // They are in the database now, so the expense form has real
            // category names offline instead of falling back to "General".
            api.listCategoriesResult =
                SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
            repository.categories().test(timeout = TURBINE_TIMEOUT) {
                assertEquals(categories, (awaitItem() as SpliitResult.Success).value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed refresh leaves the cached list alone`() = runTest(dispatcher) {
        api.listCategoriesResult = SpliitResult.Success(categories)
        repository.categories().test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.categories().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(categories, (awaitItem() as SpliitResult.Success).value)
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty cache reports the failure rather than claiming there are none`() = runTest(dispatcher) {
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))

        repository.categories().test(timeout = TURBINE_TIMEOUT) {
            // Nothing has ever been fetched, so an empty list would be a claim
            // rather than an answer: the failure is what is said instead.
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `asks again on each read, in case the server has grown one`() =
        runTest(dispatcher) {
            api.listCategoriesResult = SpliitResult.Success(categories)

            repeat(2) {
                repository.categories().test(timeout = TURBINE_TIMEOUT) {
                    awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
            }

            // Categories are seeded server-side and effectively fixed, so this
            // is cheap insurance rather than a real cost: the cached list is
            // what the screen draws either way.
            assertEquals(2, api.callsTo("listCategories").size)
        }
}
