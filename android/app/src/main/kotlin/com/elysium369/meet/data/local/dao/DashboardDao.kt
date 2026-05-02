package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.DashboardEntity
import com.elysium369.meet.data.local.entities.DashboardWidgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboards ORDER BY createdAt DESC")
    fun getAllDashboards(): Flow<List<DashboardEntity>>

    @Query("SELECT * FROM dashboards WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultDashboard(): DashboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDashboard(dashboard: DashboardEntity)

    @Delete
    suspend fun deleteDashboard(dashboard: DashboardEntity)

    @Query("SELECT * FROM dashboard_widgets WHERE dashboardId = :dashboardId")
    fun getWidgetsForDashboard(dashboardId: String): Flow<List<DashboardWidgetEntity>>

    @Query("SELECT * FROM dashboard_widgets WHERE dashboardId = :dashboardId")
    suspend fun getWidgetsForDashboardSync(dashboardId: String): List<DashboardWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidget(widget: DashboardWidgetEntity)

    @Update
    suspend fun updateWidget(widget: DashboardWidgetEntity)

    @Delete
    suspend fun deleteWidget(widget: DashboardWidgetEntity)

    @Query("DELETE FROM dashboard_widgets WHERE dashboardId = :dashboardId")
    suspend fun deleteWidgetsByDashboardId(dashboardId: String)
}
