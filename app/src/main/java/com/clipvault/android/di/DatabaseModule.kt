package com.clipvault.android.di

import android.content.Context
import com.clipvault.android.data.local.AppDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.domain.repository.ClipboardRepository
import com.clipvault.android.domain.repository.ClipboardRepositoryImpl
import com.clipvault.android.service.ClipboardServiceHelper
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
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideClipDao(database: AppDatabase): ClipDao {
        return database.clipDao()
    }

    @Provides
    @Singleton
    fun provideClipboardServiceHelper(@ApplicationContext context: Context): ClipboardServiceHelper {
        return ClipboardServiceHelper.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideClipboardRepository(
        clipDao: ClipDao,
        clipboardServiceHelper: ClipboardServiceHelper
    ): ClipboardRepository {
        return ClipboardRepositoryImpl(clipDao, clipboardServiceHelper)
    }
}
