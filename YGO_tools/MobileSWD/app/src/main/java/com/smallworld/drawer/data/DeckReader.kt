package com.smallworld.drawer.data

import android.os.Environment
import java.io.File

/**
 * 卡组读取层 — 对应 Python 的 deck_reader.py
 *
 * 功能：
 * 1. 解析 .ydk 文件
 * 2. 扫描卡组目录
 * 3. 加载卡图
 */
object DeckReader {

    // ── 卡牌类型标志 ──
    private const val TYPE_MONSTER = 0x1
    private const val TYPE_FUSION = 0x40
    private const val TYPE_SYNCHRO = 0x2000
    private const val TYPE_XYZ = 0x800000
    private const val TYPE_LINK = 0x4000000
    private const val EXTRA_DECK_TYPES = TYPE_FUSION or TYPE_SYNCHRO or TYPE_XYZ or TYPE_LINK

    /** 卡组文件默认存放目录 */
    private val DEFAULT_DECK_DIRS = listOf(
        "",             // 根目录
        "ygopro/deck",
        "deck"
    )

    // ── ydk 解析 ──

    /**
     * 解析 .ydk 文件（对应 Python 的 read_ydk_file）
     * @return DeckInfo 包含主卡组/额外/副卡组的 ID 列表
     */
    fun parseYdkFile(file: File): DeckInfo {
        val mainDeck = mutableListOf<Int>()
        val extraDeck = mutableListOf<Int>()
        val sideDeck = mutableListOf<Int>()

        var currentSection: String? = null

        file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine

                when (trimmed) {
                    "#main" -> { currentSection = "main"; return@forEachLine }
                    "#extra" -> { currentSection = "extra"; return@forEachLine }
                    "!side" -> { currentSection = "side"; return@forEachLine }
                }

                if (trimmed.startsWith("#")) return@forEachLine

                val cardId = trimmed.toIntOrNull() ?: return@forEachLine

                when (currentSection) {
                    "main" -> mainDeck.add(cardId)
                    "extra" -> extraDeck.add(cardId)
                    "side" -> sideDeck.add(cardId)
                }
            }
        }

        return DeckInfo(mainDeck, extraDeck, sideDeck)
    }

    // ── 卡组发现 ──

    /**
     * 扫描手机存储中的 .ydk 文件
     * 搜索位置：Download 目录 + YGO 数据目录
     */
    fun listAvailableDecks(): List<DeckFile> {
        val decks = mutableListOf<DeckFile>()
        val base = Environment.getExternalStorageDirectory()

        // 搜索 Download 目录
        val downloadDir = File(base, "Download")
        if (downloadDir.exists()) {
            decks.addAll(findYdkFiles(downloadDir))
        }

        // 搜索 YGO 数据目录
        val ygoDataRoot = DatabaseReader.findYgoDataRoot()
        if (ygoDataRoot != null) {
            for (subDir in DEFAULT_DECK_DIRS) {
                val dir = if (subDir.isEmpty()) ygoDataRoot else File(ygoDataRoot, subDir)
                if (dir.exists()) {
                    decks.addAll(findYdkFiles(dir))
                }
            }
        }

        return decks.sortedBy { it.name }
    }

    private fun findYdkFiles(dir: File): List<DeckFile> {
        return dir.listFiles()
            ?.filter { it.extension == "ydk" }
            ?.map { file ->
                val (main, extra, side) = parseYdkFile(file).let {
                    Triple(it.mainDeck, it.extraDeck, it.sideDeck)
                }
                val total = main.size + extra.size + side.size
                DeckFile(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    totalCount = total
                )
            }
            ?: emptyList()
    }

    // ── 卡牌类型验证（对应 Python 的 is_extra_deck_monster / can_be_in_main_deck）──

    fun isExtraDeckMonster(cardType: Int): Boolean {
        return (cardType and EXTRA_DECK_TYPES) != 0
    }

    fun canBeInMainDeck(cardType: Int): Boolean {
        if ((cardType and TYPE_MONSTER) != 0) {
            return !isExtraDeckMonster(cardType)
        }
        return true // 魔法/陷阱可以放主卡组
    }

    fun isMonster(cardType: Int): Boolean {
        return (cardType and TYPE_MONSTER) != 0
    }

    // ── 卡图加载 ──

    /**
     * 查找卡图文件
     * 先查主库图片目录，再查扩展库图片目录
     */
    fun findCardImage(cardId: Int): File? {
        val dataRoot = DatabaseReader.findYgoDataRoot() ?: return null

        val candidates = listOf(
            File(dataRoot, "picture/card/${cardId}.jpg"),
            File(dataRoot, "pics/${cardId}.jpg"),
            File(dataRoot, "expansions/pics/${cardId}.jpg"),
        )

        return candidates.firstOrNull { it.exists() }
    }

    /**
     * 将卡牌转为 UI 展示用的 CardBrief
     */
    fun toCardBrief(
        id: Int,
        nameMap: Map<Int, String>,
        dataMap: Map<Int, CardData>
    ): CardBrief {
        val name = nameMap[id] ?: "未知卡牌"
        val data = dataMap[id]
        val cardType = data?.type ?: 0
        return CardBrief(
            id = id,
            name = name,
            type = cardType,
            isMonster = isMonster(cardType),
            isExtraMonster = isExtraDeckMonster(cardType),
            race = data?.race ?: 0,
            attribute = data?.attribute ?: 0,
            level = (data?.level ?: 0) and 0xFF,
            atk = data?.atk ?: 0,
            def = data?.def ?: 0,
            hasPic = findCardImage(id) != null
        )
    }
}
