package io.github.fmaruejol.ardoise.data.local.outbox

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The outbox holds what the server has never seen, so a schema change must
 * carry every queued expense across.
 *
 * Built from the exported version-1 schema rather than through
 * `MigrationTestHelper`, which reads schemas from assets that Robolectric is
 * not given. Opening it with the app's own builder runs the migration, and Room
 * validates the schema it ends up with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OutboxMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `a queued expense survives the move to version 2, with no id yet`() {
        createVersion1(context.getDatabasePath(NAME)) { db ->
            db.execSQL(
                "INSERT INTO pending_expenses (id, groupId, title, amount, expenseDate, paidById, " +
                    "splitMode, categoryId, isReimbursement, notes, recurrenceRule, originalAmount, " +
                    "originalCurrency, conversionRate, queuedAt, attempts, lastError) VALUES " +
                    "('q1', 'g1', 'Taxi', 2200, '2026-09-11', 'p1', 'EVENLY', 0, 0, NULL, 'NONE', " +
                    "NULL, NULL, NULL, 1, 1, 'network')",
            )
            db.execSQL(
                "INSERT INTO pending_expense_paid_for (pendingId, participantId, shares, position) " +
                    "VALUES ('q1', 'p1', 1, 0)",
            )
        }

        val database = outboxDatabase(context)
        val queued = runBlocking { database.outbox().find("q1") }
        database.close()

        assertEquals("Taxi", queued?.expense?.title)
        assertEquals(1, queued?.expense?.attempts)
        assertNull(queued?.expense?.expenseId)
        assertEquals(listOf("p1"), queued?.paidFor?.map { it.participantId })
    }

    private fun createVersion1(file: File, fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMAS, "1.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1
            fill(db)
        }
    }

    private companion object {
        /** `outboxDatabase`'s file name, which the test has to open as the app does. */
        const val NAME = "spliit-outbox"

        /** Relative to the module, which is where Gradle runs unit tests. */
        val SCHEMAS = File("schemas/io.github.fmaruejol.ardoise.data.local.outbox.OutboxDatabase")
    }
}
