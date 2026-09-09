package io.github.fmaruejol.ardoise.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The offline read cache. **Nothing here is a source of truth**: every row
 * came from the server and is replaced by the next fetch, which is why a
 * schema change may throw it away.
 *
 * What cannot be re-downloaded, the group ids and which participant you are,
 * stays in DataStore. Room never touches it.
 */
@Database(
    entities = [
        GroupEntity::class,
        ParticipantEntity::class,
        ExpenseEntity::class,
        PaidForEntity::class,
        CategoryEntity::class,
        BalanceEntity::class,
        ReimbursementEntity::class,
        ActivityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SpliitDatabase : RoomDatabase() {
    abstract fun groups(): GroupDao

    abstract fun expenses(): ExpenseDao

    abstract fun balances(): BalanceDao

    abstract fun activities(): ActivityDao

    abstract fun categories(): CategoryDao

    /** One group's cached contents, removed together. */
    abstract fun cache(): CacheDao
}

fun spliitDatabase(context: Context): SpliitDatabase =
    Room.databaseBuilder(context, SpliitDatabase::class.java, "spliit-cache")
        // Nothing here that cannot be fetched again.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
