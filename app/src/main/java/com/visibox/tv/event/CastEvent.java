package com.visibox.tv.event;

import com.visibox.tv.bean.Config;
import com.visibox.tv.bean.Device;
import com.visibox.tv.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
