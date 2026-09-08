package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elysium369.meet.data.local.entities.PttChannelEntity
import com.elysium369.meet.data.local.entities.PttChannelMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PttChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: PttChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<PttChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: PttChannelMemberEntity)

    @Update
    suspend fun updateChannel(channel: PttChannelEntity)

    @Query("SELECT * FROM ptt_channels WHERE channelId = :channelId")
    suspend fun getChannelById(channelId: String): PttChannelEntity?

    @Query("SELECT * FROM ptt_channels WHERE state = 'ACTIVE' ORDER BY createdAtEpochMs DESC")
    fun getActiveChannelsFlow(): Flow<List<PttChannelEntity>>

    @Query("""
        SELECT c.* FROM ptt_channels c
        INNER JOIN ptt_channel_members m ON c.channelId = m.channelId
        WHERE m.principalId = :principalId AND m.state = 'JOINED' AND c.state = 'ACTIVE'
        ORDER BY c.createdAtEpochMs DESC
    """)
    fun getChannelsForPrincipalFlow(principalId: String): Flow<List<PttChannelEntity>>

    @Query("""
        SELECT c.* FROM ptt_channels c
        INNER JOIN ptt_channel_members m ON c.channelId = m.channelId
        WHERE m.principalId = :principalId AND m.state = 'JOINED' AND c.state = 'ACTIVE'
    """)
    suspend fun getChannelsForPrincipal(principalId: String): List<PttChannelEntity>

    @Query("DELETE FROM ptt_channel_members WHERE channelId = :channelId AND principalId = :principalId")
    suspend fun removeMember(channelId: String, principalId: String): Int

    @Query("DELETE FROM ptt_channels WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String): Int
}
