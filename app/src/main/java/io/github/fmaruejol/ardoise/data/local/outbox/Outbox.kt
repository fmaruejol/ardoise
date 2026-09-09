package io.github.fmaruejol.ardoise.data.local.outbox

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * An expense typed while offline, waiting to be sent.
 *
 * **The one thing the app stores that the server has never seen**, so it lives
 * in its own database with real migrations rather than beside the cache, whose
 * destructive migration would delete it on the next schema change.
 */
@Entity(tableName = "pending_expenses", indices = [Index("groupId")])
data class PendingExpenseEntity(
    /** Local, and never sent: the server assigns the real id on create. */
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amount: Long,
    val expenseDate: String,
    val paidById: String,
    val splitMode: String,
    val categoryId: Int,
    val isReimbursement: Boolean,
    val notes: String?,
    val recurrenceRule: String,
    val originalAmount: Long?,
    val originalCurrency: String?,
    val conversionRate: String?,
    /** When it was typed, which is also the order it will be sent in. */
    val queuedAt: Long,
    /**
     * How many times sending it has been tried.
     *
     * Zero means it has never left the device, which is what makes a retry
     * unambiguously safe. Above zero, a send may have reached the server and
     * lost its answer. See `ExpenseOutbox.alreadySent`.
     */
    val attempts: Int,
    /** The last thing that went wrong, for the card to show. */
    val lastError: String?,
)

@Entity(
    tableName = "pending_expense_paid_for",
    primaryKeys = ["pendingId", "participantId"],
    indices = [Index("pendingId")],
)
data class PendingPaidForEntity(
    val pendingId: String,
    val participantId: String,
    /** In the stored representation for the split mode, as everywhere else. */
    val shares: Long,
    val position: Int,
)

data class PendingExpenseWithPaidFor(
    @Embedded val expense: PendingExpenseEntity,
    @Relation(parentColumn = "id", entityColumn = "pendingId")
    val paidFor: List<PendingPaidForEntity>,
)

@Dao
interface OutboxDao {
    @Transaction
    @Query("SELECT * FROM pending_expenses WHERE groupId = :groupId ORDER BY queuedAt")
    fun observe(groupId: String): Flow<List<PendingExpenseWithPaidFor>>

    @Transaction
    @Query("SELECT * FROM pending_expenses WHERE id = :id")
    suspend fun find(id: String): PendingExpenseWithPaidFor?

    /** Oldest first: they were typed in an order and are sent in it. */
    @Transaction
    @Query("SELECT * FROM pending_expenses ORDER BY queuedAt")
    suspend fun all(): List<PendingExpenseWithPaidFor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: PendingExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaidFor(paidFor: List<PendingPaidForEntity>)

    @Query("UPDATE pending_expenses SET attempts = :attempts, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: String, attempts: Int, error: String?)

    @Query("DELETE FROM pending_expense_paid_for WHERE pendingId = :id")
    suspend fun deletePaidFor(id: String)

    @Query("DELETE FROM pending_expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Transaction
    suspend fun enqueue(expense: PendingExpenseEntity, paidFor: List<PendingPaidForEntity>) {
        insert(expense)
        deletePaidFor(expense.id)
        insertPaidFor(paidFor)
    }

    @Transaction
    suspend fun remove(id: String) {
        deletePaidFor(id)
        deleteExpense(id)
    }

    /**
     * Everything queued for a group the user has removed from this device.
     *
     * Without the group id there is no way back into it, so an expense still
     * waiting for it can never be sent.
     */
    @Transaction
    suspend fun removeAllFor(groupId: String) {
        deletePaidForOf(groupId)
        deleteExpensesOf(groupId)
    }

    @Query(
        "DELETE FROM pending_expense_paid_for WHERE pendingId IN " +
            "(SELECT id FROM pending_expenses WHERE groupId = :groupId)",
    )
    suspend fun deletePaidForOf(groupId: String)

    @Query("DELETE FROM pending_expenses WHERE groupId = :groupId")
    suspend fun deleteExpensesOf(groupId: String)
}

/**
 * The queue's own database.
 *
 * No `fallbackToDestructiveMigration`: everything in here is unsent work.
 * A schema change to these two tables needs a real migration, and that is the
 * price of holding something the server cannot give back.
 */
@Database(
    entities = [PendingExpenseEntity::class, PendingPaidForEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun outbox(): OutboxDao
}

fun outboxDatabase(context: Context): OutboxDatabase =
    Room.databaseBuilder(context, OutboxDatabase::class.java, "spliit-outbox").build()
