package im.laura.functions.impl.render;

import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.SliderSetting;

@FunctionRegister(name = "AspectRatio", type = Category.Render)
public class AspectRatio extends Function {

    public final SliderSetting ratio = new SliderSetting("Соотношение", 1.78f, 0.1f, 5f, 0.01f);

    public AspectRatio() {
        addSettings(ratio);
    }
}
