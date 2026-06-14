"""webapp — SmallWorldDrawer Web 主模块
Flask 应用，注册 API 路由并启动服务器。
"""

import os
import sys
import base64
import io

# 先确保能找到 db_helper（源码或打包模式）
_this_dir = os.path.dirname(os.path.abspath(__file__))
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    _root = os.path.dirname(sys.executable)
else:
    _root = os.path.dirname(os.path.dirname(_this_dir))
sys.path.insert(0, _root)
sys.path.insert(0, os.path.dirname(_this_dir))  # 让 SmallWorldDrawer 包可导入

from flask import Flask, jsonify, request, render_template, send_from_directory
import db_helper
from SmallWorldDrawer.src.deck_reader import (
    list_available_decks,
    load_deck_cards,
    TMPPIC_DIR,
)
from SmallWorldDrawer.src.analyzer import (
    load_main_monsters,
    build_small_world_map,
    build_chain_map,
)

app = Flask(__name__)

# 内存缓存：存储最近一次加载的卡组分析结果
_cache = {"sw_map": None, "chain_map": None, "main_ids": None}


# ── 页面路由 ──

@app.route("/")
def index():
    return render_template("index.html")


# ── API 路由 ──

@app.route("/api/decks")
def api_list_decks():
    decks = list_available_decks()
    if not decks:
        return jsonify({"error": "卡组目录不存在或为空", "decks": []})
    return jsonify({
        "decks": [{"file": d["file"], "name": d["name"], "count": d["count"]}
                  for d in decks]
    })


@app.route("/api/load-deck", methods=["POST"])
def api_load_deck():
    data = request.get_json()
    deck_file = data.get("deck_file") if data else None
    if not deck_file:
        return jsonify({"error": "未指定卡组文件"})

    try:
        result = load_deck_cards(deck_file)
    except (FileNotFoundError, ValueError) as e:
        return jsonify({"error": str(e)})

    # 缓存主卡组 ID
    _cache["main_ids"] = result["main_deck"]

    # 分析小世界与检索关系
    sw_map = build_small_world_map(result["main_deck"])
    chain_map = build_chain_map(sw_map)
    _cache["sw_map"] = sw_map
    _cache["chain_map"] = chain_map

    # 构建排序后的主卡组怪兽列表（level↓ atk↓ def↓ id↑）
    monsters = load_main_monsters(result["main_deck"])
    sorted_ids = sorted(
        monsters.keys(),
        key=lambda cid: (
            -(monsters[cid]["level"] & 0xFF),
            -monsters[cid]["atk"],
            -monsters[cid]["def"],
            cid,
        ),
    )
    monster_list = []
    for cid in sorted_ids:
        m = monsters[cid]
        monster_list.append({
            "id": cid,
            "name": m["name"],
            "level": m["level"] & 0xFF,
            "atk": m["atk"],
            "def": m["def"],
            "race": m["race"],
            "attribute": m["attribute"],
            "has_pic": os.path.exists(os.path.join(TMPPIC_DIR, f"{cid}.jpg")),
            "relation_count": len(sw_map.get(cid, {}).get("relations", [])),
        })

    return jsonify({
        "main_count": len(result["main_deck"]),
        "extra_count": len(result["extra_deck"]),
        "side_count": len(result["side_deck"]),
        "total": len(result["all_ids"]),
        "monster_count": len(monster_list),
        "monsters": monster_list,
    })


@app.route("/api/chain-graph/<int:card_id>")
def api_chain_graph(card_id):
    """返回指定怪兽的检索关系图 (PNG)"""
    sw_map = _cache.get("sw_map")
    chain_map = _cache.get("chain_map")

    if sw_map is None or chain_map is None:
        return jsonify({"error": "请先加载卡组"}), 400
    if card_id not in sw_map:
        return jsonify({"error": f"卡牌 {card_id} 不在主卡组怪兽中"}), 404

    # 生成该怪兽的局部检索关系图
    buf = _generate_chain_graph(card_id, sw_map, chain_map)
    return (buf.getvalue(), 200, {"Content-Type": "image/png"})


