package com.fuke.daily.feature.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════
//  OpenAI 兼容 API 请求/响应格式
// ═══════════════════════════════════════════════════

data class ChatRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean = true,
    val max_tokens: Int = 4096,
    val temperature: Float = 0.7f,
    val tools: List<ToolSchema>? = null,
)

data class ChatRequestMessage(
    val role: String,
    val content: String,
    @SerializedName("tool_calls") val toolCalls: List<ToolCallData>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
)

data class ToolSchema(
    val type: String = "function",
    val function: FunctionSchema,
)

data class FunctionSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

data class ToolCallData(
    val id: String,
    val type: String = "function",
    val function: FunctionCallData,
)

data class FunctionCallData(
    val name: String,
    val arguments: String,
)

// ═══════════════════════════════════════════════════
//  API 客户端
// ═══════════════════════════════════════════════════

class ChatApiClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 流式聊天，返回 Flow<StreamChunk>
     * 逐字输出AI回复，支持工具调用
     */
    fun streamChat(request: ChatRequest): Flow<StreamChunk> = flow {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val body = gson.toJson(request).toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            emit(StreamChunk(content = "❌ API错误 (${response.code}): $errorBody", isDone = true))
            return@flow
        }

        val reader = response.body?.byteStream()?.bufferedReader() ?: run {
            emit(StreamChunk(content = "❌ 响应为空", isDone = true))
            return@flow
        }

        // 工具调用累积器
        val toolCallAccumulators = mutableMapOf<Int, ToolCallAccumulator>()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue

                // SSE格式：data: {...}
                if (!currentLine.startsWith("data: ")) continue
                val data = currentLine.removePrefix("data: ").trim()

                // 结束标记
                if (data == "[DONE]") {
                    // 如果有未完成的工具调用，先输出
                    val completedToolCalls = toolCallAccumulators.values.map { it.build() }
                    if (completedToolCalls.isNotEmpty()) {
                        emit(StreamChunk(toolCalls = completedToolCalls.map { tc ->
                            ToolCallChunk(
                                index = tc.index,
                                id = tc.id,
                                name = tc.name,
                                arguments = tc.arguments,
                            )
                        }))
                    }
                    emit(StreamChunk(isDone = true))
                    break
                }

                // 解析JSON
                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices == null || choices.size() == 0) continue

                    val choice = choices[0].asJsonObject
                    val delta = choice.getAsJsonObject("delta")

                    // 文本内容
                    val content = delta?.get("content")?.asString ?: ""

                    // 工具调用
                    val toolCallsArray = delta?.getAsJsonArray("tool_calls")
                    if (toolCallsArray != null) {
                        for (tcElement in toolCallsArray) {
                            val tc = tcElement.asJsonObject
                            val index = tc.get("index")?.asInt ?: 0
                            val accumulator = toolCallAccumulators.getOrPut(index) {
                                ToolCallAccumulator(index = index)
                            }
                            tc.get("id")?.asString?.let { accumulator.id = it }
                            tc.getAsJsonObject("function")?.get("name")?.asString?.let {
                                accumulator.name = it
                            }
                            tc.getAsJsonObject("function")?.get("arguments")?.asString?.let {
                                accumulator.arguments += it
                            }
                        }
                    }

                    // 检查finish_reason
                    val finishReason = choice.get("finish_reason")?.asString
                    val isDone = finishReason == "stop" || finishReason == "tool_calls"

                    if (content.isNotEmpty()) {
                        emit(StreamChunk(content = content))
                    }

                    if (isDone && finishReason == "tool_calls") {
                        // 工具调用完成，输出累积的工具调用
                        val completedToolCalls = toolCallAccumulators.values.map { it.build() }
                        emit(StreamChunk(
                            toolCalls = completedToolCalls.map { tc ->
                                ToolCallChunk(
                                    index = tc.index,
                                    id = tc.id,
                                    name = tc.name,
                                    arguments = tc.arguments,
                                )
                            },
                            isDone = true,
                        ))
                    } else if (isDone) {
                        emit(StreamChunk(isDone = true))
                    }
                } catch (e: Exception) {
                    // 解析失败，跳过这行
                    continue
                }
            }
        } finally {
            reader.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 非流式聊天（用于简单测试）
     */
    suspend fun chat(request: ChatRequest): String = withContext(Dispatchers.IO) {
        val nonStreamRequest = request.copy(stream = false)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val body = gson.toJson(nonStreamRequest).toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(httpRequest).execute()
        response.body?.string() ?: "❌ 响应为空"
    }

    // 工具调用累积器（SSE分多个chunk发送一个工具调用）
    private data class ToolCallAccumulator(
        val index: Int,
        var id: String = "",
        var name: String = "",
        var arguments: String = "",
    ) {
        fun build() = ToolCallAccumulator(index, id, name, arguments)
    }
}

// ═══════════════════════════════════════════════════
//  工具注册表
// ═══════════════════════════════════════════════════

/**
 * 工具注册表，管理AI可调用的所有工具。
 * 用法：
 *   1. 在App启动时注册工具：ToolRegistry.register(ToolDefinition(...))
 *   2. 生成API请求时获取schema：ToolRegistry.getSchemas()
 *   3. AI返回工具调用时执行：ToolRegistry.execute(name, args)
 */
object ToolRegistry {
    private val tools = mutableMapOf<String, ToolDefinition>()

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
    }

    fun get(name: String): ToolDefinition? = tools[name]

    fun getSchemas(): List<ToolSchema> = tools.values.map { tool ->
        ToolSchema(
            function = FunctionSchema(
                name = tool.name,
                description = tool.description,
                parameters = try {
                    gson.fromJson(tool.parameters, Map::class.java) as Map<String, Any>
                } catch (e: Exception) {
                    emptyMap()
                }
            )
        )
    }

    fun execute(name: String, args: Map<String, Any>): String {
        val tool = tools[name] ?: return "错误：未知工具 $name"
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            "工具执行错误：${e.message}"
        }
    }

    fun hasTools(): Boolean = tools.isNotEmpty()

    private val gson = Gson()
}
