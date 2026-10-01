package com.elysium.vanguard.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TitanDatabase {
        return Room.databaseBuilder(
            context,
            TitanDatabase::class.java,
            "titan_sovereign.db"
        )
            .addMigrations(TitanDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideFileSearchDao(database: TitanDatabase): FileSearchDao {
        return database.fileSearchDao()
    }

    @Provides
    fun provideTrashDao(database: TitanDatabase): TrashDao {
        return database.trashDao()
    }
}
