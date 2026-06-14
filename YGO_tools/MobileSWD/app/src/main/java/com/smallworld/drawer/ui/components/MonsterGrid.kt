package com.smallworld.drawer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import com.smallworld.drawer.data.DeckReader
import com.smallworld.drawer.engine.SmallWorldAnalyzer
import com.smallworld.drawer.ui.theme.BgCard
import com.smallworld.drawer.ui.theme.BgCardHover
import com.smallworld.drawer.ui.theme.Gold
import com.smallworld.drawer.ui.theme.TextMuted
import com.smallworld.drawer.ui.theme.TextSecondary

/**
 * 怪兽网格组件 — 分析结果展示
 */
@Composable
fun MonsterGrid(
    monsters: List<SmallWorldAnalyzer.MonsterInfo>,
    relationCounts: Map<Int, Int>,
    onMonsterClick: (SmallWorldAnalyzer.MonsterInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // 按星级↓ 攻击↓ 防守↓ 排序
    val sorted = monsters.sortedWith(
        compareByDescending<SmallWorldAnalyzer.MonsterInfo> { it.level }
            .thenByDescending { it.atk }
            .thenByDescending { it.def }
            .thenBy { it.id }
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(90.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(sorted, key = { it.id }) { monster ->
            MonsterCard(
                monster = monster,
                relationCount = relationCounts[monster.id] ?: 0,
                onClick = { onMonsterClick(monster) }
            )
        }
    }
}

@Composable
fun MonsterCard(
    monster: SmallWorldAnalyzer.MonsterInfo,
    relationCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .border(2.dp, androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
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
                val imgFile = DeckReader.findCardImage(monster.id)
                CardImage(
                    imageFile = imgFile,
                    modifier = Modifier.fillMaxWidth(),
                    placeholderText = monster.name
                )
            }

            // 等级徽章
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, end = 2.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "Lv${monster.level}",
                    color = Gold,
                    fontSize = 9.sp
                )
            }

            // 卡名 + 攻防
            Text(
                text = monster.name,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
            Text(
                text = "ATK ${monster.atk} / DEF ${monster.def}",
                color = TextSecondary,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
