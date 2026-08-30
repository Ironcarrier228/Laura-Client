package im.laura.functions.impl.player;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventDisplay;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.util.math.MathHelper;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "AntiAim", type = Category.Player)
public class AntiAim extends Function {

    private final ModeSetting    pitchMode     = new ModeSetting  ("Режим pitch",   "None",  "None", "RandomAngle", "Spin", "Sinus", "Fixed", "Static", "Jitter");
    private final ModeSetting    yawMode       = new ModeSetting  ("Режим yaw",     "None",  "None", "RandomAngle", "Spin", "Sinus", "Fixed", "Static", "Jitter");
    private final SliderSetting  speed         = new SliderSetting("Скорость",      1f,  1f,    45f,   1f);
    private final SliderSetting  yawDelta      = new SliderSetting("Yaw Delta",     60f, -360f, 360f,  1f);
    private final SliderSetting  pitchDelta    = new SliderSetting("Pitch Delta",   10f, -90f,  90f,   1f);
    private final SliderSetting  yawOffset     = new SliderSetting("Yaw Offset",    0f,  -180f, 180f,  1f);
    private final BooleanSetting bodySync      = new BooleanSetting("Body Sync",    true);
    private final BooleanSetting allowInteract = new BooleanSetting("Allow Interact", true);

    private float rotationYaw, rotationPitch;
    private float pitchSinusStep, yawSinusStep;

    public AntiAim() {
        addSettings(pitchMode, yawMode, speed, yawDelta, pitchDelta, yawOffset, bodySync, allowInteract);
    }

    // Вызывается перед рендером кадра — аналог EventSync для применения фейковых углов
    @Subscribe
    @SuppressWarnings("unused")
    public void onDisplay(EventDisplay e) {
        if (fullNullCheck()) return;
        if (allowInteract.get() && mc.gameSettings.keyBindAttack.isKeyDown()) return;

        double gcdFix = Math.pow(mc.gameSettings.mouseSensitivity * 0.6 + 0.2, 3.0) * 1.2;

        if (!yawMode.is("None")) {
            mc.player.rotationYaw = (float) (rotationYaw - (rotationYaw - mc.player.rotationYaw) % gcdFix);
            if (bodySync.get())
                mc.player.renderYawOffset = rotationYaw;
        }

        if (!pitchMode.is("None"))
            mc.player.rotationPitch = (float) (rotationPitch - (rotationPitch - mc.player.rotationPitch) % gcdFix);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;

        int spd = Math.max(1, speed.get().intValue());

        // ── PITCH ─────────────────────────────────────────────────────────────
        if (pitchMode.is("RandomAngle") && mc.player.ticksExisted % spd == 0)
            rotationPitch = (float) (Math.random() * 180.0 - 90.0);

        if (pitchMode.is("Spin") && mc.player.ticksExisted % spd == 0) {
            rotationPitch += pitchDelta.get();
            if (rotationPitch >  90) rotationPitch = -90;
            if (rotationPitch < -90) rotationPitch =  90;
        }

        if (pitchMode.is("Sinus")) {
            pitchSinusStep += spd / 10f;
            rotationPitch = MathHelper.clamp(
                    (float) (mc.player.rotationPitch + pitchDelta.get() * Math.sin(pitchSinusStep)),
                    -90f, 90f);
        }

        if (pitchMode.is("Fixed"))
            rotationPitch = pitchDelta.get();

        if (pitchMode.is("Static"))
            rotationPitch = MathHelper.clamp(mc.player.rotationPitch + pitchDelta.get(), -90f, 90f);

        if (pitchMode.is("Jitter")) {
            if (mc.player.ticksExisted % (spd * 2) == 0)
                rotationPitch =  pitchDelta.get() / 2f;
            if (mc.player.ticksExisted % (spd * 2) == spd)
                rotationPitch = -pitchDelta.get() / 2f;
        }

        // ── YAW ───────────────────────────────────────────────────────────────
        if (yawMode.is("RandomAngle") && mc.player.ticksExisted % spd == 0)
            rotationYaw = (float) (Math.random() * 360.0);

        if (yawMode.is("Spin") && mc.player.ticksExisted % spd == 0) {
            rotationYaw += yawDelta.get();
            if (rotationYaw > 360) rotationYaw = 0;
            if (rotationYaw < 0)   rotationYaw = 360;
        }

        if (yawMode.is("Sinus")) {
            yawSinusStep += spd / 10f;
            rotationYaw = (float) (mc.player.rotationYaw + yawDelta.get() * Math.sin(yawSinusStep) + yawOffset.get());
        }

        if (yawMode.is("Fixed"))
            rotationYaw = yawDelta.get();

        if (yawMode.is("Static"))
            rotationYaw = mc.player.rotationYaw % 360 + yawDelta.get();

        if (yawMode.is("Jitter")) {
            if (mc.player.ticksExisted % (spd * 2) == 0)
                rotationYaw =  yawDelta.get() / 2f + yawOffset.get() + mc.player.rotationYaw;
            if (mc.player.ticksExisted % (spd * 2) == spd)
                rotationYaw = -yawDelta.get() / 2f + yawOffset.get() + mc.player.rotationYaw;
        }
    }
}