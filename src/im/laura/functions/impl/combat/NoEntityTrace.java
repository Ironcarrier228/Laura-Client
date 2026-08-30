package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

@FunctionRegister(name = "NoEntityTrace", type = Category.Combat)
@SuppressWarnings({"unused", "Beta"})
public class NoEntityTrace extends Function {
    
    private final SliderSetting range = new SliderSetting("Дистанция", 3f, 1f, 6f, 0.1f);
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);

    public NoEntityTrace() {
        addSettings(range, onlyPlayers);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        for (LivingEntity entity : mc.world.getPlayers()) {
            if (!isValid(entity)) continue;
            if (mc.player.getDistance(entity) <= range.get()) {
                mc.pointedEntity = entity;
                break;
            }
        }
    }

    private boolean isValid(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isInvulnerable()) return false;
        if (onlyPlayers.get() && !(entity instanceof PlayerEntity)) return false;
        if (entity instanceof PlayerEntity && AntiBot.isBot(entity)) return false;
        return true;
    }
}
