package com.visibox.tv.ui.base;

import android.view.View;

import androidx.leanback.widget.Presenter;

import com.visibox.tv.bean.Vod;

public abstract class BaseVodHolder extends Presenter.ViewHolder {

    public BaseVodHolder(View view) {
        super(view);
    }

    public abstract void initView(Vod item);

    public abstract void unbind();
}
