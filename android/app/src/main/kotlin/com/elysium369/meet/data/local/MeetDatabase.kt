package com.elysium369.meet.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.data.local.entities.*

@Database(
    entities = [
        VehicleEntity::class,
        DiagnosticSessionEntity::class,
        DtcEventEntity::class,
        TripEntity::class,
        AdapterProfileEntity::class,
        DtcDefinitionEntity::class,
        MaintenanceAlertEntity::class,
        AiConsultEntity::class,
        CustomPidEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeetDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun sessionDao(): DiagnosticSessionDao
    abstract fun dtcDao(): DtcDao
    abstract fun tripDao(): TripDao
    abstract fun adapterDao(): AdapterProfileDao
    abstract fun dtcDefinitionDao(): DtcDefinitionDao
    abstract fun maintenanceDao(): MaintenanceAlertDao
    abstract fun aiConsultDao(): AiConsultDao
    abstract fun customPidDao(): CustomPidDao
}
