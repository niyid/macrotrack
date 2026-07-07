package com.techducat.macrotrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * MacroTrackDatabase — the ONLY place this app's data lives.
 *
 * P2P/privacy note: unlike buzzr-p2p there is no peer-sync, no gossip, no
 * export path at all by default. Everything the user logs stays in this
 * on-device SQLite file. The `foods` table is a rebuildable cache; the
 * `diary_entries` table is the ledger the user actually cares about.
 *
 * Schema version history:
 *   1 — initial (foods, diary_entries, user_goals)
 */
@Database(
    entities = [FoodEntity::class, DiaryEntryEntity::class, UserGoalsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MacroTrackDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun goalsDao(): GoalsDao
}
