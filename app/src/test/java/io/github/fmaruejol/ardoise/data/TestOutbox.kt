package io.github.fmaruejol.ardoise.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import io.github.fmaruejol.ardoise.data.local.outbox.OutboxDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

/**
 * The real outbox over a real in-memory database, for the same reason
 * [testCache] is real: the queue's behaviour is the thing under test, and a
 * fake would only restate what the test already believed about it.
 */
fun testOutboxDatabase(dispatcher: CoroutineDispatcher): OutboxDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        OutboxDatabase::class.java,
    )
        .setQueryExecutor(dispatcher.asExecutor())
        .setTransactionExecutor(dispatcher.asExecutor())
        .allowMainThreadQueries()
        .build()

fun testOutbox(
    api: SpliitApi,
    cache: SpliitCache,
    preferences: GroupPreferences,
    dispatcher: CoroutineDispatcher,
): ExpenseOutbox = ExpenseOutbox(
    api = api,
    dao = testOutboxDatabase(dispatcher).outbox(),
    cache = cache,
    preferences = preferences,
)
