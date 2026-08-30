package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.SliderSetting;

@FunctionRegister(name = "Timer", type = Category.Movement)
public class Timer extends Function {

    private final SliderSetting speed = new SliderSetting("Скорость", 2f, 0.1f, 10f, 0.1f);

    public Timer() {
        addSettings(speed);
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        mc.timer.timerSpeed = speed.get();
    }

    private void reset() {
        mc.timer.timerSpeed = 1;
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        reset();
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        reset();
        return false;
    }
}
