"""analyzer — 小世界关系分析模块
计算主卡组怪兽之间的小世界关系（有且只有一个属性相同）。
"""

import os
import sys

_this_dir = os.path.dirname(os.path.abspath(__file__))
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.dirname(_this_dir))
sys.path.insert(0, BASE_DIR)

import db_helper

TYPE_MONSTER = 0x1

SMALL_WORLD_FIELDS = ["race", "attribute", "level", "atk", "def"]

FIELD_CN = {
    "race": "种族",
    "attribute": "属性",
    "level": "星级",
    "atk": "攻击力",
    "def": "防守力",
}


def is_monster(card_type):
    return (card_type & TYPE_MONSTER) != 0


def has_small_world_relation(a_data, b_data):
    return _count_matches(a_data, b_data) == 1


def _count_matches(a, b, return_field=False):
    count = 0
    field = None
    checks = [
        ("race", a["race"], b["race"]),
        ("attribute", a["attribute"], b["attribute"]),
        ("level", a["level"], b["level"]),
        ("atk", a["atk"], b["atk"]),
        ("def", a["def"], b["def"]),
    ]
    for name, av, bv in checks:
        if av == bv:
            count += 1
            field = name
    if return_field:
        return count, field if count == 1 else None
    return count


def load_main_monsters(main_deck_ids):
    data_map = db_helper.load_all_datas()
    name_map = db_helper.load_all_card_names()
    monsters = {}
    for cid in main_deck_ids:
        card_data = data_map.get(cid)
        if card_data is None or not is_monster(card_data["type"]):
            continue
        monsters[cid] = {
            "id": cid,
            "name": name_map.get(cid, "未知"),
            "race": card_data["race"],
            "attribute": card_data["attribute"],
            "level": card_data["level"] & 0xFF,
            "atk": card_data["atk"],
            "def": card_data["def"],
            "type": card_data["type"],
        }
    return monsters


def build_small_world_map(main_deck_ids):
    """
    遍历主卡组所有怪兽，构建小世界关系映射。
    返回 { card_id: { name, data, relations: [{id, name, match_field}] } }
    """
    monsters = load_main_monsters(main_deck_ids)
    ids = list(monsters.keys())

    result = {}
    for cid_a in ids:
        da = monsters[cid_a]
        rels = []
        for cid_b in ids:
            if cid_a == cid_b:
                continue
            db = monsters[cid_b]
            _, field = _count_matches(da, db, return_field=True)
            if field:
                rels.append({"id": cid_b, "name": db["name"], "match_field": field})
        result[cid_a] = {
            "name": da["name"],
            "data": da,
            "relations": sorted(rels, key=lambda x: x["id"]),
        }
    return result


def print_small_world_map(sw_map):
    for cid, info in sw_map.items():
        rels = info["relations"]
        fields_str = ", ".join(
            f"{r['name']}({FIELD_CN.get(r['match_field'], r['match_field'])})"
            for r in rels
        )
        print(f"  [{cid}] {info['name']}  ->  {len(rels)} 条关系")
        if rels:
            print(f"         {fields_str}")
        print()


def suggest_bridges(main_deck_ids, target_id):
    sw_map = build_small_world_map(main_deck_ids)
    if target_id not in sw_map:
        return []
    target_rel_ids = {r["id"] for r in sw_map[target_id]["relations"]}
    bridges = []
    for cid, info in sw_map.items():
        if cid == target_id or cid not in target_rel_ids:
            continue
        other = [r for r in info["relations"] if r["id"] != target_id]
        bridges.append({"id": cid, "name": info["name"], "also_connects": other})
    bridges.sort(key=lambda x: len(x["also_connects"]), reverse=True)
    return bridges


# ── 独立运行测试 ──

if __name__ == "__main__":
    from SmallWorldDrawer.src.deck_reader import list_available_decks, load_deck_cards

    decks = list_available_decks()
    if not decks:
        print("没有找到卡组")
        sys.exit(1)

    print(f"使用卡组: {decks[0]['name']}")
    result = load_deck_cards(decks[0]["file"])
    print(f"\n主卡组 {len(result['main_deck'])} 张 -> 分析小世界关系...\n")
    sw_map = build_small_world_map(result["main_deck"])

    total_monsters = len(sw_map)
    total_relations = sum(len(v["relations"]) for v in sw_map.values())
    print(f"怪兽数: {total_monsters}  关系总数: {total_relations}\n")
    print_small_world_map(sw_map)


# ── 检索关系（2-hop chain）──

def build_chain_map(sw_map):
    """
    基于小世界关系映射构建检索关系映射。
    检索关系: 存在 B 使得 A-B 且 B-C 均为小世界关系，则 A-C 构成检索关系。

    返回:
        { card_id: {
            "name": str,
            "data": {...},
            "chains": [
                { "target_id": int, "target_name": str,
                  "bridges": [
                      { "bridge_id": int, "bridge_name": str,
                        "a_to_b_field": str, "b_to_c_field": str },
                  ]
                },
            ]
        }}
    """
    result = {}
    ids = list(sw_map.keys())
    rel_map = {cid: info["relations"] for cid, info in sw_map.items()}
    name_map = {cid: info["name"] for cid, info in sw_map.items()}
    data_map = {cid: info["data"] for cid, info in sw_map.items()}

    for cid_a in ids:
        chain_targets = {}
        for rel_b in rel_map[cid_a]:
            b_id = rel_b["id"]
            for rel_c in rel_map.get(b_id, []):
                c_id = rel_c["id"]
                if c_id == cid_a:
                    continue
                if c_id not in chain_targets:
                    chain_targets[c_id] = []
                chain_targets[c_id].append({
                    "bridge_id": b_id,
                    "bridge_name": name_map[b_id],
                    "a_to_b_field": rel_b["match_field"],
                    "b_to_c_field": rel_c["match_field"],
                })

        chains = []
        for c_id, bridges in sorted(chain_targets.items(), key=lambda x: x[0]):
            chains.append({
                "target_id": c_id,
                "target_name": name_map[c_id],
                "bridges": bridges,
            })

        result[cid_a] = {
            "name": name_map[cid_a],
            "data": data_map[cid_a],
            "chains": chains,
        }
    return result


def print_chain_map(chain_map):
    """美化打印检索关系"""
    for cid, info in chain_map.items():
        chains = info["chains"]
        print(f"  [{cid}] {info['name']}  ->  {len(chains)} 条检索关系")
        for ch in chains[:10]:
            bridge_str = ", ".join(
                f"[{b['bridge_id']}]{b['bridge_name']}"
                f"({FIELD_CN.get(b['a_to_b_field'],b['a_to_b_field'])}-"
                f"{FIELD_CN.get(b['b_to_c_field'],b['b_to_c_field'])})"
                for b in ch["bridges"]
            )
            print(f"       -> [{ch['target_id']}]{ch['target_name']}  经: {bridge_str}")
        if len(chains) > 10:
            print(f"       ... 还有 {len(chains)-10} 条")
        print()