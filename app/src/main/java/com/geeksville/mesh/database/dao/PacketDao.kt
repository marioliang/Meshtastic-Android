package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Update
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND port_num = :portNum
    ORDER BY received_time ASC
    """
    )
    fun getAllPackets(portNum: Int): Flow<List<Packet>>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND port_num = 1
    ORDER BY received_time DESC
    """
    )
    fun getContactKeys(): Flow<Map<@MapColumn(columnName = "contact_key") String, Packet>>

    @Query(
        """
    SELECT COUNT(*) FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND port_num = 1 AND contact_key = :contact
    """
    )
    suspend fun getMessageCount(contact: String): Int

    @Insert
    fun insert(packet: Packet)

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND port_num = 1 AND contact_key = :contact
    ORDER BY received_time ASC
    """
    )
    fun getMessagesFrom(contact: String): Flow<List<Packet>>

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND data = :data
    """
    )
    fun findDataPacket(data: DataPacket): Packet?

    @Query("DELETE FROM packet WHERE uuid in (:uuidList)")
    fun deleteMessages(uuidList: List<Long>)

    @Query(
        """
    DELETE FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND contact_key IN (:contactList)
    """
    )
    fun deleteContacts(contactList: List<String>)

    @Query("DELETE FROM packet WHERE uuid=:uuid")
    fun _delete(uuid: Long)

    @Transaction
    fun delete(packet: Packet) {
        _delete(packet.uuid)
    }

    @Update
    fun update(packet: Packet)

    @Transaction
    fun updateMessageStatus(data: DataPacket, m: MessageStatus) {
        val new = data.copy(status = m)
        findDataPacket(data)?.let { update(it.copy(data = new)) }
    }

    @Transaction
    fun updateMessageId(data: DataPacket, id: Int) {
        val new = data.copy(id = id)
        findDataPacket(data)?.let { update(it.copy(data = new)) }
    }

    @Query(
        """
    SELECT data FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
    ORDER BY received_time ASC
    """
    )
    fun getDataPackets(): List<DataPacket>

    @Transaction
    fun getDataPacketById(requestId: Int): DataPacket? {
        return getDataPackets().lastOrNull { it.id == requestId }
    }

    @Transaction
    fun getQueuedPackets(): List<DataPacket>? =
        getDataPackets().filter { it.status == MessageStatus.QUEUED }

    @Query(
        """
    SELECT * FROM packet
    WHERE (myNodeNum = 0 OR myNodeNum = (SELECT myNodeNum FROM MyNodeInfo))
        AND port_num = 8
    ORDER BY received_time ASC
    """
    )
    fun getAllWaypoints(): List<Packet>

    @Transaction
    fun deleteWaypoint(id: Int) {
        val uuidList = getAllWaypoints().filter { it.data.waypoint?.id == id }.map { it.uuid }
        deleteMessages(uuidList)
    }

    @Query("SELECT * FROM contact_settings")
    fun getContactSettings(): Flow<Map<@MapColumn(columnName = "contact_key") String, ContactSettings>>

    @Query("SELECT * FROM contact_settings WHERE contact_key = :contact")
    suspend fun getContactSettings(contact:String): ContactSettings?

    @Upsert
    fun upsertContactSettings(contacts: List<ContactSettings>)

    @Transaction
    suspend fun setMuteUntil(contacts: List<String>, until: Long) {
        val contactList = contacts.map { contact ->
            getContactSettings(contact)?.copy(muteUntil = until)
                ?: ContactSettings(contact_key = contact, muteUntil = until)
        }
        upsertContactSettings(contactList)
    }
}
