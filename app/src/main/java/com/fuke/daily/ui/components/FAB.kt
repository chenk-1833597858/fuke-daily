package com.fuke.daily.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 悬浮创建按钮
 *
 * @param onClick 点击回调
 */
@Composable
fun FAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Box(
        modifier = modifier.padding(end = 20.dp, bottom = 20.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            containerColor = extended.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "创建",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
