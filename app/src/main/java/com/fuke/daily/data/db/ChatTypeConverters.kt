package com.fuke.daily.data.db

import androidx.room.TypeConverter
import com.fuke.daily.feature.chat.MessageRole

class ChatTypeConverters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(name: String): MessageRole = MessageRole.valueOf(name)
}
