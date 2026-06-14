package com.smallworld.drawer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallworld.drawer.data.CardBrief
import com.smallworld.drawer.ui.components.DeckColumn
import com.smallworld.drawer.ui.theme.AccentBlue
import com.smallworld.drawer.ui.theme.BgDark
import com.smallworld.drawer.ui.theme.TextMuted
import com.smallworld.drawer.ui.viewmodel.DeckEditorState

/**
 * 卡组编辑屏幕
 * 三栏布局：主卡组 | 副卡组（额外卡组在底部）
 */
@Composable
fun EditorScreen(
    state: DeckEditorState,
    onMoveCard: (CardBrief, String, String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.deckFileName ?: "未选择卡组",
                color = TextMuted,
                fontSize = 12.sp
            )
            Button(
                onClick = onAnalyze,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("分析", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 主卡组 + 副卡组 双栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeckColumn(
                title = "主卡组",
                cards = state.mainDeckCards,
                count = state.mainDeckCards.size,
                draggable = true,
                onCardClick = { card ->
                    onMoveCard(card, "main", "side")
                },
                modifier = Modifier.weight(1f)
            )

            DeckColumn(
                title = "副卡组",
                cards = state.sideDeckCards,
                count = state.sideDeckCards.size,
                draggable = true,
                onCardClick = { card ->
                    onMoveCard(card, "side", "main")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 额外卡组（只读）
        DeckColumn(
            title = "额外卡组",
            cards = state.extraDeckCards,
            count = state.extraDeckCards.size,
            draggable = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 提示
        Text(
            text = "💡 点击主卡组/副卡组的卡牌移动到对方区域",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }
}
