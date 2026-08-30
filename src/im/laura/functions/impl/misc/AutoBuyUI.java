package im.laura.functions.impl.misc;

import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BindSetting;

@FunctionRegister(name = "AutoBuyUI", type = Category.Misc)
public class AutoBuyUI extends Function {

    public BindSetting setting = new BindSetting("Кнопка открытия", -1);

    public AutoBuyUI() {
        addSettings(setting);
    }
}
