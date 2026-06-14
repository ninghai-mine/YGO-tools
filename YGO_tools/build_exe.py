"""
SmallWorldDrawer 打包器
生成单个 exe + 使用说明，不复制数据目录。
"""

import os
import sys
import subprocess
import shutil

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DIST_DIR = os.path.join(BASE_DIR, "dist")
BUILD_DIR = os.path.join(BASE_DIR, "build")


def _ensure_pyinstaller():
    try:
        import PyInstaller
    except ImportError:
        print("正在安装 PyInstaller...")
        subprocess.check_call(
            [sys.executable, "-m", "pip", "install", "pyinstaller", "-q"],
        )


def build():
    print("=" * 60)
    print("  SmallWorldDrawer 打包器")
    print("  生成 exe + 使用说明")
    print("=" * 60)

    _ensure_pyinstaller()
    import PyInstaller.__main__

    # 清理旧构建
    for d in [DIST_DIR, BUILD_DIR]:
        if os.path.exists(d):
            shutil.rmtree(d)

    main_file = os.path.join(BASE_DIR, "main.py")
    args = [
        main_file,
        "--name=SmallWorldDrawer",
        "--onefile",
        "--noconsole",
        "--distpath", DIST_DIR,
        "--workpath", BUILD_DIR,
        "--add-data", f"SmallWorldDrawer{os.pathsep}SmallWorldDrawer",
        "--add-data", f"db_helper.py{os.pathsep}.",
        "--hidden-import=sqlite3",
        "--hidden-import=flask",
        "--hidden-import=flask_cors",
        "--hidden-import=db_helper",
        "--hidden-import=numpy",
        "--hidden-import=matplotlib",
        "--hidden-import=matplotlib.backends.backend_agg",
        "--collect-all=flask",
        "--collect-all=matplotlib",
    ]

    print("\n打包中（约 1-3 分钟）...\n")
    PyInstaller.__main__.run(args)

    # 生成使用说明
    readme = os.path.join(DIST_DIR, "使用说明.txt")
    with open(readme, "w", encoding="utf-8") as f:
        f.write(f"""使用方法
========

1. 把 SmallWorldDrawer.exe 放到卡组数据目录中
   （即和 cdb/、deck/、expansions/、picture/ 这些文件夹放在一起）

2. 双击运行 SmallWorldDrawer.exe

3. 浏览器自动打开 http://127.0.0.1:5000

4. 使用完毕后关闭浏览器即可
""")

    exe_path = os.path.join(DIST_DIR, "SmallWorldDrawer.exe")
    print(f"\n完成！输出:")
    print(f"  {exe_path}")
    print(f"  {readme}")
    print(f"\n把 exe 放进数据目录双击就能用")


if __name__ == "__main__":
    build()
