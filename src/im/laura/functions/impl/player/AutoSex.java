package im.laura.functions.impl.player;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.Timer;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "AutoSex", type = Category.Player)
public class AutoSex extends Function {

    private final SliderSetting targetRange = new SliderSetting("Дальность", 5f, 1f, 10f, 1f);
    private final ModeSetting   mode        = new ModeSetting  ("Режим",     "Active", "Active", "Passive");
    private final SliderSetting msgDelay    = new SliderSetting("Задержка",  1f, 0f, 50f, 1f);

    private static final String[] PASSIVE_MESSAGES = {
            "It's so Biiiiiiig",
            "Be careful daddy <3",
            "Oh, I feel it inside me!"
    };

    private static final String[] ACTIVE_MESSAGES = {
            "Oh, I'm cumming!",
            "Oh, ur pussy is so nice!",
            "Yeah, yeah",
            "I feel u!",
            "Oh, im inside u"
    };

    private PlayerEntity target;
    private final Timer messageTimer = new Timer();
    private final Timer sneakTimer   = new Timer();

    public AutoSex() {
        addSettings(targetRange, mode, msgDelay);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;

        float range = targetRange.get();

        if (target == null) {
            target = getNearestPlayer(range);
            return;
        }

        if (target.getDistanceSq(mc.player) >= range * range) {
            target = null;
            return;
        }

        if (mode.is("Active")) {
            if (sneakTimer.passedMs((long) (200 + Math.random() * 1000))) {
                mc.gameSettings.keyBindSneak.setPressed(!mc.gameSettings.keyBindSneak.isKeyDown());
                sneakTimer.reset();
            }
        } else {
            if (!mc.gameSettings.keyBindSneak.isKeyDown())
                mc.gameSettings.keyBindSneak.setPressed(true);
        }

        if (messageTimer.passedMs((long) (msgDelay.get() * 1000)) && mc.player.connection != null) {
            List<String> messages = Arrays.asList(mode.is("Active") ? ACTIVE_MESSAGES : PASSIVE_MESSAGES);
            String msg = messages.get((int) (Math.random() * messages.size()));
            mc.player.sendChatMessage("msg " + target.getName().getString() + " " + msg);
            messageTimer.reset();
        }
    }

    private PlayerEntity getNearestPlayer(float range) {
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dist = p.getDistanceSq(mc.player);
            if (dist <= range * range && dist < nearestDist) {
                nearest = p;
                nearestDist = dist;
            }
        }
        return nearest;
    }
}