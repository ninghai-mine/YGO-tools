"""deck_reader — 卡组数据读取与整理模块
负责解析 ydk 文件、查询数据库、创建临时数据库和图片库，不依赖 Flask。
"""

import os
import sys
import shutil
import sqlite3

# ── 卡牌类型标志 ──
TYPE_MONSTER = 0x1
TYPE_FUSION = 0x40
TYPE_SYNCHRO = 0x2000
TYPE_XYZ = 0x800000
TYPE_LINK = 0x4000000
EXTRA_DECK_TYPES = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK

# 路径：源码或 PyInstaller 打包
_this_dir = os.path.dirname(os.path.abspath(__file__))
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.dirname(_this_dir))
sys.path.insert(0, BASE_DIR)

import db_helper

# ── 路径常量 ──
DECK_DIR = os.path.join(BASE_DIR, "deck")
TMPDB_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "tmpdb")
TMPCARD_DB_DIR = os.path.join(TMPDB_DIR, "tmpcard")
TMPPIC_DIR = os.path.join(TMPDB_DIR, "tmppic")
PICTURE_DIR = os.path.join(BASE_DIR, "picture", "card")
EXPANSION_PICS_DIR = os.path.join(BASE_DIR, "expansions", "pics")

os.makedirs(TMPCARD_DB_DIR, exist_ok=True)
os.makedirs(TMPPIC_DIR, exist_ok=True)


# ── ydk 解析 ──

def read_ydk_file(filepath):
    """解析 .ydk 文件，返回 (main, extra, side) 三个 ID 列表"""
    main_deck, extra_deck, side_deck = [], [], []
    current_section = None

    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line == "#main":
                current_section = "main"; continue
            elif line == "#extra":
                current_section = "extra"; continue
            elif line == "!side":
                current_section = "side"; continue
            if line.startswith("#"):
                continue
            try:
                card_id = int(line)
            except ValueError:
                continue
            if current_section == "main":
                main_deck.append(card_id)
            elif current_section == "extra":
                extra_deck.append(card_id)
            elif current_section == "side":
                side_deck.append(card_id)

    return main_deck, extra_deck, side_deck


# ── 卡组发现 ──

def list_available_decks():
    """返回所有可用卡组的信息列表"""
    if not os.path.isdir(DECK_DIR):
        return []
    decks = []
    for f in sorted(os.listdir(DECK_DIR)):
        if not f.endswith(".ydk"):
            continue
        filepath = os.path.join(DECK_DIR, f)
        main_deck, extra_deck, side_deck = read_ydk_file(filepath)
        total = len(main_deck) + len(extra_deck) + len(side_deck)
        decks.append({
            "file": f,
            "name": f.replace(".ydk", ""),
            "count": total,
            "path": filepath,
        })
    return decks


# ── 临时数据库创建 ──