@app.route("/api/chain-data/<int:card_id>")
def api_chain_data(card_id):
    """返回指定怪兽的检索关系 JSON 数据（供前端 canvas 绘图）"""
    sw_map = _cache.get("sw_map")
    chain_map = _cache.get("chain_map")

    if sw_map is None or chain_map is None:
        return jsonify({"error": "请先加载卡组"}), 400
    if card_id not in sw_map:
        return jsonify({"error": f"卡牌 {card_id} 不在主卡组怪兽中"}), 404

    info = sw_map[card_id]
    chains = chain_map.get(card_id, {}).get("chains", [])

    # 收集相关节点（自身 + 直接小世界关系 + 检索关系目标）
    nodes = {}
    nodes[card_id] = {"id": card_id, "name": info["name"],
                      "data": info["data"], "type": "center"}

    for rel in info["relations"]:
        rid = rel["id"]
        if rid not in nodes:
            rinfo = sw_map.get(rid, {})
            nodes[rid] = {"id": rid, "name": rinfo.get("name", ""),
                          "data": rinfo.get("data", {}), "type": "direct"}

    for ch in chains:
        tid = ch["target_id"]
        if tid not in nodes:
            tinfo = sw_map.get(tid, {})
            nodes[tid] = {"id": tid, "name": tinfo.get("name", ""),
                          "data": tinfo.get("data", {}), "type": "chain"}
        for br in ch["bridges"]:
            bid = br["bridge_id"]
            if bid not in nodes:
                binfo = sw_map.get(bid, {})
                nodes[bid] = {"id": bid, "name": binfo.get("name", ""),
                              "data": binfo.get("data", {}), "type": "bridge"}

    return jsonify({
        "center_id": card_id,
        "center_name": info["name"],
        "nodes": list(nodes.values()),
        "small_world_edges": info["relations"],
        "chain_edges": [
            {"from": card_id, "to": ch["target_id"],
             "bridges": ch["bridges"]}
            for ch in chains
        ],
    })


@app.route("/api/shutdown", methods=["POST"])
def api_shutdown():
    """关闭服务器"""
    os._exit(0)


@app.route("/api/tmpimg/<filename>")
def api_tmp_image(filename):
    return send_from_directory(TMPPIC_DIR, filename)


@app.route("/api/placeholder")
def api_placeholder():
    svg = """<svg xmlns="http://www.w3.org/2000/svg" width="177" height="254">
        <rect width="177" height="254" fill="#243447"/>
        <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle"
              fill="#4a5a6a" font-size="14" font-family="sans-serif">无图片</text>
    </svg>"""
    return (base64.b64decode(base64.b64encode(svg.encode())),
            200, {"Content-Type": "image/svg+xml"})


# ── 横向树形图表生成（卡图节点，无文字） ──

