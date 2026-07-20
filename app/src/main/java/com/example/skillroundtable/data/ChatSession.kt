package com.example.skillroundtable.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
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
    @ColumnInfo(defaultValue = "0") val audioSizeBytes: Long = 0L
)

@Dao
interface ChatDao {
    // Session Queries
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateSessionTitle(id: Long, title: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Long)

    // Message Queries
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId AND isPending = 1")
    suspend fun removePendingMessages(chatId: Long)

    @Query("UPDATE messages SET audioFilePath = :path, audioFormat = :format, audioSizeBytes = :size WHERE id = :id")
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
        try {
            val messages = chatDao.getMessagesForChat(id)
            messages.forEach { msg ->
                val path = msg.audioFilePath
                if (!path.isNullOrBlank()) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    suspend fun deleteMessageById(id: Long) = chatDao.deleteMessageById(id)

    suspend fun removePendingMessages(chatId: Long) = chatDao.removePendingMessages(chatId)
}
