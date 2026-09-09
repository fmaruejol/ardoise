package io.github.fmaruejol.ardoise.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import io.github.fmaruejol.ardoise.data.local.SpliitDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A real Room database, in memory, driven by the test's own dispatcher. Real
 * rather than faked: the mapping, the query ordering and the cached-then-fresh
 * sequencing are the things worth testing.
 */
fun testDatabase(dispatcher: CoroutineDispatcher): SpliitDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        SpliitDatabase::class.java,
    )
        .setQueryExecutor(dispatcher.asExecutor())
        .setTransactionExecutor(dispatcher.asExecutor())
        .allowMainThreadQueries()
        .build()

fun testCache(api: SpliitApi, dispatcher: CoroutineDispatcher): SpliitCache =
    SpliitCache(api, testDatabase(dispatcher))

/**
 * How long a Turbine `test { }` waits. Its own default is 3 seconds of *real*
 * time, while these run on a virtual clock over an in-memory database.
 */
val TURBINE_TIMEOUT: Duration = 30.seconds
