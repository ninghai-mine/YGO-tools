"""数据库公共模块 — 支持主数据库 + 扩展数据库（超先行卡）的统一读取"""

import sqlite3
import os
import sys


def get_project_root():
    """
    获取项目根目录。
    源码模式：基于 __file__ 定位到项目根。
    PyInstaller 打包模式：基于 exe 所在目录。
    """
    if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
        return os.path.dirname(sys.executable)
    else:
        return os.path.dirname(os.path.abspath(__file__))


BASE_DIR = get_project_root()
MAIN_DB = os.path.join(BASE_DIR, "cdb", "cards.cdb")
EXPANSIONS_DIR = os.path.join(BASE_DIR, "expansions")


def get_expansion_files():
    """扫描 expansions 目录下所有 .cdb 文件"""
    if not os.path.isdir(EXPANSIONS_DIR):
        return []
    return sorted(
        os.path.join(EXPANSIONS_DIR, f)
        for f in os.listdir(EXPANSIONS_DIR)
        if f.endswith(".cdb")
    )


def get_all_db_files():
    """返回所有数据库文件列表：[主数据库, 扩展数据库1, ...]"""
    dbs = [MAIN_DB]
    dbs.extend(get_expansion_files())
    return [db for db in dbs if os.path.exists(db)]


def connect_all():
    """连接所有数据库，返回 (main_conn, [expansion_conn, ...])"""
    main_conn = sqlite3.connect(MAIN_DB)
    exp_conns = []
    for f in get_expansion_files():
        exp_conns.append(sqlite3.connect(f))
    return main_conn, exp_conns


def load_all_card_names():
    """从所有数据库加载卡牌 ID -> 名称的映射（扩展库覆盖主库同名 ID）"""
    name_map = {}

    # 先加载主数据库
    if os.path.exists(MAIN_DB):
        conn = sqlite3.connect(MAIN_DB)
        cursor = conn.cursor()
        cursor.execute("SELECT id, name FROM texts")
        name_map.update(cursor.fetchall())
        conn.close()

    # 加载扩展数据库（后加载的会覆盖主库中同 ID 的卡名，即使用新卡信息）
    for f in get_expansion_files():
        conn = sqlite3.connect(f)
        cursor = conn.cursor()
        cursor.execute("SELECT id, name FROM texts")
        name_map.update(cursor.fetchall())
        conn.close()

    return name_map


def load_all_datas():
    """从所有数据库加载卡牌 ID -> datas 的映射"""
    data_map = {}

    if os.path.exists(MAIN_DB):
        conn = sqlite3.connect(MAIN_DB)
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM datas")
        columns = [desc[0] for desc in cursor.description]
        rows = cursor.fetchall()
        for row in rows:
            data_map[row[0]] = dict(zip(columns, row))
        conn.close()

    for f in get_expansion_files():
        conn = sqlite3.connect(f)
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM datas")
        columns = [desc[0] for desc in cursor.description]
        rows = cursor.fetchall()
        for row in rows:
            data_map[row[0]] = dict(zip(columns, row))
        conn.close()

    return data_map


def get_card_name(card_id, name_map=None):
    """获取卡牌名称，如果 name_map 为 None 则自动加载"""
    if name_map is None:
        name_map = load_all_card_names()
    return name_map.get(card_id)


def get_db_stats():
    """返回各数据库的统计信息"""
    stats = {"main": None, "expansions": []}

    if os.path.exists(MAIN_DB):
        conn = sqlite3.connect(MAIN_DB)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM texts")
        stats["main"] = ("主数据库 (cdb/cards.cdb)", cursor.fetchone()[0])
        conn.close()

    for f in get_expansion_files():
        conn = sqlite3.connect(f)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM texts")
        name = os.path.basename(f)
        stats["expansions"].append((f"扩展数据库 ({name})", cursor.fetchone()[0]))
        conn.close()

    return stats
