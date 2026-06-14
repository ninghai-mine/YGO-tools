"""drawer — 小世界关系与检索关系可视化绘图模块"""

import os
import sys

_this_dir = os.path.dirname(os.path.abspath(__file__))
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.dirname(_this_dir))
sys.path.insert(0, BASE_DIR)

import matplotlib.pyplot as plt
import networkx as nx

from SmallWorldDrawer.src.analyzer import (
    build_small_world_map,
    build_chain_map,
    FIELD_CN,
)

# 种族/属性颜色映射
RACE_COLORS = {
    0x1: "#e74c3c", 0x2: "#9b59b6", 0x4: "#f1c40f", 0x8: "#2c3e50",
    0x10: "#7f8c8d", 0x20: "#3498db", 0x40: "#2980b9", 0x80: "#e67e22",
    0x100: "#95a5a6", 0x200: "#1abc9c", 0x400: "#27ae60", 0x800: "#f39c12",
    0x1000: "#d35400", 0x2000: "#c0392b", 0x4000: "#8e44ad", 0x8000: "#16a085",
}
ATTR_SYMBOLS = {0x01: "\u2641", 0x02: "\u2648", 0x04: "\u2642", 0x08: "\u2644",
                 0x10: "\u2606", 0x20: "\u2605", 0x40: "\u2726", 0x80: "\u2753"}


def draw_small_world_graph(sw_map, save_path=None):
    """
    绘制小世界关系图（无向图）。
    节点=怪兽，边=小世界关系，边标签=匹配字段。
    """
    G = nx.Graph()
    name_map = {cid: info["name"] for cid, info in sw_map.items()}

    # 添加节点
    for cid, info in sw_map.items():
        data = info["data"]
        label = f"{cid}\\n{data['name']}"
        G.add_node(cid, label=label, race=data["race"], attr=data["attribute"])

    # 添加边
    for cid, info in sw_map.items():
        for rel in info["relations"]:
            # 无向图，只添加一次
            if cid < rel["id"]:
                G.add_edge(cid, rel["id"], match=rel["match_field"])

    _plot_graph(G, name_map, "小世界关系图", save_path)
    return G


def draw_chain_graph(sw_map, chain_map, save_path=None):
    """
    绘制检索关系图。
    红色实线=小世界关系，蓝色虚线=检索关系。
    节点名称标注在下方。
    """
    G = nx.Graph()
    name_map = {cid: info["name"] for cid, info in sw_map.items()}

    for cid, info in sw_map.items():
        data = info["data"]
        label = f"{data['name']}"
        G.add_node(cid, label=label, race=data["race"], attr=data["attribute"])

    # 小世界关系边（红色）
    for cid, info in sw_map.items():
        for rel in info["relations"]:
            if cid < rel["id"]:
                G.add_edge(cid, rel["id"], kind="small_world",
                           match=rel["match_field"])

    # 检索关系边（蓝色虚线），避免重复
    added_chains = set()
    for cid, info in chain_map.items():
        for ch in info["chains"]:
            key = (min(cid, ch["target_id"]), max(cid, ch["target_id"]))
            if key not in added_chains:
                added_chains.add(key)
                # 如果已经有小世界关系边，不覆盖
                if not G.has_edge(*key):
                    G.add_edge(cid, ch["target_id"], kind="chain")

    _plot_graph(G, name_map, "检索关系图", save_path, show_chains=True)
    return G


def _plot_graph(G, name_map, title, save_path=None, show_chains=False):
    """统一的绘图逻辑"""
    plt.figure(figsize=(14, 10))
    pos = nx.spring_layout(G, k=2, iterations=50, seed=42)

    # 绘制节点
    node_labels = nx.get_node_attributes(G, "label")
    nx.draw_networkx_nodes(G, pos, node_color="#3498db", node_size=800,
                           edgecolors="#2c3e50", linewidths=1)

    # 绘制节点标签
    nx.draw_networkx_labels(G, pos, labels=node_labels, font_size=7,
                            font_family="sans-serif")

    # 绘制边
    if show_chains and any(d.get("kind") == "chain" for _, _, d in G.edges(data=True)):
        sw_edges = [(u, v) for u, v, d in G.edges(data=True)
                    if d.get("kind") == "small_world"]
        chain_edges = [(u, v) for u, v, d in G.edges(data=True)
                       if d.get("kind") == "chain"]
        nx.draw_networkx_edges(G, pos, edgelist=sw_edges, edge_color="#e74c3c",
                               width=1.5, alpha=0.7)
        nx.draw_networkx_edges(G, pos, edgelist=chain_edges, edge_color="#3498db",
                               width=1.5, alpha=0.5, style="dashed")
    else:
        nx.draw_networkx_edges(G, pos, edge_color="#e74c3c", width=1.2, alpha=0.6)
        # 边标签（匹配字段）
        edge_labels = {(u, v): d.get("match", "") for u, v, d in G.edges(data=True)
                       if d.get("match")}
        if edge_labels:
            edge_labels_cn = {k: FIELD_CN.get(v, v) for k, v in edge_labels.items()}
            nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels_cn,
                                         font_size=6, alpha=0.7)

    plt.title(title, fontsize=16, pad=20)
    plt.axis("off")
    plt.tight_layout()

    if save_path:
        plt.savefig(save_path, dpi=200, bbox_inches="tight")
        print(f"  已保存: {save_path}")
    plt.show()


# ── 独立运行 ──

if __name__ == "__main__":
    from SmallWorldDrawer.src.deck_reader import list_available_decks, load_deck_cards

    decks = list_available_decks()
    if not decks:
        print("没有找到卡组")
        sys.exit(1)

    print(f"使用卡组: {decks[0]['name']}")
    result = load_deck_cards(decks[0]["file"])
    main_ids = result["main_deck"]

    print("构建小世界关系...")
    sw_map = build_small_world_map(main_ids)
    print(f"  怪兽 {len(sw_map)} 只")

    print("构建检索关系...")
    chain_map = build_chain_map(sw_map)
    total_chains = sum(len(v["chains"]) for v in chain_map.values())
    print(f"  检索关系 {total_chains} 条")

    print("\n绘制小世界关系图...")
    draw_small_world_graph(sw_map, save_path=os.path.join(BASE_DIR, "graph_small_world.png"))

    print("绘制检索关系图...")
    draw_chain_graph(sw_map, chain_map, save_path=os.path.join(BASE_DIR, "graph_chain.png"))