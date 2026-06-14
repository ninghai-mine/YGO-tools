package com.smallworld.drawer.data

/**
 * 卡牌 datas 表原始数据（对应 Python db_helper 的 datas 表）
 */
data class CardData(
    val id: Int,
    val ot: Int,
    val alias: Int,
    val setcode: Int,
    val type: Int,
    val atk: Int,
    val def: Int,
    val level: Int,
    val race: Int,
    val attribute: Int,
    val category: Int
)

/**
 * 卡组信息（对应 Python deck_reader 的 read_ydk_file 返回值）
 */
data class DeckInfo(
    val mainDeck: List<Int>,
    val extraDeck: List<Int>,
    val sideDeck: List<Int>
)

/**
 * 卡组文件信息（对应 Python deck_reader 的 list_available_decks 返回值）
 */
data class DeckFile(
    val name: String,
    val path: String,
    val totalCount: Int
)

/**
 * 用于 UI 展示的卡牌摘要信息
 */
data class CardBrief(
    val id: Int,
    val name: String,
    val type: Int,
    val isMonster: Boolean,
    val isExtraMonster: Boolean,
    val race: Int,
    val attribute: Int,
    val level: Int,
    val atk: Int,
    val def: Int,
    val hasPic: Boolean
)
