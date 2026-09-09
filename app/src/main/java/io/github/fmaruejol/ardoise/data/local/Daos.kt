package io.github.fmaruejol.ardoise.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    /**
     * The groups this device knows, in the order the ids are given: Room
     * cannot order by a parameter list, so the caller keeps the order.
     */
    @Query("SELECT * FROM `groups` WHERE id IN (:ids)")
    fun observeAll(ids: List<String>): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    fun observe(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM participants WHERE groupId = :groupId ORDER BY position")
    fun observeParticipants(groupId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE groupId = :groupId ORDER BY position")
    suspend fun participants(groupId: String): List<ParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(groups: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    @Query("DELETE FROM participants WHERE groupId = :groupId")
    suspend fun clearParticipants(groupId: String)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Replaces a group and everyone in it. Participants are deleted rather
     * than upserted: somebody removed upstream has to disappear here too.
     */
    @Transaction
    suspend fun replace(group: GroupEntity, participants: List<ParticipantEntity>) {
        upsert(listOf(group))
        clearParticipants(group.id)
        upsertParticipants(participants)
    }
}

@Dao
interface ExpenseDao {
    @Transaction
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY position LIMIT :limit")
    fun observePage(groupId: String, limit: Int): Flow<List<ExpenseWithPaidFor>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observe(id: String): Flow<ExpenseWithPaidFor?>

    @Query("SELECT COUNT(*) FROM expenses WHERE groupId = :groupId")
    suspend fun count(groupId: String): Int

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun clear(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expenses: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaidFor(paidFor: List<PaidForEntity>)

    @Query("SELECT id, notes, conversionRate FROM expenses WHERE groupId = :groupId")
    suspend fun detailsOnly(groupId: String): List<ExpenseDetailColumns>

    /** Everything the group has that the page did not bring back. */
    @Query(
        "DELETE FROM expenses WHERE groupId = :groupId AND id NOT IN (:pageIds)",
    )
    suspend fun deleteOutside(groupId: String, pageIds: List<String>)

    /**
     * The rows the page covered and did not bring back, i.e. deleted upstream.
     * Anything older than its last row is outside the window, not gone.
     */
    @Query(
        """
        DELETE FROM expenses
        WHERE groupId = :groupId
          AND id NOT IN (:pageIds)
          AND (
              expenseDate > :oldestDate
              OR (expenseDate = :oldestDate AND createdAt >= :oldestCreatedAt)
          )
        """,
    )
    suspend fun deleteWithinWindow(
        groupId: String,
        pageIds: List<String>,
        oldestDate: String,
        oldestCreatedAt: Long,
    )

    @Query("DELETE FROM expense_paid_for WHERE expenseId IN (:expenseIds)")
    suspend fun clearPaidForOf(expenseIds: List<String>)

    /** Shares whose expense has gone. Nothing else would ever remove them. */
    @Query("DELETE FROM expense_paid_for WHERE expenseId NOT IN (SELECT id FROM expenses)")
    suspend fun clearOrphanPaidFor()

    @Query(
        "SELECT id FROM expenses WHERE groupId = :groupId AND id NOT IN (:pageIds) ORDER BY position",
    )
    suspend fun idsOutside(groupId: String, pageIds: List<String>): List<String>

    @Query("UPDATE expenses SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    /**
     * Replaces a group's feed with the page just fetched.
     *
     * Not an upsert: an expense deleted on another device has to leave, and
     * merging pages would keep it.
     *
     * **What the list does not return is carried over rather than nulled.**
     * `groups.expenses.list` has no `notes` and no `conversionRate`, so
     * replacing a row from a summary used to blank both. The refresh that does
     * know them overwrites straight after, which keeps the carried value from
     * going stale.
     *
     * **Rows older than the page stay.** They are outside its window, not
     * gone; [complete] says the page was the whole list, and then anything
     * missing really has gone. Kept rows are renumbered to sit after the page,
     * since [position] is the feed's order and a page starts at zero.
     */
    @Transaction
    suspend fun replacePage(
        groupId: String,
        expenses: List<ExpenseEntity>,
        paidFor: List<PaidForEntity>,
        complete: Boolean,
    ) {
        val known = detailsOnly(groupId).associateBy { it.id }
        val pageIds = expenses.map { it.id }
        val oldest = expenses.lastOrNull()

        if (complete || oldest == null) {
            deleteOutside(groupId, pageIds)
        } else {
            deleteWithinWindow(groupId, pageIds, oldest.expenseDate, oldest.createdAt)
        }
        clearPaidForOf(pageIds)
        clearOrphanPaidFor()

        upsert(
            expenses.map { expense ->
                val previous = known[expense.id] ?: return@map expense
                expense.copy(
                    notes = expense.notes ?: previous.notes,
                    conversionRate = expense.conversionRate ?: previous.conversionRate,
                )
            },
        )
        upsertPaidFor(paidFor)

        idsOutside(groupId, pageIds).forEachIndexed { index, id ->
            setPosition(id, expenses.size + index)
        }
    }
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balances WHERE groupId = :groupId")
    fun observeBalances(groupId: String): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM reimbursements WHERE groupId = :groupId ORDER BY position")
    fun observeReimbursements(groupId: String): Flow<List<ReimbursementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalances(balances: List<BalanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReimbursements(reimbursements: List<ReimbursementEntity>)

    @Query("DELETE FROM balances WHERE groupId = :groupId")
    suspend fun clearBalances(groupId: String)

    @Query("DELETE FROM reimbursements WHERE groupId = :groupId")
    suspend fun clearReimbursements(groupId: String)

    @Transaction
    suspend fun replace(
        groupId: String,
        balances: List<BalanceEntity>,
        reimbursements: List<ReimbursementEntity>,
    ) {
        clearBalances(groupId)
        clearReimbursements(groupId)
        upsertBalances(balances)
        upsertReimbursements(reimbursements)
    }
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE groupId = :groupId ORDER BY position LIMIT :limit")
    fun observe(groupId: String, limit: Int): Flow<List<ActivityEntity>>

    @Query("DELETE FROM activities WHERE groupId = :groupId")
    suspend fun clear(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activities: List<ActivityEntity>)

    @Transaction
    suspend fun replace(groupId: String, activities: List<ActivityEntity>) {
        clear(groupId)
        upsert(activities)
    }
}

/**
 * Everything the cache holds about one group, deleted together.
 *
 * Its own DAO because it is the only operation spanning the others: seven
 * tables, which spread across the callers were seven statements to keep in
 * step with the schema.
 *
 * Deliberately **not** `ON DELETE CASCADE`: a foreign key would require every
 * cached row to have its group row already, and the feed and the group are
 * fetched concurrently, so the constraint would turn that race into a crash.
 */
@Dao
interface CacheDao {
    @Transaction
    suspend fun forget(groupId: String) {
        // Children first, which the transaction makes academic.
        deleteExpenseShares(groupId)
        deleteExpenses(groupId)
        deleteBalances(groupId)
        deleteReimbursements(groupId)
        deleteActivities(groupId)
        deleteParticipants(groupId)
        deleteGroup(groupId)
    }

    @Query(
        "DELETE FROM expense_paid_for WHERE expenseId IN " +
            "(SELECT id FROM expenses WHERE groupId = :groupId)",
    )
    suspend fun deleteExpenseShares(groupId: String)

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun deleteExpenses(groupId: String)

    @Query("DELETE FROM balances WHERE groupId = :groupId")
    suspend fun deleteBalances(groupId: String)

    @Query("DELETE FROM reimbursements WHERE groupId = :groupId")
    suspend fun deleteReimbursements(groupId: String)

    @Query("DELETE FROM activities WHERE groupId = :groupId")
    suspend fun deleteActivities(groupId: String)

    @Query("DELETE FROM participants WHERE groupId = :groupId")
    suspend fun deleteParticipants(groupId: String)

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY grouping, name")
    fun observe(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(categories: List<CategoryEntity>)
}
