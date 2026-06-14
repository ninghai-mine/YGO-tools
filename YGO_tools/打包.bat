@echo off
chcp 65001 >nul
title SmallWorldDrawer 打包工具
echo ============================================
echo   SmallWorldDrawer - 一键打包
echo   为小白用户生成可直接运行的 exe
echo ============================================
echo.
echo [1/2] 安装依赖（仅首次需要）...
call pip install flask flask-cors networkx matplotlib pyinstaller -q
echo.
echo [2/2] 打包中...
python build_exe.py
echo.
echo 打包完成！dist/SmallWorldDrawer.exe 已生成
echo 双击该 exe 即可使用
echo.
pause
