"""生成 TV 端设置页布局。

一页太挤（31 项 8 行），这里拆成两页：
  pageMain —— 常用（接口/直播/播放/外观维护/更多），带分组小标题
  pageMore —— 个性化设置（网络、播放细节、显示、其他），从 pageMain 的「个性化设置」进入

布局由「行 × 单元格」数据驱动，改设置项只需改下面的 ROWS_MAIN / ROWS_MORE。
用法：python tools/gen_setting_layout.py
"""

import io
import os

CELL = '''                <androidx.appcompat.widget.LinearLayoutCompat
                    android:id="@+id/{id}"
                    android:layout_width="0dp"
                    android:layout_height="56dp"
                    android:layout_margin="4dp"
                    android:layout_weight="{weight}"
                    android:background="@drawable/selector_item"
                    android:focusable="true"
                    android:focusableInTouchMode="true"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:paddingStart="20dp"
                    android:paddingEnd="12dp">

                    <com.google.android.material.textview.MaterialTextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/{label}"
                        android:textColor="@color/text_glass"
                        android:textSize="17sp" />

                    <com.google.android.material.textview.MaterialTextView
                        android:id="@+id/{value}"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:layout_weight="1"
                        android:ellipsize="end"
                        android:gravity="end"
                        android:singleLine="true"
                        android:textColor="@color/text_glass_sub"
                        android:textSize="15sp" />

                    <androidx.appcompat.widget.AppCompatImageView
                        android:layout_width="18dp"
                        android:layout_height="18dp"
                        android:layout_marginStart="6dp"
                        android:src="@drawable/ic_setting_chevron" />

                </androidx.appcompat.widget.LinearLayoutCompat>
'''

CELL_NOVAL = '''                <androidx.appcompat.widget.LinearLayoutCompat
                    android:id="@+id/{id}"
                    android:layout_width="0dp"
                    android:layout_height="56dp"
                    android:layout_margin="4dp"
                    android:layout_weight="{weight}"
                    android:background="@drawable/selector_item"
                    android:focusable="true"
                    android:focusableInTouchMode="true"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:paddingStart="20dp"
                    android:paddingEnd="12dp">

                    <com.google.android.material.textview.MaterialTextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/{label}"
                        android:textColor="@color/text_glass"
                        android:textSize="17sp" />

                    <Space
                        android:layout_width="0dp"
                        android:layout_height="1dp"
                        android:layout_weight="1" />

                    <androidx.appcompat.widget.AppCompatImageView
                        android:layout_width="18dp"
                        android:layout_height="18dp"
                        android:layout_marginStart="6dp"
                        android:src="@drawable/ic_setting_chevron" />

                </androidx.appcompat.widget.LinearLayoutCompat>
'''

EMPTY = '''                <Space
                    android:layout_width="0dp"
                    android:layout_height="56dp"
                    android:layout_margin="4dp"
                    android:layout_weight="1" />
'''

HEADER = '''            <com.google.android.material.textview.MaterialTextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:layout_marginTop="14dp"
                android:text="@string/{label}"
                android:textColor="@color/text_glass_sub"
                android:textSize="15sp"
                android:textStyle="bold" />
'''

# (id, 标签字符串, 值字符串 id 或 None)；权重由 WEIGHTS 查表，默认 1
WEIGHTS = {"vod": 2, "line": 2, "live": 2, "customize": 2, "version": 2, "wall": 2, "back": 2}

# "H" 开头的一行是分组标题
ROWS_MAIN = [
    ("H", "setting_group_api"),
    [("vod", "setting_config_addr", "vodUrl"), ("line", "setting_line", None)],
    ("H", "setting_group_live"),
    [("live", "setting_live_addr", "liveUrl"), ("liveHome", "setting_live_default", None),
     ("liveHistory", "setting_live_history", None)],
    ("H", "setting_group_play"),
    [("playerSel", "setting_player_sel", "playerSelText"), ("player", "setting_player", None),
     ("vodHome", "setting_home_default", None), ("vodHistory", "setting_config_history", None)],
    ("H", "setting_group_look"),
    [("wallDefault", "setting_wall_default", None), ("cache", "setting_cache", "cacheText"),
     ("backup", "setting_backup", None), ("restore", "setting_restore", None)],
    ("H", "setting_group_more"),
    [("customize", "setting_customize", None), ("version", "setting_version", "versionText")],
]

ROWS_MORE = [
    ("H", "setting_group_net"),
    [("doh", "setting_doh", "dohText"), ("multi", "setting_multi", "multiText"),
     ("thread", "setting_thread", "threadText"), ("incognito", "setting_incognito", "incognitoText")],
    ("H", "setting_group_player"),
    [("render", "setting_render", "renderText"), ("buffer", "setting_buffer", "bufferText"),
     ("danmaku", "setting_danmaku", "danmakuText"), ("scale", "setting_scale", "scaleText")],
    ("H", "setting_group_display"),
    [("size", "setting_size", "sizeText"), ("glass", "setting_glass", "glassText"),
     ("homeCol", "setting_home_col", "homeColText"), ("historyNum", "setting_history_num", "historyNumText")],
    ("H", "setting_group_other"),
    [("wall", "setting_wall_addr", "wallUrl"), ("wallRefresh", "setting_wall_refresh", None),
     ("boot", "setting_boot", "bootText"), ("back", "setting_back", None)],
]

ROOT = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <androidx.appcompat.widget.LinearLayoutCompat
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingEnd="16dp"
        android:paddingBottom="16dp">

        <!-- 常用项 -->
        <androidx.appcompat.widget.LinearLayoutCompat
            android:id="@+id/pageMain"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

{main}
        </androidx.appcompat.widget.LinearLayoutCompat>

        <!-- 个性化设置（不常用项） -->
        <androidx.appcompat.widget.LinearLayoutCompat
            android:id="@+id/pageMore"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">

{more}
        </androidx.appcompat.widget.LinearLayoutCompat>

    </androidx.appcompat.widget.LinearLayoutCompat>

</androidx.core.widget.NestedScrollView>
'''


def build_body(rows):
    body = []
    for row in rows:
        if row[0] == "H":
            body.append(HEADER.replace("{label}", row[1]))
            body.append("\n")
            continue
        body.append('''            <androidx.appcompat.widget.LinearLayoutCompat
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">
''')
        for cell in row:
            if cell is None:
                body.append(EMPTY)
                continue
            cid, label, value = cell
            text = (CELL if value else CELL_NOVAL).replace("{id}", cid).replace("{label}", label).replace("{weight}", str(WEIGHTS.get(cid, 1)))
            if value:
                text = text.replace("{value}", value)
            body.append(text)
        body.append('            </androidx.appcompat.widget.LinearLayoutCompat>\n')
    return "".join(body)


def main():
    out = os.path.join("app", "src", "leanback", "res", "layout", "activity_setting.xml")
    text = ROOT.replace("{main}", build_body(ROWS_MAIN)).replace("{more}", build_body(ROWS_MORE))
    io.open(out, "w", encoding="utf-8").write(text)
    print("written", out)


if __name__ == "__main__":
    main()
