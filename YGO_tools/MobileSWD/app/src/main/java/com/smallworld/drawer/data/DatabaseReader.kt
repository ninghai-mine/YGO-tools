package com.smallworld.drawer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File

/**
 * 数据库读取层 — 对应 Python 的 db_helper.py
 *
 * 功能：
 * 1. 自动扫描手机中常见的 YGO 数据目录
 * 2. 读取主数据库 cards.cdb + 扩展数据库 expansions/*.cdb
 * 3. 加载卡牌名称和 datas 数据
 */
object DatabaseReader {

    /** 常见 YGO 软件数据目录路径（按优先级排序） */
    private val YGO_DATA_DIRS = listOf(
        "Android/data/com.ygopro.linux/files",
        "Android/data/jp.lily.ygopro/files",
        "Android/data/io.github.enderneko.ygomobile/files",
        "Android/data/cn.gary.ygopro/files",
        "Android/data/com.ygopro.paulo/files",
        "ygopro" // 某些版本直接放在根目录
    )

    /** 主数据库相对路径 */
    private const val MAIN_DB_RELATIVE = "cdb/cards.cdb"

    /** 扩展数据库目录 */
    private const val EXPANSIONS_DIR = "expansions"

    // ── 目录扫描 ──

    /**
     * 查找手机上已安装的 YGO 数据库文件
     * @return 找到的第一个 cards.cdb 文件，未找到则返回 null
     */
    fun findYgoDatabase(): File? {
        val base = Environment.getExternalStorageDirectory()
        for (dir in YGO_DATA_DIRS) {
            val dbFile = File(base, "$dir/$MAIN_DB_RELATIVE")
            if (dbFile.exists()) return dbFile
        }
        return null
    }

    /**
     * 查找 YGO 数据根目录（用于读取卡图等资源）
     */
    fun findYgoDataRoot(): File? {
        val base = Environment.getExternalStorageDirectory()
        for (dir in YGO_DATA_DIRS) {
            val dataDir = File(base, dir)
            if (dataDir.exists() && dataDir.isDirectory) return dataDir
        }
        return null
    }

    /**
     * 查找扩展数据库文件列表
     */
    fun findExpansionDatabases(dataRoot: File): List<File> {
        val expDir = File(dataRoot, EXPANSIONS_DIR)
        if (!expDir.exists() || !expDir.isDirectory) return emptyList()
        return expDir.listFiles()
            ?.filter { it.extension == "cdb" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    // ── 数据加载 ──

    /**
     * 加载所有卡牌 ID→名称 映射
     * 扩展库的卡名会覆盖主库中同名 ID（对应 Python 的 load_all_card_names）
     */
    fun loadAllCardNames(mainDb: File, expansionDbs: List<File>): Map<Int, String> {
        val map = mutableMapOf<Int, String>()

        // 主数据库
        if (mainDb.exists()) {
            loadNamesFromDb(mainDb, map)
        }

        // 扩展数据库（后加载覆盖）
        for (expDb in expansionDbs) {
            if (expDb.exists()) {
                loadNamesFromDb(expDb, map)
            }
        }

        return map
    }

    /**
     * 加载所有卡牌 ID→CardData 映射
     * 扩展库覆盖主库中同名 ID
     */
    fun loadAllCardDatas(mainDb: File, expansionDbs: List<File>): Map<Int, CardData> {
        val map = mutableMapOf<Int, CardData>()

        if (mainDb.exists()) {
            loadDatasFromDb(mainDb, map)
        }

        for (expDb in expansionDbs) {
            if (expDb.exists()) {
                loadDatasFromDb(expDb, map)
            }
        }

        return map
    }

    // ── 内部方法 ──

    private fun loadNamesFromDb(dbFile: File, map: MutableMap<Int, String>) {
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT id, name FROM texts", null)
            while (cursor.moveToNext()) {
                map[cursor.getInt(0)] = cursor.getString(1)
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDatasFromDb(dbFile: File, map: MutableMap<Int, CardData>) {
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT * FROM datas", null)
            while (cursor.moveToNext()) {
                val data = CardData(
                    id = cursor.getInt(0),
                    ot = cursor.getInt(1),
                    alias = cursor.getInt(2),
                    setcode = cursor.getInt(3),
                    type = cursor.getInt(4),
                    atk = cursor.getInt(5),
                    def = cursor.getInt(6),
                    level = cursor.getInt(7),
                    race = cursor.getInt(8),
                    attribute = cursor.getInt(9),
                    category = cursor.getInt(10)
                )
                map[data.id] = data
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取数据库统计信息（对应 Python 的 get_db_stats）
     */
    fun getDbStats(mainDb: File, expansionDbs: List<File>): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()

        if (mainDb.exists()) {
            try {
                val db = SQLiteDatabase.openDatabase(mainDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("SELECT COUNT(*) FROM texts", null)
                if (cursor.moveToFirst()) stats["主数据库"] = cursor.getInt(0)
                cursor.close()
                db.close()
            } catch (_: Exception) {}
        }

        for (expDb in expansionDbs) {
            if (expDb.exists()) {
                try {
                    val db = SQLiteDatabase.openDatabase(expDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    val cursor = db.rawQuery("SELECT COUNT(*) FROM texts", null)
                    if (cursor.moveToFirst()) stats["扩展:${expDb.name}"] = cursor.getInt(0)
                    cursor.close()
                    db.close()
                } catch (_: Exception) {}
            }
        }

        return stats
    }
}
