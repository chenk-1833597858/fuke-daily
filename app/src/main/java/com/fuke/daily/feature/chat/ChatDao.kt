package com.fuke.daily.feature.chat

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════
//  API 配置 DAO
// ═══════════════════════════════════════════════════

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM chat_api_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<ApiConfig>>

    @Query("SELECT * FROM chat_api_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfig(): ApiConfig?

    @Query("SELECT * FROM chat_api_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ApiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiConfig): Long

    @Update
    suspend fun updateConfig(config: ApiConfig)

    @Delete
    suspend fun deleteConfig(config: ApiConfig)

    @Query("UPDATE chat_api_configs SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE chat_api_configs SET isActive = 1 WHERE id = :id")
    suspend fun activateConfig(id: Long)
}

// ═══════════════════════════════════════════════════
//  对话 DAO
// ═══════════════════════════════════════════════════

@Dao
interface ConversationDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM chat_conversations WHERE id = :id")
    suspend fun getConversation(id: Long): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: Long, updatedAt: Long = System.currentTimeMillis())
}

// ═══════════════════════════════════════════════════
//  消息 DAO
// ═══════════════════════════════════════════════════

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessages(conversationId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesList(conversationId: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int
}
