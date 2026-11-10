package com.clipvault.android.di

import android.content.Context
import com.clipvault.android.data.local.AppDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.domain.repository.ClipboardRepositoryImpl
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addCallback(AppDatabase.DB_CALLBACK).build()
    }

    @Provides
    @Singleton
    fun provideClipDao(database: AppDatabase): ClipDao {
        return database.clipDao()
    }

    @Provides
    @Singleton
    fun provideClipboardRepository(clipDao: ClipDao): ClipboardRepository {
        return ClipboardRepositoryImpl(clipDao)
    }
}
