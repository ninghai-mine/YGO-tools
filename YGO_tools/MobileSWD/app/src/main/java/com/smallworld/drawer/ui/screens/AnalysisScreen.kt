package com.smallworld.drawer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallworld.drawer.engine.SmallWorldAnalyzer
import com.smallworld.drawer.ui.components.MonsterGrid
import com.smallworld.drawer.ui.theme.BgCard
import com.smallworld.drawer.ui.theme.BgDark
import com.smallworld.drawer.ui.theme.Gold
import com.smallworld.drawer.ui.theme.TextMuted
import com.smallworld.drawer.ui.theme.TextSecondary
import com.smallworld.drawer.ui.viewmodel.AnalysisState

/**
 * 关系分析屏幕
 * 显示统计栏 + 怪兽网格
 */
@Composable
fun AnalysisScreen(
    state: AnalysisState,
    onMonsterClick: (SmallWorldAnalyzer.MonsterInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 统计栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatItem(num = state.mainCount.toString(), label = "主卡组")
            StatItem(num = state.monsterCount.toString(), label = "怪兽")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 排序信息
        Text(
            text = "仅显示主卡组怪兽 · 按星级↓ · 攻击力↓ · 防守力↓ 排序",
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // 怪兽网格 或 空状态
        if (state.monsters.isEmpty()) {
            Text(
                text = "主卡组中没有怪兽卡，请先在「卡组编辑」中调整卡组。",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 60.dp).fillMaxWidth()
            )
        } else {
            MonsterGrid(
                monsters = state.monsters,
                relationCounts = state.relationCounts,
                onMonsterClick = onMonsterClick
            )
        }
    }
}

@Composable
private fun StatItem(num: String, label: String) {
    Column(
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = num,
            color = Gold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}
