package com.elysium369.meet.data.supabase

import com.elysium369.meet.core.remote.RemoteResult
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.entities.VehicleEntity
import com.elysium369.meet.identity.ActivePrincipal
import com.elysium369.meet.identity.ActivePrincipalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehicleAccountIsolationTest {
    private class Owner : ActivePrincipalProvider {
        var id="account-a"
        override fun current()=ActivePrincipal.authenticated(id)
    }
    private class Dao : VehicleDao {
        val rows=MutableStateFlow<List<VehicleEntity>>(emptyList())
        var writes=0
        var afterLookup: (() -> Unit)?=null
        override fun getAllVehiclesForUser(userId:String):Flow<List<VehicleEntity>> = rows
        override suspend fun getVehicleByIdForUser(userId:String,id:String)=rows.value.firstOrNull { it.id==id && it.userId==userId }
        override suspend fun getVehicleById(id:String):VehicleEntity? {
            val result=rows.value.firstOrNull { it.id==id };afterLookup?.invoke();return result
        }
        override suspend fun getVehicleByVinForUser(userId:String,vin:String)=rows.value.firstOrNull { it.userId==userId && it.vin==vin }
        override suspend fun getPendingVehiclesForUser(userId: String): List<VehicleEntity> =
            rows.value.filter { it.userId == userId && it.syncedAt == null }
        override suspend fun markVehicleSynced(userId: String, id: String, syncedAt: Long) {
            rows.value = rows.value.map {
                if (it.userId == userId && it.id == id) it.copy(syncedAt = syncedAt) else it
            }
        }
        override suspend fun insertVehicle(vehicle:VehicleEntity) { writes++ }
        override suspend fun deleteVehicle(vehicle:VehicleEntity) { writes++ }
    }
    private fun vehicle(owner:String,id:String="vehicle-1")=Vehicle(id,owner,2005,"Test","Test","Test",vin="NOT_READ",plate="NOT_SET")
    @Test fun `guest vehicle remains valid locally with cloud pending`()=runBlocking {
        val dao=Dao()
        val guest = object : ActivePrincipalProvider {
            override fun current() = ActivePrincipal.local("guest-device")
        }
        val result=VehicleRepository(dao,guest).insertVehicle(vehicle(guest.current().id))
        assertTrue(result is RemoteResult.TransportFailure)
        assertEquals(1,dao.writes)
    }
    @Test fun `foreign add and delete never reach local mutation`()=runBlocking {
        val dao=Dao();val owner=Owner();val repo=VehicleRepository(dao,owner)
        assertTrue(repo.insertVehicle(vehicle("account-b")) is RemoteResult.Forbidden)
        assertTrue(repo.deleteVehicle(vehicle("account-b")) is RemoteResult.Forbidden)
        assertEquals(0,dao.writes)
    }
    @Test fun `identifier collision cannot overwrite another owner`()=runBlocking {
        val dao=Dao();dao.rows.value=listOf(vehicle("account-b").toEntity())
        assertTrue(VehicleRepository(dao,Owner()).insertVehicle(vehicle("account-a")) is RemoteResult.Forbidden)
        assertEquals(0,dao.writes)
    }
    @Test fun `account switch during lookup cannot write old account`()=runBlocking {
        val dao=Dao();val owner=Owner();dao.afterLookup={owner.id="account-b"}
        assertTrue(VehicleRepository(dao,owner).insertVehicle(vehicle("account-a")) is RemoteResult.Forbidden)
        assertEquals(0,dao.writes)
    }
    @Test fun `stale projection and foreign rows are hidden`()=runBlocking {
        val dao=Dao();val owner=Owner();val repo=VehicleRepository(dao,owner)
        dao.rows.value=listOf(vehicle("account-a").toEntity(),vehicle("account-b","vehicle-2").toEntity())
        assertEquals(listOf("vehicle-1"),repo.getVehiclesForUser("account-a").first().map { it.id })
        owner.id="account-b"
        assertTrue(repo.getVehiclesForUser("account-a").first().isEmpty())
        assertEquals(listOf("vehicle-2"),repo.getVehiclesForUser("account-b").first().map { it.id })
    }
}
