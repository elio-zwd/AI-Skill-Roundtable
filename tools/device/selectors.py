"""兼容 Python 标准库 ``selectors`` 与设备 UI 选择器命名空间。

直接执行 ``tools/device/cli.py`` 时，脚本目录会位于 ``sys.path`` 首位。
标准库 ``subprocess`` 随后导入顶层 ``selectors``，可能命中本文件。
此时加载真正的标准库实现；作为 ``device.selectors`` 导入时则转发
到项目的 UI 选择器模块。
"""

from __future__ import annotations

if __name__ == "selectors" and not __package__:
    import os as _os

    _stdlib_path = _os.path.join(_os.path.dirname(_os.__file__), "selectors.py")
    with open(_stdlib_path, "rb") as _stream:
        _source = _stream.read()
    exec(compile(_source, _stdlib_path, "exec"), globals(), globals())
else:
    from . import ui_selectors as _ui_selectors
    from .ui_selectors import (
        find_nodes,
        parse_bounds,
        parse_ui_nodes,
        select_unique_node,
        visible_texts,
        wait_for_selector,
    )

    # 保留既有测试和调用方通过 device.selectors.time 注入时钟的兼容性。
    time = _ui_selectors.time

    __all__ = [
        "find_nodes",
        "parse_bounds",
        "parse_ui_nodes",
        "select_unique_node",
        "visible_texts",
        "wait_for_selector",
        "time",
    ]
