package com.visibox.tv.ui.custom;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visibox.tv.utils.ResUtil;

public class SpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final int spacing;
    private final int spanCount;

    public SpaceItemDecoration(int spacing) {
        this(-1, spacing);
    }

    public SpaceItemDecoration(int spanCount, int spacing) {
        this.spanCount = spanCount;
        this.spacing = ResUtil.dp2px(spacing);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position >= 0 && spanCount == -1) {
            if (position > 0) outRect.top = spacing;
        } else if (position >= 0 && spanCount > 0) {
            // 网格均分模式：不注水平 offset 与垂直行距（item 自身的 margin 已提供键位间隙），
            // 避免叠加导致内容总高超出可视高度、最后一行(7/8/9)被底部裁剪
        } else {
            outRect.left = 0;
            outRect.right = 0;
            outRect.top = 0;
            outRect.bottom = 0;
        }
    }
}