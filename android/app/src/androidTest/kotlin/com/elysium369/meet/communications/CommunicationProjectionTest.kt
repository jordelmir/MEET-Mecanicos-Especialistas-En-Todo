package com.elysium369.meet.communications

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.CommunicationConversationEntity
import com.elysium369.meet.data.local.entities.CommunicationEventEntity
import com.elysium369.meet.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunicationProjectionTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),MeetDatabase::class.java)

    @Test fun migrationPreservesExistingCiphertextAndAllowsSameEventForTwoOwners() {
        val name="communication-migration-85-86.db"
        helper.createDatabase(name,85).use { db ->
            db.execSQL("INSERT INTO communication_conversations(conversationId,ownerPrincipalId,kind,title,requestState,proofState,createdAtEpochMs,updatedAtEpochMs) VALUES('conversation','one','DIRECT','Test','ACCEPTED','SERVER_AUTHORITATIVE',1,1)")
            db.execSQL("INSERT INTO communication_events(eventId,conversationId,ownerPrincipalId,senderPrincipalId,senderDeviceId,eventType,localCiphertextBase64,localNonceBase64,syncState,createdAtEpochMs) VALUES('same-event','conversation','one','one','device','TEXT','original-ciphertext','nonce','PENDING_REMOTE',1)")
        }
        helper.runMigrationsAndValidate(name,86,true,AppModule.MIGRATION_85_86).use { db ->
            db.query("SELECT localCiphertextBase64 FROM communication_events WHERE ownerPrincipalId='one'").use { cursor ->
                assertTrue(cursor.moveToFirst());assertEquals("original-ciphertext",cursor.getString(0))
            }
            db.execSQL("INSERT INTO communication_conversations(conversationId,ownerPrincipalId,kind,title,requestState,proofState,createdAtEpochMs,updatedAtEpochMs) VALUES('conversation','two','DIRECT','Test','ACCEPTED','SERVER_AUTHORITATIVE',1,1)")
            db.execSQL("INSERT INTO communication_events(eventId,conversationId,ownerPrincipalId,senderPrincipalId,senderDeviceId,eventType,localCiphertextBase64,localNonceBase64,syncState,createdAtEpochMs) VALUES('same-event','conversation','two','one','device','TEXT','other-ciphertext','nonce','NEARBY_RECEIVED',1)")
            db.query("SELECT COUNT(*) FROM communication_events WHERE eventId='same-event'").use { cursor -> cursor.moveToFirst();assertEquals(2,cursor.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test fun nearbyAcknowledgementRetainsOutboxAndServerProjectionReconcilesWithoutCrossAccountWrites()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),MeetDatabase::class.java).build()
        try {
            val dao=db.communicationDao()
            for(owner in listOf("one","two")) dao.insertConversation(CommunicationConversationEntity("conversation",owner,"DIRECT","Test",createdAtEpochMs=1,updatedAtEpochMs=1))
            val outgoing=CommunicationEventEntity("same-event","conversation","one","one","device","TEXT","cipher","nonce",remoteEnvelopeJson="envelope",syncState="PENDING_REMOTE",createdAtEpochMs=1)
            assertTrue(dao.appendEvent(outgoing))
            assertTrue(dao.appendEvent(outgoing.copy(ownerPrincipalId="two",syncState="NEARBY_RECEIVED")))
            dao.acknowledgeNearbyEvent("same-event","one","conversation")
            assertEquals("NEARBY_ACK",dao.pendingOnlineEvents("one").single().syncState)
            assertEquals("NEARBY_RECEIVED",dao.getOnlineEvent("same-event","two","conversation")!!.syncState)
            dao.reconcileReceivedEvent("same-event","two","envelope",10,2)
            assertEquals(10L,dao.getOnlineEvent("same-event","two","conversation")!!.serverSequence)
            assertEquals("SERVER_ACK",dao.getOnlineEvent("same-event","two","conversation")!!.syncState)
            assertEquals("NEARBY_ACK",dao.getOnlineEvent("same-event","one","conversation")!!.syncState)
            dao.acknowledgeOnlineEvent("same-event","one",10,2)
            assertTrue(dao.pendingOnlineEvents("one").isEmpty())
        } finally { db.close() }
    }
}
