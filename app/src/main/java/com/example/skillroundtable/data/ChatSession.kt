package com.example.skillroundtable.data

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
    val isPending: Boolean = false
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
}

class ChatRepository(private val chatDao: ChatDao) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesFlow(chatId: Long): Flow<List<Message>> = chatDao.getMessagesForChatFlow(chatId)

    suspend fun getMessages(chatId: Long): List<Message> = chatDao.getMessagesForChat(chatId)

    suspend fun getSessionById(id: Long): ChatSession? = chatDao.getSessionById(id)

    suspend fun createSession(title: String): Long {
        return chatDao.insertSession(ChatSession(title = title))
    }

    suspend fun deleteSession(id: Long) {
        chatDao.deleteSessionById(id)
        chatDao.deleteMessagesByChatId(id)
    }

    suspend fun insertMessage(message: Message): Long = chatDao.insertMessage(message)

    suspend fun deleteMessageById(id: Long) = chatDao.deleteMessageById(id)

    suspend fun removePendingMessages(chatId: Long) = chatDao.removePendingMessages(chatId)
}
