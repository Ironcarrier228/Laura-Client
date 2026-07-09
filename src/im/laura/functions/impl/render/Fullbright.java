package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventMotion;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.entity.player.PlayerEntity;

@FunctionRegister(name = "Fullbright", type = Category.Render)
public class Fullbright extends Function {
    private final Minecraft mc = Minecraft.getInstance();
    
    private final ModeSetting mode = new ModeSetting("Режим", "Gamma", "Gamma", "NightVision", "Both");
    private final SliderSetting gamma = new SliderSetting("Гамма", 16.0f, 0.0f, 100.0f, 0.5f);

    private double originalGamma;

    public Fullbright() {
        addSettings(mode, gamma);
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        originalGamma = mc.gameSettings.gamma;
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        mc.gameSettings.gamma = originalGamma;

        if (mc.player != null) {
            mc.player.removePotionEffect(Effects.NIGHT_VISION);
        }
        return false;
    }

    @Subscribe
    private void onUpdate(EventMotion e) {
        String selectedMode = mode.get();

        if (selectedMode.equals("Gamma") || selectedMode.equals("Both")) {
            mc.gameSettings.gamma = gamma.get().doubleValue();
        } else {
            mc.gameSettings.gamma = originalGamma;
        }
        
        if (selectedMode.equals("NightVision") || selectedMode.equals("Both")) {
            applyNightVision();
        } else {
            if (mc.player != null) {
                mc.player.removePotionEffect(Effects.NIGHT_VISION);
            }
        }
    }

    private void applyNightVision() {
        if (mc.player != null) {
            PlayerEntity player = mc.player;

            if (!player.isPotionActive(Effects.NIGHT_VISION)) {
                player.addPotionEffect(new EffectInstance(Effects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            }
        }
    }
}