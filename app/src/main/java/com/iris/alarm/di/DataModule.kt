package com.iris.alarm.di

import android.content.Context
import androidx.room.Room
import com.iris.alarm.data.local.AlarmDao
import com.iris.alarm.data.local.IrisDatabase
import com.iris.alarm.data.repository.AlarmRepositoryImpl
import com.iris.alarm.domain.model.DeviceCapabilities
import com.iris.alarm.domain.repository.AlarmRepository
import com.iris.alarm.vision.AndroidDeviceCapabilities
import com.iris.alarm.vision.LumenMonitor
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): IrisDatabase =
        Room.databaseBuilder(context, IrisDatabase::class.java, IrisDatabase.NAME).build()

    @Provides
    fun provideAlarmDao(database: IrisDatabase): AlarmDao = database.alarmDao()
}

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideLumenMonitor(@ApplicationContext context: Context): LumenMonitor =
        LumenMonitor(context)

    @Provides
    @Singleton
    fun provideDeviceCapabilities(@ApplicationContext context: Context): DeviceCapabilities =
        AndroidDeviceCapabilities(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(impl: AlarmRepositoryImpl): AlarmRepository
}
