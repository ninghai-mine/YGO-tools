package com.smallworld.drawer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smallworld.drawer.data.DeckReader
import com.smallworld.drawer.engine.SmallWorldAnalyzer
import com.smallworld.drawer.ui.theme.BgDark
import com.smallworld.drawer.ui.theme.Gold
import com.smallworld.drawer.ui.theme.L1Colors
import com.smallworld.drawer.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 检索关系图对话框
 * 移植自 webapp.py 的 _generate_chain_graph
 */
@Composable
fun ChainGraphDialog(
    centerId: Int,
    centerName: String,
    swMap: Map<Int, SmallWorldAnalyzer.SmallWorldNode>,
    chainMap: Map<Int, SmallWorldAnalyzer.ChainNode>,
    onDismiss: () -> Unit
) {
    val centerNode = swMap[centerId] ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
                .background(BgDark, shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
        ) {
            Column {
                // 标题
                Text(
                    text = "$centerName - 检索关系图",
                    color = Gold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally)
                )

                // Canvas 画布
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    drawChainGraph(
                        centerId = centerId,
                        centerNode = centerNode,
                        chainMap = chainMap,
                        swMap = swMap
                    )
                }

                // 关闭按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("关闭", color = Gold)
                }
            }
        }
    }
}

/**
 * 在 Canvas 上绘制树状检索关系图
 * 移植自 webapp.py 的 _generate_chain_graph
 */
private fun DrawScope.drawChainGraph(
    centerId: Int,
    centerNode: SmallWorldAnalyzer.SmallWorldNode,
    chainMap: Map<Int, SmallWorldAnalyzer.ChainNode>,
    swMap: Map<Int, SmallWorldAnalyzer.SmallWorldNode>
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // 收集节点
    val level1 = centerNode.relations
    val l1Ids = level1.map { it.id }.toSet()

    val chains = chainMap[centerId]?.chains ?: emptyList()

    // level2: 不在 level1 中的检索目标
    val level2Map = mutableMapOf<Int, MutableList<SmallWorldAnalyzer.Bridge>>()
    for (ch in chains) {
        val tid = ch.targetId
        if (tid == centerId || tid in l1Ids) continue
        val list = level2Map.getOrPut(tid) { mutableListOf() }
        for (br in ch.bridges) {
            if (br.bridgeId in l1Ids) {
                list.add(br)
            }
        }
    }
    val level2 = level2Map.entries.toList()

    val nL1 = level1.size
    val nL2 = level2.size

    // ── 布局参数 ──
    val margin = 40f
    val cardW = 60f
    val cardH = cardW * (254f / 177f) // 保持卡牌比例

    val xRoot = margin + cardW * 0.5f
    val xL1 = canvasWidth * 0.42f
    val xL2 = canvasWidth * 0.78f

    val usableTop = margin
    val usableBottom = canvasHeight - margin

    fun distribute(n: Int): List<Float> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(canvasHeight / 2f)
        val step = (usableBottom - usableTop) / (n - 1).coerceAtLeast(1)
        return (0 until n).map { usableTop + it * step }
    }

    val yL1List = if (nL1 > 0) distribute(nL1) else emptyList()
    val yL2List = if (nL2 > 0) distribute(nL2) else emptyList()
    val yRoot = canvasHeight / 2f

    // 一级节点颜色
    val l1Colors = L1Colors.map { it }.toList()
    val l1ColorMap = mutableMapOf<Int, Color>()
    for ((i, rel) in level1.withIndex()) {
        l1ColorMap[rel.id] = l1Colors[i % l1Colors.size]
    }

    // ── 绘制连线 ──
    val lineColor = Gold
    val lineAlpha = 0.6f

    // 根 → 一级
    for (i in yL1List.indices) {
        val y = yL1List[i]
        drawLine(
            color = lineColor.copy(alpha = lineAlpha),
            start = Offset(xRoot + cardW * 0.4f, yRoot),
            end = Offset(xL1 - cardW * 0.4f, y),
            strokeWidth = 3f
        )
    }

    // 一级 → 二级（用对应桥梁的颜色）
    for ((idx, (tid, bridges)) in level2.withIndex()) {
        val yC = if (idx < yL2List.size) yL2List[idx] else canvasHeight / 2f
        val parents = bridges.map { it.bridgeId }.filter { it in l1Ids }

        if (parents.isNotEmpty()) {
            for (pid in parents) {
                val py = yL1List[level1.indexOfFirst { it.id == pid }.coerceAtLeast(0)]
                val edgeColor = l1ColorMap[pid] ?: Color(0xFFE74C3C)
                drawLine(
                    color = edgeColor.copy(alpha = 0.5f),
                    start = Offset(xL1 + cardW * 0.4f, py),
                    end = Offset(xL2 - cardW * 0.4f, yC),
                    strokeWidth = 2.5f
                )
            }
        } else {
            drawLine(
                color = lineColor.copy(alpha = 0.3f),
                start = Offset(xRoot + cardW * 0.4f, yRoot),
                end = Offset(xL2 - cardW * 0.4f, yC),
                strokeWidth = 2f
            )
        }
    }

    // ── 绘制节点（卡图或占位方块）──
    val borderWidth = 3f

    // 根节点
    drawCardPlaceholder(
        cx = xRoot, cy = yRoot, w = cardW * 1.2f, h = cardH * 1.2f,
        border = Gold, borderWidth = borderWidth, cardId = centerId
    )

    // 一级节点（彩色边框）
    for ((i, rel) in level1.withIndex()) {
        val y = if (i < yL1List.size) yL1List[i] else canvasHeight / 2f
        drawCardPlaceholder(
            cx = xL1, cy = y, w = cardW, h = cardH,
            border = l1ColorMap[rel.id] ?: Gold, borderWidth = borderWidth,
            cardId = rel.id
        )
    }

    // 二级节点（蓝色边框）
    for ((idx, (tid, _)) in level2.withIndex()) {
        val y = if (idx < yL2List.size) yL2List[idx] else canvasHeight / 2f
        drawCardPlaceholder(
            cx = xL2, cy = y, w = cardW, h = cardH,
            border = Color(0xFF3498DB), borderWidth = borderWidth,
            cardId = tid
        )
    }
}

/**
 * 绘制卡牌占位方块（有卡图则显示卡图）
 */
private fun DrawScope.drawCardPlaceholder(
    cx: Float, cy: Float, w: Float, h: Float,
    border: Color, borderWidth: Float,
    cardId: Int
) {
    val left = cx - w / 2
    val top = cy - h / 2

    // 尝试加载卡图
    val imgFile = DeckReader.findCardImage(cardId)
    if (imgFile?.exists() == true) {
        // 用绘制占位块替代，实际 app 中可用 ImageBitmap
        drawRect(
            color = Color(0xFF243447),
            topLeft = Offset(left, top),
            size = Size(w, h)
        )
        // 显示卡名缩写
        drawContext.canvas.nativeCanvas.drawText(
            "#$cardId", left + 4, top + h / 2,
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#667788")
                textSize = 12f
            }
        )
    } else {
        drawRect(
            color = Color(0xFF243447),
            topLeft = Offset(left, top),
            size = Size(w, h)
        )
    }

    // 边框
    drawRect(
        color = border,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = borderWidth)
    )
}
