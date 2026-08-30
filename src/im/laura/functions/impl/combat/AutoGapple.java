package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.InventoryUtil;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.AirItem;
import net.minecraft.item.Items;
import net.minecraft.potion.Effects;

@FunctionRegister(name = "AutoGapple", type = Category.Combat)
public class AutoGapple extends Function {
    private final SliderSetting healthSetting = new SliderSetting("Здоровье", 16.0f, 1.0f, 20.0f, 0.05f);
    private final BooleanSetting eatAtStart = new BooleanSetting("Съесть в начале", true);
    private final BooleanSetting onlyLowHP = new BooleanSetting("Только низкое HP", false);
    private final BooleanSetting autoOffhand = new BooleanSetting("Авто оффхенд", true);
    private final SliderSetting regenDelay = new SliderSetting("Реген задержка", 500f, 0f, 2000f, 50f);

    private boolean isEating;
    private final StopWatch stopWatch = new StopWatch();
    private final StopWatch regenTimer = new StopWatch();

    public AutoGapple() {
        addSettings(healthSetting, eatAtStart, onlyLowHP, autoOffhand, regenDelay);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        // Всегда пытаемся взять яблоко в оффхенд, если его там нет и есть яблоки в инвентаре
        if (shouldTakeGapple()) {
            takeGappleInOffHand();
        }

        if (shouldEat()) {
            startEating();
        } else if (isEating) {
            stopEating();
        }
    }

    private boolean shouldTakeGapple() {
        if (!autoOffhand.get()) return false;
        
        // Нет яблока в оффхенде
        boolean noAppleInOffHand = !(mc.player.getHeldItemOffhand().getItem() == Items.GOLDEN_APPLE);
        // Есть эффекты абсорбции или регенерации (значит яблоко недавно съедено)
        boolean hasAppleEffect = mc.player.getAbsorptionAmount() > 0.0f || mc.player.isPotionActive(Effects.REGENERATION);
        // Низкое здоровье
        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean lowHealth = currentHealth <= healthSetting.get();
        // Начало игры
        boolean startGame = mc.player.ticksExisted < 100;
        // Есть яблоки в инвентаре
        boolean hasGappleInInventory = InventoryUtil.getInstance().getSlotInInventory(Items.GOLDEN_APPLE) != -1;
        
        // Берём яблоко если: нет в оффхенде И есть в инвентаре И (начало игры ИЛИ низкое HP ИЛИ нет эффектов)
        return noAppleInOffHand && hasGappleInInventory && (startGame || lowHealth || !hasAppleEffect);
    }

    private boolean shouldEat() {
        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean noAppleEffect = mc.player.getAbsorptionAmount() == 0.0f || !mc.player.isPotionActive(Effects.REGENERATION);
        boolean lowHealth = currentHealth <= healthSetting.get();
        boolean startGame = mc.player.ticksExisted < 100 && noAppleEffect;

        // Проверяем, есть ли яблоко в оффхенде
        boolean hasGappleInOffhand = mc.player.getHeldItemOffhand().getItem() == Items.GOLDEN_APPLE;

        if (onlyLowHP.get() && !lowHealth) return false;

        return (lowHealth || startGame) && hasGappleInOffhand && !isGappleOnCooldown() && regenTimer.isReached(regenDelay.get().longValue());
    }

    private void takeGappleInOffHand() {
        if (!autoOffhand.get()) return;

        int gappleSlot = InventoryUtil.getInstance().getSlotInInventory(Items.GOLDEN_APPLE);
        if (gappleSlot >= 0) {
            moveGappleToOffhand(gappleSlot);
        }
    }

    private void moveGappleToOffhand(int gappleSlot) {
        int slot = gappleSlot < 9 ? gappleSlot + 36 : gappleSlot;

        mc.playerController.windowClick(0, slot, 0, ClickType.PICKUP, mc.player);
        mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, mc.player);

        if (!(mc.player.getHeldItemOffhand().getItem() instanceof AirItem)) {
            mc.playerController.windowClick(0, slot, 0, ClickType.PICKUP, mc.player);
        }
        stopWatch.reset();
    }

    private void startEating() {
        if (mc.currentScreen != null) {
            mc.currentScreen.passEvents = true;
        }
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) {
            mc.gameSettings.keyBindUseItem.setPressed(true);
            isEating = true;
        }
    }

    private void stopEating() {
        if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
            mc.gameSettings.keyBindUseItem.setPressed(false);
        }
        isEating = false;
        regenTimer.reset();
    }

    private boolean isGappleOnCooldown() {
        return mc.player.getCooldownTracker().hasCooldown(Items.GOLDEN_APPLE);
    }

    @Override
    public boolean onDisable() {
        stopEating();
        stopWatch.reset();
        regenTimer.reset();
        super.onDisable();
        return false;
    }
}
