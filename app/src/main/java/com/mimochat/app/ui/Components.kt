package com.mimochat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 统一视觉常量：全站圆角与控件尺寸 */
object AppDimens {
    val iconButton = 40.dp
    val iconSize = 22.dp
    val iconSizeSmall = 18.dp
    val shapeSm = 12.dp
    val shapeMd = 14.dp
    val shapeLg = 20.dp
    val shapePill = 24.dp
    val sliderTrack = 8.dp
    val sliderThumb = 18.dp
}

/** 透明背景图标按钮 — 顶栏菜单/设置/关闭等。 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(AppDimens.iconButton),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent)
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(AppDimens.iconSize))
    }
}

/** 主操作图标按钮 — 圆角矩形填充（发送等），与胶囊输入栏同一形状语言。 */
@Composable
fun AppFilledIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(AppDimens.iconButton)
            .clip(RoundedCornerShape(AppDimens.shapeMd)),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(AppDimens.iconSize))
    }
}

/** 停止按钮 — 同样圆角矩形，错误色系。 */
@Composable
fun AppStopIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(AppDimens.iconButton)
            .clip(RoundedCornerShape(AppDimens.shapeMd)),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(Icons.Rounded.StopCircle, "停止", modifier = Modifier.size(AppDimens.iconSize))
    }
}

/** 全宽主按钮 — 「新对话」等。 */
@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(AppDimens.shapeMd),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(AppDimens.iconSizeSmall))
            Spacer(Modifier.size(8.dp))
        }
        Text(text)
    }
}

/** 全宽次按钮 — 与主按钮同高同圆角。 */
@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(AppDimens.shapeMd),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(AppDimens.iconSizeSmall))
            Spacer(Modifier.size(8.dp))
        }
        Text(text)
    }
}

/**
 * 现代滑块 — 主色轨道、大号拇指、无刻度噪点。
 * （Material3 Track API 各版本差异大，这里用 colors 保证兼容与观感）
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        steps = steps,
        colors = colors,
        modifier = modifier
    )
}

/** 胶囊背景容器（输入栏 / 搜索框 / 模型选择）。 */
@Composable
fun AppPillBackground(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.shapePill))
            .background(
                containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
