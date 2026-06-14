package com.smallworld.drawer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallworld.drawer.data.CardBrief
import com.smallworld.drawer.data.DeckReader
import com.smallworld.drawer.ui.theme.BgCard
import com.smallworld.drawer.ui.theme.BgCardHover
import com.smallworld.drawer.ui.theme.Border
import com.smallworld.drawer.ui.theme.ExtraBadgeBg
import com.smallworld.drawer.ui.theme.Gold
import com.smallworld.drawer.ui.theme.TextMuted
import com.smallworld.drawer.ui.theme.TextSecondary

/**
 * 卡组列组件 — 显示一个卡组区（主卡组/副卡组/额外卡组）的卡牌网格
 *
 * @param title 列标题（如"主卡组"）
 * @param cards 卡牌列表
 * @param count 卡牌数量
 * @param draggable 是否可拖拽（主/副卡组为 true，额外卡组为 false）
 * @param onCardClick 点击卡牌回调（用于移动）
 */
@Composable
fun DeckColumn(
    title: String,
    cards: List<CardBrief>,
    count: Int,
    draggable: Boolean = true,
    onCardClick: (CardBrief) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        // 列头
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Gold,
                fontSize = 13.sp
            )
            Text(
                text = "${count} 张",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // 卡牌网格
        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(cards, key = { it.id }) { card ->
                DeckCard(
                    card = card,
                    draggable = draggable,
                    onClick = { onCardClick(card) }
                )
            }
        }
    }
}

/**
 * 单个卡牌卡片组件
 */
@Composable
fun DeckCard(
    card: CardBrief,
    draggable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .let { mod ->
                if (draggable) mod.clickable(onClick = onClick)
                else mod
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 卡图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(177f / 254f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgCardHover)
            ) {
                val imgFile = DeckReader.findCardImage(card.id)
                CardImage(
                    imageFile = imgFile,
                    modifier = Modifier.fillMaxWidth(),
                    placeholderText = card.name
                )

                // 等级勋章
                if (card.isMonster && card.level > 0 && !card.isExtraMonster) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .background(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Lv${card.level}",
                            color = Gold,
                            fontSize = 8.sp
                        )
                    }
                }

                // 额外怪兽 EX 标记
                if (card.isExtraMonster) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .background(
                                color = ExtraBadgeBg.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "EX",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 7.sp
                        )
                    }
                }
            }

            // 卡名
            Text(
                text = card.name,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}