def _generate_chain_graph(center_id, sw_map, chain_map):
    """
    生成横向树状检索关系图。
    布局：根节点(左) → 一级/桥梁节点(中,红框) → 二级检索目标(右)
    无文字，仅卡图与线条。
    """
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.image as mpimg
    import numpy as np

    info = sw_map[center_id]
    chains = chain_map.get(center_id, {}).get("chains", [])

    # ── 收集树节点 ──
    level_1 = info["relations"]
    l1_ids = {r["id"] for r in level_1}

    # level_2: chain targets 且不在 level_1 中
    level_2_map = {}
    for ch in chains:
        tid = ch["target_id"]
        if tid == center_id or tid in l1_ids:
            continue
        if tid not in level_2_map:
            level_2_map[tid] = {"bridges": []}
        for br in ch["bridges"]:
            if br["bridge_id"] in l1_ids:
                level_2_map[tid]["bridges"].append(br)

    level_2 = list(level_2_map.items())  # [(tid, {bridges}), ...]

    n_l1 = len(level_1)
    n_l2 = len(level_2)
    n_total = max(n_l1, n_l2) + 1

    # ── 加载卡图 ──
    def _load_image(cid):
        path = os.path.join(TMPPIC_DIR, f"{cid}.jpg")
        if os.path.exists(path):
            return mpimg.imread(path)
        arr = np.zeros((86, 60, 4), dtype=np.uint8)
        arr[:, :, :3] = [36, 52, 71]
        arr[:, :, 3] = 255
        return arr

    # ── 横向布局参数 ──
    # 画布：按节点数量动态调整，最小 14x7
    fig_w = max(14, (n_total - 1) * 1.5 + 4)
    fig_h = max(7, n_total * 1.2 + 2)
    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    ax.set_facecolor("#0f1923")
    fig.patch.set_facecolor("#0f1923")

    # 归一化坐标范围
    x_root, x_l1, x_l2 = 0.10, 0.42, 0.78
    # 图片归一化尺寸（更大）
    iw, ih = 0.07, 0.10

    def _place(ax, cid, x, y, w, h, border="#c9a84c", lw=3):
        img = _load_image(cid)
        # 保持卡牌原始宽高比 (177:254 ≈ 0.697)
        card_w_px, card_h_px = 177, 254
        ratio = card_w_px / card_h_px
        # 在可用空间内按比例缩放
        if w / h > ratio:
            disp_h = h
            disp_w = disp_h * ratio
        else:
            disp_w = w
            disp_h = disp_w / ratio
        ext = [x - disp_w/2, x + disp_w/2, y - disp_h/2, y + disp_h/2]
        ax.imshow(img, extent=ext, aspect="auto", zorder=3)
        rect = plt.Rectangle((x - disp_w/2, y - disp_h/2), disp_w, disp_h,
                             fill=False, edgecolor=border, linewidth=lw, zorder=4)
        ax.add_patch(rect)

    def _line(ax, x1, y1, x2, y2, color="#c9a84c", lw=3, ls="-", alpha=0.8):
        ax.plot([x1, x2], [y1, y2], color=color, linewidth=lw,
                linestyle=ls, alpha=alpha, zorder=1)

    # ── 计算各层 Y 位置（均匀分布） ──
    y_root = 0.5

    if n_l1 > 0:
        y_l1_list = _distribute(n_l1, 0.12, 0.88)
    else:
        y_l1_list = []

    if n_l2 > 0:
        y_l2_list = _distribute(n_l2, 0.12, 0.88)
    else:
        y_l2_list = []

    # ── 一级节点配色方案 ──
    L1_COLORS = [
        "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6", "#1abc9c",
        "#e91e63", "#00bcd4", "#ff5722", "#8bc34a", "#ff9800",
        "#673ab7", "#03a9f4", "#cddc39", "#795548", "#607d8b",
    ]
    l1_colors = [L1_COLORS[i % len(L1_COLORS)] for i in range(n_l1)]
    # 一级 ID → 颜色
    l1_color_map = {rel["id"]: l1_colors[i] for i, rel in enumerate(level_1)}

    # ── 放置根节点 ──
    _place(ax, center_id, x_root, y_root, iw * 1.4, ih * 1.4,
           border="#c9a84c", lw=3)

    # ── 放置一级节点（彩色边框）并连线到根 ──
    for i, rel in enumerate(level_1):
        y = y_l1_list[i]
        color = l1_colors[i]
        _place(ax, rel["id"], x_l1, y, iw, ih, border=color, lw=3)
        _line(ax, x_root + iw * 0.7, y_root, x_l1 - iw * 0.7, y,
              color="#c9a84c", lw=3)

    # ── 放置二级节点并连线到桥梁（用桥梁对应的颜色） ──
    l2_y_map = {}
    for idx, (tid, _) in enumerate(level_2):
        l2_y_map[tid] = y_l2_list[idx] if idx < len(y_l2_list) else 0.5

    for tid, ch_info in level_2:
        y_c = l2_y_map[tid]
        _place(ax, tid, x_l2, y_c, iw, ih, border="#3498db", lw=3)

        parents = [br["bridge_id"] for br in ch_info["bridges"]
                   if br["bridge_id"] in l1_ids]
        if parents:
            for pid in parents:
                py = y_root
                for j, rel in enumerate(level_1):
                    if rel["id"] == pid:
                        py = y_l1_list[j]
                        break
                # 使用桥梁对应的颜色实线连接
                edge_color = l1_color_map.get(pid, "#e74c3c")
                _line(ax, x_l1 + iw * 0.7, py, x_l2 - iw * 0.7, y_c,
                      color=edge_color, lw=3, ls="-", alpha=0.6)
        else:
            _line(ax, x_root + iw * 0.7, y_root, x_l2 - iw * 0.7, y_c,
                  color="#e67e22", lw=2.5, ls="-", alpha=0.4)

    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")
    fig.tight_layout(pad=0)

    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=200, bbox_inches="tight",
                facecolor="#0f1923")
    plt.close(fig)
    buf.seek(0)
    return buf


def _distribute(n, start, end):
    """在 [start, end] 之间均匀分布 n 个点"""
    if n == 1:
        return [(start + end) / 2]
    step = (end - start) / (n - 1)
    return [start + i * step for i in range(n)]


# ── 启动 ──

def main():
    print("=" * 50)
    print("  SmallWorldDrawer - 小世界树绘制")
    print("=" * 50)

    stats = db_helper.get_db_stats()
    if stats["main"]:
        print(f"  主数据库: {stats['main'][1]} 张卡牌")
    for label, count in stats["expansions"]:
        print(f"  {label}: {count} 张超先行卡")
    print()

    decks = list_available_decks()
    print(f"  发现 {len(decks)} 个卡组文件")
    print()
    print(f"  启动服务器: http://127.0.0.1:5000")
    print("=" * 50)

    app.run(debug=False, host="127.0.0.1", port=5000)


if __name__ == "__main__":
    main()
