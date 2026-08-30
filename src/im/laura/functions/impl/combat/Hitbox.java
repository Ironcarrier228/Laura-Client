package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;

@FunctionRegister(name = "HitBox", type = Category.Combat)
@SuppressWarnings({"unused", "Beta"})
public class Hitbox extends Function {
    public final SliderSetting size = new SliderSetting("Размер", 0.2f, 0f, 3f, 0.05f);
    public final BooleanSetting visible = new BooleanSetting("Видимые", false);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);
    public final ModeSetting mode = new ModeSetting("Режим", "Expand", "Expand", "Shrink", "Custom");
    public final SliderSetting height = new SliderSetting("Высота", 1.8f, 0.1f, 3f, 0.1f).setVisible(() -> mode.is("Custom"));
    
    public Hitbox() {
        addSettings(size, visible, onlyPlayers, mode, height);
    }
    
    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (!visible.get() || mc.player == null || mc.world == null) return;

        for (LivingEntity entity : mc.world.getPlayers()) {
            if (!isValid(entity)) continue;
            
            double expand = getExpandValue();
            entity.setBoundingBox(calculateBoundingBox(entity, expand));
        }
    }

    private double getExpandValue() {
        return switch (mode.get()) {
            case "Shrink" -> -size.get();
            case "Custom" -> 0;
            default -> size.get();
        };
    }

    private boolean isValid(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        if (onlyPlayers.get() && !(entity instanceof PlayerEntity)) return false;
        return true;
    }

    private AxisAlignedBB calculateBoundingBox(Entity entity, double expand) {
        AxisAlignedBB bb = entity.getBoundingBox();
        
        double minX = entity.getPosX() - (entity.getWidth() / 2 + expand);
        double minY = mode.is("Custom") ? entity.getPosY() : bb.minY;
        double minZ = entity.getPosZ() - (entity.getWidth() / 2 + expand);
        double maxX = entity.getPosX() + (entity.getWidth() / 2 + expand);
        double maxY = mode.is("Custom") ? entity.getPosY() + height.get() : bb.maxY;
        double maxZ = entity.getPosZ() + (entity.getWidth() / 2 + expand);

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
