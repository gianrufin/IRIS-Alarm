package com.iris.alarm.data.repository

import com.iris.alarm.data.local.AlarmDao
import com.iris.alarm.data.local.toDomain
import com.iris.alarm.data.local.toEntity
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.repository.AlarmRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val dao: AlarmDao,
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getAlarm(id: Long): Alarm? = dao.findById(id)?.toDomain()

    override suspend fun getEnabledAlarms(): List<Alarm> =
        dao.findEnabled().map { it.toDomain() }

    override suspend fun upsert(alarm: Alarm): Long {
        // REPLACE-on-conflict makes insert() work for both paths and returns the
        // row id in either case, so callers never need to branch on id == 0.
        return dao.insert(alarm.toEntity())
    }

    override suspend fun delete(alarm: Alarm) = dao.delete(alarm.toEntity())

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
