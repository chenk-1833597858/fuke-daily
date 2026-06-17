package com.fuke.daily.ui.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.components.PageHeader
import com.fuke.daily.ui.theme.FukeTheme

// ═══════════════════════════════════════════════════
//  记忆卡片数据
// ═══════════════════════════════════════════════════

data class MemoryCard(
    val id: Long,
    val front: String,
    val back: String,
)

// ═══════════════════════════════════════════════════
//  记忆卡片页 — Anki 风格
// ═══════════════════════════════════════════════════

@Composable
fun MemoryCardScreen(
    listId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    // ── Mock 数据 ──
    val mockCards = remember {
        listOf(
            MemoryCard(1L, "什么是 Kotlin？", "Kotlin 是一种在 Java 虚拟机上运行的静态类型编程语言。"),
            MemoryCard(2L, "Compose 的核心概念是什么？", "声明式 UI：用 Kotlin 代码描述界面状态，框架自动更新。"),
            MemoryCard(3L, "什么是 Room 数据库？", "Room 是 Android 的 ORM 库，在 SQLite 上提供了抽象层。"),
            MemoryCard(4L, "Hilt 的作用是什么？", "Hilt 是 Dagger 的封装，简化 Android 依赖注入。"),
            MemoryCard(5L, "StateFlow 和 LiveData 的区别？", "StateFlow 需要 初始值，支持 Flow 操作符；LiveData 有生命周期感知。"),
        )
    }

    // ── 卡片池状态 ──
    // currentPool: 当前轮待复习的卡片
    // nextRoundPool: 下一轮待复习的卡片（"模糊"放入）
    // round: 当前轮次
    var currentPool by remember { mutableStateOf(mockCards.shuffled()) }
    var nextRoundPool by remember { mutableStateOf(mutableListOf<MemoryCard>()) }
    var round by remember { mutableIntStateOf(1) }

    // ── 翻面状态 ──
    var isFlipped by remember { mutableStateOf(false) }

    val totalCards = mockCards.size
    val isAllDone = currentPool.isEmpty()

    // 当前卡片（池顶）
    val currentCard = currentPool.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extended.bg),
    ) {
        // ── 顶栏 ──
        PageHeader(
            title = "记忆卡片",
            onBack = onBack,
        )

        if (isAllDone) {
            // ── 空态 ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "🎉",
                        fontSize = 48.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "所有卡片已记住！",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = extended.text,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 如果还有下一轮的卡片，显示继续按钮
                    if (nextRoundPool.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    currentPool = nextRoundPool.shuffled()
                                    nextRoundPool = mutableListOf()
                                    round++
                                    isFlipped = false
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = extended.muted,
                        ) {
                            Text(
                                text = "开始下一轮 (${nextRoundPool.size}张)",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        } else {
            // ── 卡片区域 ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 翻转动画
                val rotationY by animateFloatAsState(
                    targetValue = if (isFlipped) 180f else 0f,
                    animationSpec = tween(durationMillis = 400),
                    label = "cardFlip",
                )

                // 卡片正面（rotationY 0~90 可见）
                // 卡片背面（rotationY 90~180 可见，需要水平翻转）
                val showFront = rotationY < 90f

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .graphicsLayer {
                            this.rotationY = rotationY
                            this.cameraDistance = 12f * density
                        }
                        .clickable { isFlipped = !isFlipped },
                    shape = RoundedCornerShape(20.dp),
                    color = extended.card,
                    shadowElevation = 4.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showFront) {
                            // 正面
                            Text(
                                text = currentCard!!.front,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = extended.text,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp,
                            )
                        } else {
                            // 背面（需要水平翻转，因为graphicsLayer翻过来后文字会镜像）
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    this.scaleX = -1f
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = currentCard!!.back,
                                    fontSize = 16.sp,
                                    color = extended.text,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp,
                                )
                            }
                        }
                    }
                }

                // 点击提示
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (!isFlipped) "点击卡片查看答案" else "点击卡片翻回正面",
                    fontSize = 12.sp,
                    color = extended.muted.copy(alpha = 0.5f),
                )

                // ── 翻面后自评按钮 ──
                if (isFlipped) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // 忘记 → 放回当前池末尾
                        SelfEvalButton(
                            label = "忘记",
                            color = Color(0xFFEF4444),
                            onClick = {
                                val card = currentPool.first()
                                currentPool = currentPool.drop(1) + card
                                isFlipped = false
                            },
                        )

                        // 模糊 → 放入下一轮
                        SelfEvalButton(
                            label = "模糊",
                            color = Color(0xFFF59E0B),
                            onClick = {
                                val card = currentPool.first()
                                nextRoundPool.add(card)
                                currentPool = currentPool.drop(1)
                                isFlipped = false
                            },
                        )

                        // 记住 → 移除
                        SelfEvalButton(
                            label = "记住",
                            color = Color(0xFF22C55E),
                            onClick = {
                                currentPool = currentPool.drop(1)
                                isFlipped = false
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── 底部状态 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "第${round}轮 · 剩余${currentPool.size}张 / 共${totalCards}张",
                        fontSize = 13.sp,
                        color = extended.muted.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  自评按钮
// ═══════════════════════════════════════════════════

@Composable
private fun SelfEvalButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(96.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}
