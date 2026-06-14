"""SmallWorldDrawer 打包安装脚本"""
from setuptools import setup, find_packages

setup(
    name="smallworlddrawer",
    version="1.0.0",
    description="小世界树绘制 — 游戏王卡组分析与可视化工具",
    author="SmallWorldDrawer",
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        "flask>=3.0",
        "flask-cors>=4.0",
    ],
    entry_points={
        "console_scripts": [
            "smallworlddrawer=SmallWorldDrawer.src.app:main",
        ],
    },
    python_requires=">=3.10",
)
