package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import im.laura.events.CameraEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;

import java.lang.reflect.Field;

@FunctionRegister(name = "CameraClip", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava"})
public class CameraClip extends Function {

    private final SliderSetting distance = new SliderSetting("Дистанция", 3.0f, 0.5f, 10.0f, 0.5f);
    private final BooleanSetting noClip = new BooleanSetting("No Clip", true);

    public CameraClip() {
        addSettings(distance, noClip);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onCamera(CameraEvent e) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        if (!noClip.get()) return;

        try {
            Field clipDistanceField = mc.gameRenderer.getClass().getDeclaredField("clipDistance");
            clipDistanceField.setAccessible(true);
            clipDistanceField.set(mc.gameRenderer, distance.get());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
