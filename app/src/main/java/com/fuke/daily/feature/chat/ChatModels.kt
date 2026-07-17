package com.fuke.daily.feature.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ═══════════════════════════════════════════════════
//  API 配置
// ═══════════════════════════════════════════════════

@Entity(tableName = "chat_api_configs")
data class ApiConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 配置名称（如"DeepSeek"、"OpenAI"）
    val baseUrl: String,                 // API基础URL（如 https://api.deepseek.com/v1）
    val apiKey: String,                  // 用户的API Key
    val model: String,                   // 模型名（如 deepseek-chat）
    val isActive: Boolean = false,       // 当前是否激活
    val maxTokens: Int = 4096,           // 最大输出token
    val temperature: Float = 0.7f,       // 温度
    val systemPrompt: String = "",       // 系统提示词
    val createdAt: Long = System.currentTimeMillis(),
)

// ═══════════════════════════════════════════════════
//  对话（一个对话=一组消息）
// ═══════════════════════════════════════════════════

@Entity(tableName = "chat_conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "新对话",         // 对话标题
    val apiConfigId: Long = 0,           // 关联的API配置
    val systemPrompt: String = "",       // 对话级系统提示词（覆盖API配置的）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// ═══════════════════════════════════════════════════
//  聊天消息
// ═══════════════════════════════════════════════════

enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,                 // 消息文本内容
    val toolCalls: String = "",          // JSON: AI请求的工具调用列表
    val toolCallId: String = "",         // 工具调用结果对应的ID
    val isStreaming: Boolean = false,     // 是否正在流式输出中
    val createdAt: Long = System.currentTimeMillis(),
)

// ═══════════════════════════════════════════════════
//  工具定义（AI可调用的函数）
// ═══════════════════════════════════════════════════

/**
 * 工具定义，描述AI可以调用的函数。
 * 存储在代码中，不存数据库（工具是App功能，不是用户数据）。
 */
data class ToolDefinition(
    val name: String,                    // 函数名
    val description: String,             // 给AI看的描述
    val parameters: String,              // JSON Schema格式的参数定义
    val execute: (Map<String, Any>) -> String,  // 执行函数，返回结果文本
)

// ═══════════════════════════════════════════════════
//  SSE 流式响应数据
// ═══════════════════════════════════════════════════

data class StreamChunk(
    val content: String = "",            // 文本增量
    val toolCalls: List<ToolCallChunk> = emptyList(),  // 工具调用增量
    val isDone: Boolean = false,         // 是否结束
)

data class ToolCallChunk(
    val index: Int,                      // 第几个工具调用
    val id: String = "",                 // 工具调用ID（只有第一个chunk有）
    val name: String = "",               // 函数名（只有第一个chunk有）
    val arguments: String = "",          // 参数JSON增量
)
