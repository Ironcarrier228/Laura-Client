package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;

@FunctionRegister(name = "AutoClicker", type = Category.Combat)
public class AutoClicker extends Function {

    private final SliderSetting cps = new SliderSetting("CPS", 12f, 1f, 20f, 1f);
    private final BooleanSetting blockHit = new BooleanSetting("BlockHit", true);
    private final BooleanSetting onlySword = new BooleanSetting("Only Sword", true);
    private final BooleanSetting swing = new BooleanSetting("Swing", true);

    private long lastClickTime = 0;

    public AutoClicker() {
        addSettings(cps, blockHit, onlySword, swing);
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка на меч в руке
        if (onlySword.get()) {
            ItemStack stack = mc.player.getHeldItemMainhand();
            if (!(stack.getItem() instanceof SwordItem)) {
                return;
            }
        }

        // Проверка зажатой кнопки атаки
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }

        // Расчет задержки между кликами
        long delay = (long) (1000f / cps.get());

        if (System.currentTimeMillis() - lastClickTime >= delay) {
            // Клик
            mc.clickMouse();
            
            // BlockHit - сброс кулдауна
            if (blockHit.get()) {
                mc.player.resetCooldown();
            }

            // Swing анимация
            if (swing.get()) {
                mc.player.swingArm(net.minecraft.util.Hand.MAIN_HAND);
            }

            lastClickTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onDisable() {
        return super.onDisable();
    }
}
