package com.smallworld.drawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smallworld.drawer.engine.SmallWorldAnalyzer
import com.smallworld.drawer.ui.screens.AnalysisScreen
import com.smallworld.drawer.ui.screens.ChainGraphDialog
import com.smallworld.drawer.ui.screens.EditorScreen
import com.smallworld.drawer.ui.theme.BgDark
import com.smallworld.drawer.ui.theme.Gold
import com.smallworld.drawer.ui.theme.GoldLight
import com.smallworld.drawer.ui.theme.SmallWorldDrawerTheme
import com.smallworld.drawer.ui.theme.TextMuted
import com.smallworld.drawer.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmallWorldDrawerTheme {
                val viewModel: MainViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by remember { mutableIntStateOf(0) }
                var dialogMonster by remember { mutableStateOf<SmallWorldAnalyzer.MonsterInfo?>(null) }

                // 显示错误信息
                val editorError = viewModel.editorState.error
                val analysisError = viewModel.analysisState.error
                LaunchedEffect(editorError) {
                    editorError?.let { snackbarHostState.showSnackbar(it) }
                }
                LaunchedEffect(analysisError) {
                    analysisError?.let { snackbarHostState.showSnackbar(it) }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = BgDark
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BgDark)
                            .padding(paddingValues)
                    ) {
                        // 顶部标题
                        androidx.compose.material3.Text(
                            text = "SmallWorldDrawer",
                            color = Gold,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        androidx.compose.material3.Text(
                            text = "小世界树绘制 — 卡组分析与可视化工具",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )

                        // 标签页
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = BgDark,
                            contentColor = Gold,
                            indicator = { tabPositions ->
                                if (selectedTab < tabPositions.size) {
                                    SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                        color = Gold
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Text(
                                        "📋 卡组编辑",
                                        color = if (selectedTab == 0) Gold else TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Text(
                                        "🔍 关系分析",
                                        color = if (selectedTab == 1) Gold else TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }

                        // 内容
                        when (selectedTab) {
                            0 -> {
                                EditorScreen(
                                    state = viewModel.editorState,
                                    onMoveCard = { card, from, to ->
                                        viewModel.moveCard(card, from, to)
                                    },
                                    onAnalyze = {
                                        viewModel.reanalyze()
                                        selectedTab = 1
                                    }
                                )
                            }
                            1 -> {
                                AnalysisScreen(
                                    state = viewModel.analysisState,
                                    onMonsterClick = { monster ->
                                        dialogMonster = monster
                                    }
                                )
                            }
                        }
                    }
                }

                // 检索关系图弹窗
                dialogMonster?.let { monster ->
                    val swMap = viewModel.getSwMap()
                    val chainMap = viewModel.getChainMap()
                    if (monster.id in swMap) {
                        ChainGraphDialog(
                            centerId = monster.id,
                            centerName = monster.name,
                            swMap = swMap,
                            chainMap = chainMap,
                            onDismiss = { dialogMonster = null }
                        )
                    } else {
                        android.widget.Toast
                            .makeText(
                                this@MainActivity,
                                "该怪兽不在主卡组中",
                                android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                        dialogMonster = null
                    }
                }
            }
        }
    }
}
