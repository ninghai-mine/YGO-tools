package com.smallworld.drawer.engine

import com.smallworld.drawer.data.CardData
import com.smallworld.drawer.data.DeckReader

/**
 * 小世界关系分析引擎 — 对应 Python 的 analyzer.py
 *
 * 功能：
 * 1. 构建小世界关系映射（5 个属性中有且只有一个相同）
 * 2. 构建 2-hop 检索关系映射
 * 3. 推荐桥梁怪兽
 */
object SmallWorldAnalyzer {

    private const val TYPE_MONSTER = 0x1
    val SMALL_WORLD_FIELDS = listOf("race", "attribute", "level", "atk", "def")

    val FIELD_CN = mapOf(
        "race" to "种族",
        "attribute" to "属性",
        "level" to "星级",
        "atk" to "攻击力",
        "def" to "防守力"
    )

    // ── 数据结构 ──

    data class MonsterInfo(
        val id: Int,
        val name: String,
        val race: Int,
        val attribute: Int,
        val level: Int,
        val atk: Int,
        val def: Int,
        val type: Int
    )

    data class Relation(
        val id: Int,
        val name: String,
        val matchField: String
    )

    data class ChainTarget(
        val targetId: Int,
        val targetName: String,
        val bridges: List<Bridge>
    )

    data class Bridge(
        val bridgeId: Int,
        val bridgeName: String,
        val aToBField: String,
        val bToCField: String
    )

    data class SmallWorldNode(
        val name: String,
        val data: MonsterInfo,
        val relations: List<Relation>
    )

    data class ChainNode(
        val name: String,
        val data: MonsterInfo,
        val chains: List<ChainTarget>
    )

    // ── 核心逻辑 ──

    /**
     * 从主卡组 ID 列表中加载怪兽信息（对应 Python 的 load_main_monsters）
     */
    fun loadMainMonsters(
        mainDeckIds: List<Int>,
        nameMap: Map<Int, String>,
        dataMap: Map<Int, CardData>
    ): Map<Int, MonsterInfo> {
        val monsters = mutableMapOf<Int, MonsterInfo>()
        for (cid in mainDeckIds) {
            val cardData = dataMap[cid] ?: continue
            if ((cardData.type and TYPE_MONSTER) == 0) continue
            monsters[cid] = MonsterInfo(
                id = cid,
                name = nameMap[cid] ?: "未知",
                race = cardData.race,
                attribute = cardData.attribute,
                level = cardData.level and 0xFF,
                atk = cardData.atk,
                def = cardData.def,
                type = cardData.type
            )
        }
        return monsters
    }

    /**
     * 计算两个怪兽匹配的属性数量
     * @param returnField 如果为 true，当精确匹配 1 个时返回该字段名
     */
    fun countMatches(a: MonsterInfo, b: MonsterInfo, returnField: Boolean = false): Pair<Int, String?> {
        var count = 0
        var field: String? = null

        // 逐一比较 5 个属性
        if (a.race == b.race) { count++; field = "race" }
        if (a.attribute == b.attribute) { count++; field = "attribute" }
        if (a.level == b.level) { count++; field = "level" }
        if (a.atk == b.atk) { count++; field = "atk" }
        if (a.def == b.def) { count++; field = "def" }

        return if (returnField) {
            Pair(count, if (count == 1) field else null)
        } else {
            Pair(count, null)
        }
    }

    /**
     * 判断两只怪兽是否存在小世界关系
     */
    fun hasSmallWorldRelation(a: MonsterInfo, b: MonsterInfo): Boolean {
        return countMatches(a, b).first == 1
    }

    /**
     * 构建小世界关系映射（对应 Python 的 build_small_world_map）
     *
     * @return Map<cardId, SmallWorldNode>
     */
    fun buildSmallWorldMap(
        mainDeckIds: List<Int>,
        nameMap: Map<Int, String>,
        dataMap: Map<Int, CardData>
    ): Map<Int, SmallWorldNode> {
        val monsters = loadMainMonsters(mainDeckIds, nameMap, dataMap)
        val ids = monsters.keys.toList()

        val result = mutableMapOf<Int, SmallWorldNode>()

        for (cidA in ids) {
            val a = monsters[cidA]!!
            val rels = mutableListOf<Relation>()

            for (cidB in ids) {
                if (cidA == cidB) continue
                val b = monsters[cidB]!!
                val (count, field) = countMatches(a, b, returnField = true)
                if (field != null) {
                    rels.add(Relation(id = cidB, name = b.name, matchField = field))
                }
            }

            result[cidA] = SmallWorldNode(
                name = a.name,
                data = a,
                relations = rels.sortedBy { it.id }
            )
        }

        return result
    }

