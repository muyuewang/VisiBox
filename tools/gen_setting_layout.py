"""生成 TV 端设置页的四列网格布局。

布局由「行 × 单元格」数据驱动，改设置项只需改下面的 ROWS 表。
用法：python tools/gen_setting_layout.py
"""

import io
import os

CELL = '''            <androidx.appcompat.widget.LinearLayoutCompat
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
                    android:textColor="@color/white"
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
                    android:textColor="#B3FFFFFF"
                    android:textSize="15sp" />

                <androidx.appcompat.widget.AppCompatImageView
                    android:layout_width="18dp"
                    android:layout_height="18dp"
                    android:layout_marginStart="6dp"
                    android:src="@drawable/ic_setting_chevron" />

            </androidx.appcompat.widget.LinearLayoutCompat>
'''

CELL_NOVAL = '''            <androidx.appcompat.widget.LinearLayoutCompat
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
                    android:textColor="@color/white"
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

EMPTY = '''            <Space
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_margin="4dp"
                android:layout_weight="1" />
'''

# (id, 标签字符串, 值字符串 id 或 None)；权重由 WEIGHTS 查表，默认 1
WEIGHTS = {"vod": 2, "line": 2, "live": 2}

ROWS = [
    [("vod", "setting_config_addr", "vodUrl"), ("line", "setting_line", None)],
    [("live", "setting_live_addr", "liveUrl"), ("playerSel", "setting_player_sel", "playerSelText"), ("player", "setting_player", None)],
    [("vodHome", "setting_home_default", None), ("liveHome", "setting_live_default", None),
     ("vodHistory", "setting_config_history", None), ("liveHistory", "setting_live_history", None)],
    [("wall", "setting_wall_addr", "wallUrl"), ("wallDefault", "setting_wall_default", None),
     ("wallRefresh", "setting_wall_refresh", None), ("multi", "setting_multi", "multiText")],
    [("doh", "setting_doh", "dohText"), ("incognito", "setting_incognito", "incognitoText"),
     ("size", "setting_size", "sizeText"), ("scale", "setting_scale", "scaleText")],
    [("render", "setting_render", "renderText"), ("buffer", "setting_buffer", "bufferText"),
     ("danmaku", "setting_danmaku", "danmakuText"), ("thread", "setting_thread", "threadText")],
    [("cache", "setting_cache", "cacheText"), ("backup", "setting_backup", None),
     ("restore", "setting_restore", None), ("version", "setting_version", "versionText")],
    [("boot", "setting_boot", "bootText"), ("historyNum", "setting_history_num", "historyNumText"),
     ("homeCol", "setting_home_col", "homeColText"), None],
]

ROOT = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <androidx.appcompat.widget.LinearLayoutCompat
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="16dp">

{body}
    </androidx.appcompat.widget.LinearLayoutCompat>

</androidx.core.widget.NestedScrollView>
'''


def build_body():
    body = []
    for row in ROWS:
        body.append('''        <androidx.appcompat.widget.LinearLayoutCompat
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
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
        body.append('        </androidx.appcompat.widget.LinearLayoutCompat>\n')
    return "".join(body)


def main():
    out = os.path.join("app", "src", "leanback", "res", "layout", "activity_setting.xml")
    io.open(out, "w", encoding="utf-8").write(ROOT.replace("{body}", build_body()))
    print("written", out)


if __name__ == "__main__":
    main()
