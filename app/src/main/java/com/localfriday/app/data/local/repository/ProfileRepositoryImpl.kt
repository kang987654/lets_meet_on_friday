package com.localfriday.app.data.local.repository

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.dao.ProfileDao
import com.localfriday.app.data.local.db.entity.ProfileEntity
import com.localfriday.app.data.local.prefs.SettingsDataStore
import com.localfriday.app.domain.memory.ProfileRepository
import com.localfriday.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore
) : ProfileRepository {

    override fun getProfile(): Flow<UserProfile?> {
        return profileDao.getProfileFlow().combine(settingsDataStore.responseStyleFlow) { entity, style ->
            if (entity == null) {
                UserProfile(
                    name = "User",
                    role = "Default",
                    preferences = mapOf("response_style" to style)
                )
            } else {
                UserProfile(
                    name = entity.name,
                    role = entity.style,
                    preferences = mapOf("response_style" to style)
                )
            }
        }
    }

    override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> {
        return try {
            val entity = ProfileEntity(
                id = "LOCAL_USER",
                name = profile.name,
                style = profile.role,
                updatedAt = System.currentTimeMillis()
            )
            profileDao.insertOrUpdateProfile(entity)
            
            profile.preferences["response_style"]?.let {
                settingsDataStore.saveResponseStyle(it)
            }
            
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbWriteError("profile"))
        }
    }
}