    /**
     * 构建检索关系映射（2-hop chain，对应 Python 的 build_chain_map）
     *
     * 检索关系: 存在 B 使得 A-B 且 B-C 均为小世界关系，则 A-C 构成检索关系
     */
    fun buildChainMap(swMap: Map<Int, SmallWorldNode>): Map<Int, ChainNode> {
        val result = mutableMapOf<Int, ChainNode>()
        val ids = swMap.keys.toList()
        val relMap = swMap.mapValues { (_, info) -> info.relations }
        val nameMap = swMap.mapValues { (_, info) -> info.name }
        val dataMap = swMap.mapValues { (_, info) -> info.data }

        for (cidA in ids) {
            val chainTargets = mutableMapOf<Int, MutableList<Bridge>>()

            for (relB in relMap[cidA] ?: emptyList()) {
                val bId = relB.id
                for (relC in relMap[bId] ?: emptyList()) {
                    val cId = relC.id
                    if (cId == cidA) continue

                    val bridges = chainTargets.getOrPut(cId) { mutableListOf() }
                    bridges.add(
                        Bridge(
                            bridgeId = bId,
                            bridgeName = nameMap[bId] ?: "",
                            aToBField = relB.matchField,
                            bToCField = relC.matchField
                        )
                    )
                }
            }

            val chains = chainTargets.entries
                .sortedBy { it.key }
                .map { (cId, bridges) ->
                    ChainTarget(
                        targetId = cId,
                        targetName = nameMap[cId] ?: "",
                        bridges = bridges
                    )
                }

            result[cidA] = ChainNode(
                name = nameMap[cidA] ?: "",
                data = dataMap[cidA]!!,
                chains = chains
            )
        }

        return result
    }

    /**
     * 推荐桥梁怪兽（对应 Python 的 suggest_bridges）
     * 给定目标怪兽 ID，找出可以作为桥梁的怪兽（按额外连接数排序）
     */
    fun suggestBridges(
        mainDeckIds: List<Int>,
        targetId: Int,
        nameMap: Map<Int, String>,
        dataMap: Map<Int, CardData>
    ): List<BridgeCandidate> {
        val swMap = buildSmallWorldMap(mainDeckIds, nameMap, dataMap)
        val targetNode = swMap[targetId] ?: return emptyList()

        val targetRelIds = targetNode.relations.map { it.id }.toSet()
        val bridges = mutableListOf<BridgeCandidate>()

        for ((cid, info) in swMap) {
            if (cid == targetId || cid !in targetRelIds) continue
            val other = info.relations.filter { it.id != targetId }
            bridges.add(
                BridgeCandidate(
                    id = cid,
                    name = info.name,
                    alsoConnects = other
                )
            )
        }

        return bridges.sortedByDescending { it.alsoConnects.size }
    }

    data class BridgeCandidate(
        val id: Int,
        val name: String,
        val alsoConnects: List<Relation>
    )

    // ── 打印（仅供调试）──

    fun printSmallWorldMap(swMap: Map<Int, SmallWorldNode>) {
        for ((cid, info) in swMap) {
            val fieldsStr = info.relations.joinToString(", ") {
                "${it.name}(${FIELD_CN[it.matchField] ?: it.matchField})"
            }
            println("  [$cid] ${info.name}  ->  ${info.relations.size} 条关系")
            if (info.relations.isNotEmpty()) {
                println("         $fieldsStr")
            }
            println()
        }
    }

    fun printChainMap(chainMap: Map<Int, ChainNode>) {
        for ((cid, info) in chainMap) {
            println("  [$cid] ${info.name}  ->  ${info.chains.size} 条检索关系")
            for (ch in info.chains.take(10)) {
                val bridgeStr = ch.bridges.joinToString(", ") {
                    "[${it.bridgeId}]${it.bridgeName}" +
                            "(${FIELD_CN[it.aToBField] ?: it.aToBField}-" +
                            "${FIELD_CN[it.bToCField] ?: it.bToCField})"
                }
                println("       -> [${ch.targetId}]${ch.targetName}  经: $bridgeStr")
            }
            if (info.chains.size > 10) {
                println("       ... 还有 ${info.chains.size - 10} 条")
            }
            println()
        }
    }
}
