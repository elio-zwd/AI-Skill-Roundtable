package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.elio.jianyu.telemetry.InteractionChainStore
import com.elio.jianyu.telemetry.PrivacySafeLogger
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id", "issueId", "stageId"],
            childColumns = ["executionRunId", "issueId", "stageId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionParticipantSnapshotEntity::class,
            parentColumns = ["id", "runId"],
            childColumns = ["participantSnapshotId", "executionRunId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["id", "issueId", "stageId"], unique = true),
        Index(value = ["issueId"]),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["executionRunId", "issueId", "stageId"]),
        Index(value = ["participantSnapshotId", "executionRunId"])
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val senderId: String, // "user" or character id
    val senderName: String,
    val avatar: String, // Emoji or icon code
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    @ColumnInfo(defaultValue = "0") val roundIndex: Int = 0,
    val audioFilePath: String? = null,
    val audioFormat: String? = null,
    @ColumnInfo(defaultValue = "0") val audioSizeBytes: Long = 0L,
    val issueId: String? = null,
    val stageId: String? = null,
    val executionRunId: String? = null,
    val participantSnapshotId: String? = null
)

@Dao
interface ChatDao {
    @Query(
        "SELECT * FROM chat_sessions WHERE id NOT IN (" +
            "SELECT legacyChatSessionId FROM issues " +
            "WHERE legacyChatSessionId IS NOT NULL AND id NOT LIKE 'legacy-chat-%'" +
            ") ORDER BY createdAt DESC"
    )
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Query(
        "SELECT EXISTS(SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = :sessionId AND id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun isDomainCompatibilitySession(sessionId: Long): Boolean

    @Query(
        "DELETE FROM chat_sessions WHERE id = :id AND NOT EXISTS (" +
            "SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = :id AND id NOT LIKE 'legacy-chat-%'" +
            ")"
    )
    suspend fun deleteSessionById(id: Long)

    @Query(
        "UPDATE chat_sessions SET title = :title WHERE id = :id AND NOT EXISTS (" +
            "SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = :id AND id NOT LIKE 'legacy-chat-%'" +
            ")"
    )
    suspend fun updateSessionTitle(id: Long, title: String)

    @Query(
        "DELETE FROM messages WHERE chatId = :chatId AND NOT EXISTS (" +
            "SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = :chatId AND id NOT LIKE 'legacy-chat-%'" +
            ")"
    )
    suspend fun deleteMessagesByChatId(chatId: Long)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query(
        "UPDATE messages SET text = :text WHERE id = :id AND isPending = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = messages.chatId " +
            "AND issues.id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun updatePendingMessageText(id: Long, text: String)

    @Query(
        "DELETE FROM messages WHERE id = :id AND NOT EXISTS (" +
            "SELECT 1 FROM issues WHERE legacyChatSessionId = messages.chatId " +
            "AND issues.id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun deleteMessageById(id: Long)

    @Query(
        "DELETE FROM messages WHERE chatId = :chatId AND isPending = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = messages.chatId " +
            "AND issues.id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun removePendingMessages(chatId: Long)

    @Query(
        "DELETE FROM messages WHERE isPending = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = messages.chatId " +
            "AND issues.id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun removeAllPendingMessages()

    @Query(
        "UPDATE messages SET audioFilePath = :path, audioFormat = :format, " +
            "audioSizeBytes = :size WHERE id = :id AND NOT EXISTS (" +
            "SELECT 1 FROM issues WHERE legacyChatSessionId = messages.chatId " +
            "AND issues.id NOT LIKE 'legacy-chat-%')"
    )
    suspend fun updateMessageAudio(id: Long, path: String?, format: String?, size: Long)

    @Query("SELECT * FROM messages WHERE audioFilePath IS NOT NULL AND audioFilePath != '' ORDER BY timestamp DESC")
    fun getAudioMessagesFlow(): Flow<List<Message>>
}

class ChatRepository(private val chatDao: ChatDao) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    val audioMessages: Flow<List<Message>> = chatDao.getAudioMessagesFlow()

    fun getMessagesFlow(chatId: Long): Flow<List<Message>> = chatDao.getMessagesForChatFlow(chatId)

    suspend fun getMessages(chatId: Long): List<Message> = chatDao.getMessagesForChat(chatId)

    suspend fun getSessionById(id: Long): ChatSession? = chatDao.getSessionById(id)

    suspend fun createSession(title: String): Long {
        return chatDao.insertSession(ChatSession(title = title))
    }

    suspend fun deleteSession(id: Long) {
        if (chatDao.isDomainCompatibilitySession(id)) {
            PrivacySafeLogger.w(
                "ChatRepository",
                "Skipped deletion of domain compatibility session"
            )
            return
        }
        InteractionChainStore.clearSession(id)
        try {
            val messages = chatDao.getMessagesForChat(id)
            messages.forEach { message ->
                val path = message.audioFilePath
                if (!path.isNullOrBlank()) {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                }
            }
        } catch (error: Exception) {
            PrivacySafeLogger.e("ChatRepository", "Session audio cleanup failed", error)
        }
        chatDao.deleteSessionById(id)
        chatDao.deleteMessagesByChatId(id)
    }

    suspend fun updateSessionTitle(id: Long, title: String) {
        chatDao.updateSessionTitle(id, title)
    }

    suspend fun updateMessageAudio(id: Long, path: String?, format: String?, size: Long) {
        chatDao.updateMessageAudio(id, path, format, size)
    }

    suspend fun insertMessage(message: Message): Long = chatDao.insertMessage(message)

    suspend fun updatePendingMessageText(id: Long, text: String) {
        chatDao.updatePendingMessageText(id, text)
    }

    suspend fun deleteMessageById(id: Long) = chatDao.deleteMessageById(id)

    suspend fun removePendingMessages(chatId: Long) = chatDao.removePendingMessages(chatId)

    suspend fun removeAllPendingMessages() = chatDao.removeAllPendingMessages()
}
