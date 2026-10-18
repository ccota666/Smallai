package com.mimochat.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图片编辑：单界面集成裁剪（直接在预览图上拖框）/ 90° 旋转 / 压缩率。
 */
@Composable
fun ImageEditorDialog(
    sourcePath: String,
    onDismiss: () -> Unit,
    onSave: (newPath: String, sizeBytes: Long) -> Unit
) {
    var baseBmp by remember { mutableStateOf<Bitmap?>(null) }
    var rotQuarter by remember { mutableStateOf(0) } // 0..3, 每次 90°
    // 初始裁剪框覆盖整张图，用户再拖角/边缩小
    var cropNorm by remember { mutableStateOf<Pair<Pair<Float, Float>, Pair<Float, Float>>?>((0f to 0f) to (1f to 1f)) }
    var cropDragging by remember { mutableStateOf(false) } // (left,top) to (right,bottom) 0..1
    var quality by remember { mutableFloatStateOf(85f) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewBytes by remember { mutableStateOf(0L) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(sourcePath) {
        baseBmp = withContext(Dispatchers.IO) {
            runCatching {
                var bmp = BitmapFactory.decodeFile(sourcePath) ?: return@runCatching null
                val maxDim = 2048
                if (max(bmp.width, bmp.height) > maxDim) {
                    val scale = maxDim.toFloat() / max(bmp.width, bmp.height)
                    bmp = Bitmap.createScaledBitmap(
                        bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                    )
                }
                bmp
            }.getOrNull()
        }
    }

    // 实时预览：旋转 → 裁剪 → 压缩
    LaunchedEffect(baseBmp, rotQuarter, cropNorm, quality) {
        val src = baseBmp ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                var bmp = src
                if (rotQuarter != 0) {
                    val m = Matrix().apply { postRotate(rotQuarter * 90f) }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                val c = cropNorm
                if (c != null) {
                    val l = (c.first.first * bmp.width).toInt().coerceIn(0, bmp.width - 2)
                    val t = (c.first.second * bmp.height).toInt().coerceIn(0, bmp.height - 2)
                    val r = (c.second.first * bmp.width).toInt().coerceIn(l + 2, bmp.width)
                    val b = (c.second.second * bmp.height).toInt().coerceIn(t + 2, bmp.height)
                    if (r - l > 4 && b - t > 4) {
                        bmp = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
                    }
                }
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality.toInt(), bos)
                Pair(bmp.asImageBitmap(), bos.size().toLong())
            }.getOrNull()
        }
        if (result != null) {
            preview = result.first
            previewBytes = result.second
        }
    }

    fun currentBitmap(): Bitmap? {
        var bmp = baseBmp ?: return null
        if (rotQuarter != 0) {
            val m = Matrix().apply { postRotate(rotQuarter * 90f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val c = cropNorm
        if (c != null) {
            val l = (c.first.first * bmp.width).toInt().coerceIn(0, bmp.width - 2)
            val t = (c.first.second * bmp.height).toInt().coerceIn(0, bmp.height - 2)
            val r = (c.second.first * bmp.width).toInt().coerceIn(l + 2, bmp.width)
            val b = (c.second.second * bmp.height).toInt().coerceIn(t + 2, bmp.height)
            if (r - l > 4 && b - t > 4) {
                bmp = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
            }
        }
        return bmp
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                    Text("编辑图片", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(
                        enabled = preview != null && !saving,
                        onClick = {
                            saving = true
                            val q = quality.toInt()
                            Thread {
                                try {
                                    val outBmp = currentBitmap()
                                    val dir = File(sourcePath).parentFile ?: File("/data/local/tmp")
                                    val out = File(dir, "edited_${System.currentTimeMillis()}.jpg")
                                    out.outputStream().use { fos ->
                                        outBmp?.compress(Bitmap.CompressFormat.JPEG, q, fos)
                                    }
                                    onSave(out.absolutePath, out.length())
                                } catch (_: Exception) {
                                } finally {
                                    saving = false
                                }
                            }.start()
                        }
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 预览：裁剪直接在图上拖框
                val base = baseBmp
                // 图片区占满剩余空间，图片完整可见不被裁切
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (base == null) {
                        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        CropPreview(
                            bitmap = base,
                            rotationQuarter = rotQuarter,
                            crop = cropNorm,
                            onCropChange = { cropNorm = it },
                            onDragState = {}
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 旋转：仅 90°
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("旋转", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp))
                    OutlinedButton(
                        onClick = { rotQuarter = (rotQuarter + 3) % 4 },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.RotateLeft, null, modifier = Modifier.size(16.dp))
                        Text(" 左90°", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { rotQuarter = (rotQuarter + 1) % 4 },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.RotateRight, null, modifier = Modifier.size(16.dp))
                        Text(" 右90°", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { rotQuarter = 0; cropNorm = null },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("重置", style = MaterialTheme.typography.labelSmall) }
                }

                Spacer(Modifier.height(6.dp))

                // 压缩率
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("压缩率", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${quality.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(40.dp)
                    )
                }
                Text(
                    "约 ${formatBytes(previewBytes)} · 拖角/边调整裁剪框，拖内部移动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!cropDragging) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 预览 + 裁剪框（手势层盖在图片上，确保可拖）：
 * - 无选区：在图上拖动 -> 新建选区
 * - 有选区：拖内部 -> 移动；拖边/角 -> 调整
 */
@Composable
private fun CropPreview(
    bitmap: Bitmap,
    rotationQuarter: Int,
    crop: Pair<Pair<Float, Float>, Pair<Float, Float>>?,
    onCropChange: (Pair<Pair<Float, Float>, Pair<Float, Float>>?) -> Unit,
    onDragState: (Boolean) -> Unit = {}
) {
    val rotatedW = if (rotationQuarter % 2 == 0) bitmap.width else bitmap.height
    val rotatedH = if (rotationQuarter % 2 == 0) bitmap.height else bitmap.width
    val aspect = rotatedW.toFloat() / rotatedH.toFloat()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var cropAtStart by remember { mutableStateOf(crop) }
    // gesture 每帧读最新 crop，避免 keyed restart 导致拖一点点就断
    val cropState = remember { androidx.compose.runtime.mutableStateOf(crop) }
    androidx.compose.runtime.LaunchedEffect(crop) { cropState.value = crop }

    val dispBmp = remember(bitmap, rotationQuarter) {
        if (rotationQuarter == 0) bitmap.asImageBitmap()
        else {
            val m = Matrix().apply { postRotate(rotationQuarter * 90f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                .asImageBitmap()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Image(dispBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        // 透明手势层：确保拿到所有拖拽
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            onDragState(true)
                            val nx = pos.x / size.width
                            val ny = pos.y / size.height
                            dragStart = Offset(nx, ny)
                            cropAtStart = crop
                            val cur = cropState.value
                            val kind = hitTest(nx, ny, cur)
                            dragging = kind
                            cropAtStart = cur
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = change.position.x / size.width
                            val ny = change.position.y / size.height
                            val dx = nx - dragStart.x
                            val dy = ny - dragStart.y
                            val st = cropAtStart
                            when (dragging) {
                                "Move" -> if (st != null) {
                                    val w = st.second.first - st.first.first
                                    val h = st.second.second - st.first.second
                                    val l = (st.first.first + dx).coerceIn(0f, 1f - w)
                                    val t = (st.first.second + dy).coerceIn(0f, 1f - h)
                                    onCropChange((l to t) to (l + w to t + h))
                                }
                                "N" -> if (st != null) {
                                    val t = (st.first.second + dy).coerceIn(0f, st.second.second - 0.03f)
                                    onCropChange((st.first.first to t) to st.second)
                                }
                                "S" -> if (st != null) {
                                    val b = (st.second.second + dy).coerceIn(st.first.second + 0.03f, 1f)
                                    onCropChange(st.first to (st.second.first to b))
                                }
                                "W" -> if (st != null) {
                                    val l = (st.first.first + dx).coerceIn(0f, st.second.first - 0.03f)
                                    onCropChange((l to st.first.second) to st.second)
                                }
                                "E" -> if (st != null) {
                                    val r = (st.second.first + dx).coerceIn(st.first.first + 0.03f, 1f)
                                    onCropChange(st.first to (r to st.second.second))
                                }
                                "NW" -> if (st != null) {
                                    val l = (st.first.first + dx).coerceIn(0f, st.second.first - 0.03f)
                                    val t = (st.first.second + dy).coerceIn(0f, st.second.second - 0.03f)
                                    onCropChange((l to t) to st.second)
                                }
                                "NE" -> if (st != null) {
                                    val r = (st.second.first + dx).coerceIn(st.first.first + 0.03f, 1f)
                                    val t = (st.first.second + dy).coerceIn(0f, st.second.second - 0.03f)
                                    onCropChange((st.first.first to t) to (r to st.second.second))
                                }
                                "SW" -> if (st != null) {
                                    val l = (st.first.first + dx).coerceIn(0f, st.second.first - 0.03f)
                                    val b = (st.second.second + dy).coerceIn(st.first.second + 0.03f, 1f)
                                    onCropChange((l to st.first.second) to (st.second.first to b))
                                }
                                "SE" -> if (st != null) {
                                    val r = (st.second.first + dx).coerceIn(st.first.first + 0.03f, 1f)
                                    val b = (st.second.second + dy).coerceIn(st.first.second + 0.03f, 1f)
                                    onCropChange(st.first to (r to b))
                                }
                                else -> Unit
                            }
                        },
                        onDragEnd = { dragging = null; onDragState(false) },
                        onDragCancel = { dragging = null; onDragState(false) }
                    )
                }
        )

        // 选区蒙层 + 边框
        Canvas(Modifier.fillMaxSize()) {
            val c = crop ?: return@Canvas
            val left = c.first.first * size.width
            val top = c.first.second * size.height
            val right = c.second.first * size.width
            val bottom = c.second.second * size.height
            drawRect(Color.Black.copy(alpha = 0.42f), Offset(0f, 0f), Size(size.width, top))
            drawRect(Color.Black.copy(alpha = 0.42f), Offset(0f, bottom), Size(size.width, size.height - bottom))
            drawRect(Color.Black.copy(alpha = 0.42f), Offset(0f, top), Size(left, bottom - top))
            drawRect(Color.Black.copy(alpha = 0.42f), Offset(right, top), Size(size.width - right, bottom - top))
            drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top),
                style = Stroke(width = 2.5.dp.toPx()))
            val w = right - left
            val h = bottom - top
            for (i in 1..2) {
                drawLine(Color.White.copy(alpha = 0.5f),
                    Offset(left + w * i / 3, top), Offset(left + w * i / 3, bottom), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f),
                    Offset(left, top + h * i / 3), Offset(right, top + h * i / 3), 1.dp.toPx())
            }
            val hs = 14.dp.toPx()
            listOf(
                Offset(left, top), Offset(right, top),
                Offset(left, bottom), Offset(right, bottom),
                Offset((left + right) / 2, top), Offset((left + right) / 2, bottom),
                Offset(left, (top + bottom) / 2), Offset(right, (top + bottom) / 2)
            ).forEach { o ->
                drawRect(Color.White, Offset(o.x - hs / 2, o.y - hs / 2), Size(hs, hs))
            }
        }
    }
}

private fun hitTest(
    nx: Float,
    ny: Float,
    crop: Pair<Pair<Float, Float>, Pair<Float, Float>>?
): String {
    if (crop == null) return "Move"
    val (lt, rb) = crop
    val edge = 0.05f
    val nearL = kotlin.math.abs(nx - lt.first) < edge
    val nearR = kotlin.math.abs(nx - rb.first) < edge
    val nearT = kotlin.math.abs(ny - lt.second) < edge
    val nearB = kotlin.math.abs(ny - rb.second) < edge
    val inside = nx > lt.first && nx < rb.first && ny > lt.second && ny < rb.second
    return when {
        nearL && nearT -> "NW"
        nearR && nearT -> "NE"
        nearL && nearB -> "SW"
        nearR && nearB -> "SE"
        nearL -> "W"
        nearR -> "E"
        nearT -> "N"
        nearB -> "S"
        inside -> "Move"
        else -> "Move"
    }
}

private fun abs(v: Float) = if (v < 0) -v else v

private fun formatBytes(b: Long): String = when {
    b >= 1024 * 1024 -> "%.2f MB".format(b / 1024.0 / 1024.0)
    b >= 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}
