package com.smallworld.drawer.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smallworld.drawer.data.CardBrief
import com.smallworld.drawer.data.CardData
import com.smallworld.drawer.data.DatabaseReader
import com.smallworld.drawer.data.DeckInfo
import com.smallworld.drawer.data.DeckReader
import com.smallworld.drawer.engine.SmallWorldAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ── 编辑器状态 ──

data class DeckEditorState(
    val deckFileName: String? = null,
    val mainDeckCards: List<CardBrief> = emptyList(),
    val sideDeckCards: List<CardBrief> = emptyList(),
    val extraDeckCards: List<CardBrief> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// ── 分析状态 ──

data class AnalysisState(
    val mainCount: Int = 0,
    val monsterCount: Int = 0,
    val monsters: List<SmallWorldAnalyzer.MonsterInfo> = emptyList(),
    val relationCounts: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// ── ViewModel ──

class MainViewModel(application: Application) : AndroidViewModel(application) {

    var editorState by mutableStateOf(DeckEditorState())
        private set

    var analysisState by mutableStateOf(AnalysisState())
        private set

    // 数据库缓存
    private var nameMap: Map<Int, String> = emptyMap()
    private var dataMap: Map<Int, CardData> = emptyMap()

    // 卡组原始数据（用于还原移动操作）
    private var currentDeckInfo: DeckInfo? = null
    private var currentDeckPath: String? = null

    // 分析结果缓存
    private var swMap: Map<Int, SmallWorldAnalyzer.SmallWorldNode> = emptyMap()
    private var chainMap: Map<Int, SmallWorldAnalyzer.ChainNode> = emptyMap()

    init {
        // 自动尝试加载数据库
        viewModelScope.launch {
            loadDatabase()
        }
    }

    /**
     * 加载 YGO 数据库
     */
    private suspend fun loadDatabase() {
        editorState = editorState.copy(isLoading = true, error = null)

        withContext(Dispatchers.IO) {
            try {
                val mainDb = DatabaseReader.findYgoDatabase()
                if (mainDb == null) {
                    editorState = editorState.copy(
                        isLoading = false,
                        error = "未找到 YGO 数据库文件，请确认已安装游戏王软件"
                    )
                    return@withContext
                }

                val dataRoot = DatabaseReader.findYgoDataRoot()
                val expansionDbs = if (dataRoot != null) {
                    DatabaseReader.findExpansionDatabases(dataRoot)
                } else emptyList()

                nameMap = DatabaseReader.loadAllCardNames(mainDb, expansionDbs)
                dataMap = DatabaseReader.loadAllCardDatas(mainDb, expansionDbs)

                // 扫描卡组
                val decks = DeckReader.listAvailableDecks()
                if (decks.isNotEmpty()) {
                    loadDeck(decks.first().path)
                } else {
                    editorState = editorState.copy(
                        isLoading = false,
                        error = "未找到卡组文件"
                    )
                }
            } catch (e: Exception) {
                editorState = editorState.copy(
                    isLoading = false,
                    error = "数据库加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载指定卡组
     */
    fun loadDeck(filePath: String) {
        viewModelScope.launch {
            editorState = editorState.copy(isLoading = true, error = null)

            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    val deckInfo = DeckReader.parseYdkFile(file)
                    currentDeckInfo = deckInfo
                    currentDeckPath = filePath

                    // 构建卡片摘要
                    val mainCards = deckInfo.mainDeck.map { id ->
                        DeckReader.toCardBrief(id, nameMap, dataMap)
                    }
                    val sideCards = deckInfo.sideDeck.map { id ->
                        DeckReader.toCardBrief(id, nameMap, dataMap)
                    }
                    val extraCards = deckInfo.extraDeck.map { id ->
                        DeckReader.toCardBrief(id, nameMap, dataMap)
                    }

                    editorState = editorState.copy(
                        deckFileName = file.name,
                        mainDeckCards = mainCards,
                        sideDeckCards = sideCards,
                        extraDeckCards = extraCards,
                        isLoading = false
                    )

                    // 自动分析
                    performAnalysis(deckInfo.mainDeck)

                } catch (e: Exception) {
                    editorState = editorState.copy(
                        isLoading = false,
                        error = "卡组加载失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 移动卡牌
     */
    fun moveCard(card: CardBrief, fromSection: String, toSection: String) {
        val deck = currentDeckInfo ?: return

        // 验证：移入主卡组的不能是额外怪兽
        if (toSection == "main" && card.isExtraMonster) {
            editorState = editorState.copy(
                error = "「${card.name}」是额外卡组怪兽，不能放入主卡组！"
            )
            return
        }

        when {
            fromSection == "main" && toSection == "side" -> {
                val newMain = deck.mainDeck.toMutableList().apply { remove(card.id) }
                val newSide = deck.sideDeck.toMutableList().apply { add(card.id) }
                val newDeck = deck.copy(mainDeck = newMain, sideDeck = newSide)
                currentDeckInfo = newDeck
                refreshEditor(newDeck)
                performAnalysis(newDeck.mainDeck)
            }
            fromSection == "side" && toSection == "main" -> {
                val newSide = deck.sideDeck.toMutableList().apply { remove(card.id) }
                val newMain = deck.mainDeck.toMutableList().apply { add(card.id) }
                val newDeck = deck.copy(mainDeck = newMain, sideDeck = newSide)
                currentDeckInfo = newDeck
                refreshEditor(newDeck)
                performAnalysis(newDeck.mainDeck)
            }
        }
    }

    private fun refreshEditor(deck: DeckInfo) {
        val mainCards = deck.mainDeck.map { DeckReader.toCardBrief(it, nameMap, dataMap) }
        val sideCards = deck.sideDeck.map { DeckReader.toCardBrief(it, nameMap, dataMap) }
        val extraCards = deck.extraDeck.map { DeckReader.toCardBrief(it, nameMap, dataMap) }
        editorState = editorState.copy(
            mainDeckCards = mainCards,
            sideDeckCards = sideCards,
            extraDeckCards = extraCards
        )
    }

    /**
     * 执行分析
     */
    fun performAnalysis(mainIds: List<Int>? = null) {
        viewModelScope.launch {
            analysisState = analysisState.copy(isLoading = true)

            val ids = mainIds ?: currentDeckInfo?.mainDeck ?: run {
                analysisState = analysisState.copy(isLoading = false)
                return@launch
            }

            withContext(Dispatchers.Default) {
                try {
                    val sw = SmallWorldAnalyzer.buildSmallWorldMap(ids, nameMap, dataMap)
                    val ch = SmallWorldAnalyzer.buildChainMap(sw)
                    swMap = sw
                    chainMap = ch

                    val monsters = SmallWorldAnalyzer.loadMainMonsters(ids, nameMap, dataMap)
                    val relCounts = sw.mapValues { (_, v) -> v.relations.size }

                    val sortedIds = monsters.entries.sortedWith(
                        compareByDescending<Map.Entry<Int, SmallWorldAnalyzer.MonsterInfo>> { it.value.level }
                            .thenByDescending { it.value.atk }
                            .thenByDescending { it.value.def }
                            .thenBy { it.key }
                    ).map { it.key }

                    val monsterList = sortedIds.mapNotNull { monsters[it] }

                    analysisState = analysisState.copy(
                        mainCount = ids.size,
                        monsterCount = monsterList.size,
                        monsters = monsterList,
                        relationCounts = relCounts,
                        isLoading = false
                    )
                } catch (e: Exception) {
                    analysisState = analysisState.copy(
                        isLoading = false,
                        error = "分析失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 重新分析（公开方法，供按钮调用）
     */
    fun reanalyze() {
        performAnalysis()
    }

    fun getSwMap() = swMap
    fun getChainMap() = chainMap
}
