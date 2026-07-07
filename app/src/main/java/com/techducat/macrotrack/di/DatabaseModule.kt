package com.techducat.macrotrack.di

import android.content.Context
import androidx.room.Room
import com.techducat.macrotrack.data.db.DiaryDao
import com.techducat.macrotrack.data.db.FoodDao
import com.techducat.macrotrack.data.db.GoalsDao
import com.techducat.macrotrack.data.db.MacroTrackDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "macrotrack.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MacroTrackDatabase =
        Room.databaseBuilder(context, MacroTrackDatabase::class.java, DB_NAME)
            // Remove destructive migration once this ships with real users' data —
            // fine for the initial scaffold while the schema is still moving.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFoodDao(db: MacroTrackDatabase): FoodDao = db.foodDao()

    @Provides
    fun provideDiaryDao(db: MacroTrackDatabase): DiaryDao = db.diaryDao()

    @Provides
    fun provideGoalsDao(db: MacroTrackDatabase): GoalsDao = db.goalsDao()
}
