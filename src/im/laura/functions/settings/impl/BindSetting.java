package im.laura.functions.settings.impl;

import im.laura.functions.settings.Setting;

import java.util.function.Supplier;

public class BindSetting extends Setting<Integer> {
    public BindSetting(String name, Integer defaultVal) {
        super(name, defaultVal);
    }

    @Override
    public BindSetting setVisible(Supplier<Boolean> bool) {
        return (BindSetting) super.setVisible(bool);
    }
}
