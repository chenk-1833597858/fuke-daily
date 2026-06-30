package com.fuke.daily.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

// ═══════════════════════════════════════════════════
//  列表类型
// ═══════════════════════════════════════════════════

enum class ListType { SELECTION, RANDOM, QUIZ, MAINLINE }

// Room 类型转换器（ListType ↔ String）
class ListTypeConverters {
    @TypeConverter
    fun fromListType(value: ListType): String = value.name

    @TypeConverter
    fun toListType(value: String): ListType = ListType.valueOf(value)
}

// ═══════════════════════════════════════════════════
//  主列表
// ═══════════════════════════════════════════════════

@Entity(tableName = "main_lists")
data class MainList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: ListType,
    val isEnabled: Boolean = true,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// ═══════════════════════════════════════════════════
//  子列表
// ═══════════════════════════════════════════════════

@Entity(tableName = "sub_lists")
data class SubList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentListId: Long,
    val name: String = "",
    val fixedSlot: Int = 0,          // 随机列表固定槽位（0=无）
    val imageUri: String? = null,
    val imageEnabled: Boolean = true,
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  内容配置（悬浮窗3条输入+3个引用槽）
// ═══════════════════════════════════════════════════

@Entity(tableName = "content_configs", primaryKeys = ["subListId", "parentListId"])
data class ContentConfig(
    val subListId: Long,
    val parentListId: Long,
    val input1Text: String = "",
    val button1Storage: Int = 0,     // 0=无, 1-5=槽位
    val input1TextColor: String = "",  // input1文本颜色
    val input1RefColor: String = "",   // input1引用颜色
    val input2Text: String = "",
    val button2Storage: Int = 0,
    val input2TextColor: String = "",  // input2文本颜色
    val input2RefColor: String = "",   // input2引用颜色
    val input3Text: String = "",
    val button3Storage: Int = 0,
    val input3TextColor: String = "",  // input3文本颜色
    val input3RefColor: String = "",   // input3引用颜色
)

// ═══════════════════════════════════════════════════
//  选项按钮
// ═══════════════════════════════════════════════════

@Entity(tableName = "option_buttons")
data class OptionButton(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subListId: Long,
    val parentListId: Long,
    val name: String = "",
    val jumpTo: Int = 0,             // 跳转子列表序号，0=不跳转
    val storageSlot: Int = 0,        // 0=无, 1-5=存储槽位
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  富文本（随机列表专用）
// ═══════════════════════════════════════════════════

@Entity(tableName = "rich_texts")
data class RichText(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentListId: Long,
    val name: String = "",
    val content: String = "",        // 换行分隔的文本
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  主线支线
// ═══════════════════════════════════════════════════

@Entity(tableName = "mainline_branches")
data class MainlineBranch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentListId: Long,
    val name: String = "",
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  主线支线子项
// ═══════════════════════════════════════════════════

@Entity(tableName = "mainline_items")
data class MainlineItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val branchId: Long,
    val name: String = "",
    val isCurrent: Boolean = false,   // 是否为"现状"（挂在路标下但作为现状显示）
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  答题组
// ═══════════════════════════════════════════════════

@Entity(tableName = "quiz_groups")
data class QuizGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentListId: Long,
    val name: String = "",
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  答题卡片
// ═══════════════════════════════════════════════════

@Entity(tableName = "quiz_cards")
data class QuizCard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val front: String = "",          // 问题
    val back: String = "",           // 答案
    val sortOrder: Int = 0,
)

// ═══════════════════════════════════════════════════
//  链路选择记录
// ═══════════════════════════════════════════════════

@Entity(tableName = "link_history")
data class LinkRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,                // JSON: [{id,name},...]
    val timestamp: Long,
    val date: String,                // 日期标签 yyyy-MM-dd
)

// ═══════════════════════════════════════════════════
//  存储数据（内存态，不持久化到独立表）
// ═══════════════════════════════════════════════════

data class StorageData(
    val slot1: String = "",
    val slot2: String = "",
    val slot3: String = "",
    val slot4: String = "",
    val slot5: String = "",
) {
    fun getSlot(index: Int): String = when (index) {
        1 -> slot1; 2 -> slot2; 3 -> slot3; 4 -> slot4; 5 -> slot5; else -> ""
    }

    fun setSlot(index: Int, value: String): StorageData = when (index) {
        1 -> copy(slot1 = value)
        2 -> copy(slot2 = value)
        3 -> copy(slot3 = value)
        4 -> copy(slot4 = value)
        5 -> copy(slot5 = value)
        else -> this
    }
}

// ═══════════════════════════════════════════════════
//  主线配置（存 DataStore）
// ═══════════════════════════════════════════════════

data class MainlineConfig(
    val morningHour: Int = 0,        // 上午选择时段
    val eveningHour: Int = 21,       // 晚间选择时段
    val lastMorningDate: String = "",  // 上次上午选择日期
    val lastEveningDate: String = "",  // 上次晚间选择日期
)
