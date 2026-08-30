package im.laura.functions.impl.misc;

import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;

@FunctionRegister(name = "BetterMinecraft", type = Category.Misc)
public class BetterMinecraft extends Function {

    public final BooleanSetting smoothCamera = new BooleanSetting("Плавная камера", true);
    //public final BooleanSetting smoothTab = new BooleanSetting("Плавный таб", true); // пот
    public final BooleanSetting betterTab = new BooleanSetting("Улучшенный таб", true);

    public BetterMinecraft() {
        addSettings(smoothCamera, betterTab);
    }
}