def load_deck_cards(deck_file):
    """读取指定卡组，创建临时数据库和图片库，返回卡牌数据字典"""
    filepath = os.path.join(DECK_DIR, deck_file)
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"卡组文件不存在: {deck_file}")

    main_deck, extra_deck, side_deck = read_ydk_file(filepath)
    all_ids = main_deck + extra_deck + side_deck
    unique_ids = list(set(all_ids))

    if not unique_ids:
        raise ValueError("卡组为空")

    # 加载卡牌映射
    name_map = db_helper.load_all_card_names()
    data_map = db_helper.load_all_datas()

    # ── 写入临时数据库 ──
    tmp_db_path = os.path.join(TMPCARD_DB_DIR, "tmp_cards.db")
    conn = sqlite3.connect(tmp_db_path)
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS datas (
            id INTEGER PRIMARY KEY, ot INTEGER, alias INTEGER, setcode INTEGER,
            type INTEGER, atk INTEGER, def INTEGER, level INTEGER,
            race INTEGER, attribute INTEGER, category INTEGER
        )
    """)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS texts (
            id INTEGER PRIMARY KEY, name TEXT, desc TEXT,
            str1 TEXT, str2 TEXT, str3 TEXT, str4 TEXT, str5 TEXT,
            str6 TEXT, str7 TEXT, str8 TEXT, str9 TEXT, str10 TEXT,
            str11 TEXT, str12 TEXT, str13 TEXT, str14 TEXT, str15 TEXT, str16 TEXT
        )
    """)
    cursor.execute("DELETE FROM datas")
    cursor.execute("DELETE FROM texts")

    # 查扩展库
    exp_files = db_helper.get_expansion_files()

    cards_info = []
    pic_count = 0

    for cid in unique_ids:
        card_data = data_map.get(cid)
        card_name = name_map.get(cid, "未知卡牌")

        # 查完整文本（主库 → 扩展库）
        text_row = _query_text_row(cid, exp_files)

        # 插入 datas
        if card_data:
            cursor.execute(
                "INSERT INTO datas VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                (card_data["id"], card_data["ot"], card_data["alias"],
                 card_data["setcode"], card_data["type"], card_data["atk"],
                 card_data["def"], card_data["level"], card_data["race"],
                 card_data["attribute"], card_data["category"])
            )
        else:
            cursor.execute("INSERT INTO datas (id) VALUES (?)", (cid,))

        # 插入 texts
        if text_row:
            cursor.execute(
                "INSERT INTO texts VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                text_row
            )
        else:
            cursor.execute("INSERT INTO texts (id, name, desc) VALUES (?,?,?)",
                           (cid, card_name, ""))

        # 复制图片
        has_pic = _copy_card_image(cid)
        if has_pic:
            pic_count += 1

        cards_info.append({"id": cid, "name": card_name, "has_pic": has_pic})

    conn.commit()
    conn.close()

    return {
        "main_deck": main_deck,
        "extra_deck": extra_deck,
        "side_deck": side_deck,
        "all_ids": all_ids,
        "cards_info": cards_info,
        "db_count": len(unique_ids),
        "pic_count": pic_count,
    }


# ── 内部工具 ──

def _query_text_row(card_id, exp_files):
    """先查主库 texts，查不到再遍历扩展库"""
    conn = sqlite3.connect(db_helper.MAIN_DB)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM texts WHERE id = ?", (card_id,))
    row = cursor.fetchone()
    conn.close()
    if row:
        return row

    for f in exp_files:
        try:
            conn = sqlite3.connect(f)
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM texts WHERE id = ?", (card_id,))
            row = cursor.fetchone()
            conn.close()
            if row:
                return row
        except Exception:
            continue
    return None


def _copy_card_image(card_id):
    """将卡图复制到临时目录，先找主库图片再找扩展库图片"""
    dst = os.path.join(TMPPIC_DIR, f"{card_id}.jpg")

    src = os.path.join(PICTURE_DIR, f"{card_id}.jpg")
    if os.path.exists(src):
        shutil.copy2(src, dst)
        return True

    src = os.path.join(EXPANSION_PICS_DIR, f"{card_id}.jpg")
    if os.path.exists(src):
        shutil.copy2(src, dst)
        return True

    return False


# ── 卡牌类型验证 ──

def is_extra_deck_monster(card_type):
    """判断是否为额外卡组怪兽（融合/同步/超量/连接）"""
    return bool(card_type & EXTRA_DECK_TYPES)


def can_be_in_main_deck(card_type):
    """
    判断卡牌是否可以放入主卡组。
    怪兽卡（type & 0x1）但非额外卡组怪兽即可。
    非怪兽卡（魔法/陷阱）也可以放入主卡组。
    """
    if card_type & TYPE_MONSTER:
        return not is_extra_deck_monster(card_type)
    return True


# ── ydk 保存 ──

def save_ydk_file(filepath, main_ids, extra_ids, side_ids):
    """将卡组保存回 ydk 文件"""
    with open(filepath, "w", encoding="utf-8") as f:
        f.write("#main\n")
        for cid in main_ids:
            f.write(f"{cid}\n")
        f.write("#extra\n")
        for cid in extra_ids:
            f.write(f"{cid}\n")
        f.write("!side\n")
        for cid in side_ids:
            f.write(f"{cid}\n")
