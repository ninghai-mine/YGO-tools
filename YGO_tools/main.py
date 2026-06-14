"""SmallWorldDrawer — 启动入口"""
import sys
import os
import threading
import webbrowser

# PyInstaller 打包后，BASE_DIR 为 exe 所在目录
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE_DIR)

from SmallWorldDrawer.src.webapp import main


def _open_browser():
    webbrowser.open("http://127.0.0.1:5000")


if __name__ == "__main__":
    threading.Timer(1.5, _open_browser).start()
    main()
