package im.laura.functions.impl.misc;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import im.laura.events.AttackEvent;
import im.laura.events.EventPacket;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.KillAura;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.StringSetting;
import im.laura.utils.player.InventoryUtil;
import im.laura.utils.world.ExplosionUtil;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.play.server.SExplosionPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "FakePlayer", type = Category.Misc)
public class FakePlayer extends Function {

    private final BooleanSetting copyInventory = new BooleanSetting("Копировать инвентарь", false);
    private final BooleanSetting record        = new BooleanSetting("Запись",               false);
    private final BooleanSetting play          = new BooleanSetting("Воспроизведение",      false);
    private final BooleanSetting autoTotem     = new BooleanSetting("Авто тотем",           false);
    private final StringSetting  name          = new StringSetting ("Имя",                  "Laura", "");

    public static RemoteClientPlayerEntity fakePlayer;

    private final List<PlayerState> positions = new ArrayList<>();
    private int movementTick;
    private int deathTime;

    public FakePlayer() {
        addSettings(copyInventory, record, play, autoTotem, name);
    }

    @Override
    public boolean onEnable() {
        if (fullNullCheck()) return false;

        fakePlayer = new RemoteClientPlayerEntity(
                mc.world,
                new GameProfile(UUID.fromString("66123666-6666-6666-6666-666666666600"), name.get())
        );
        fakePlayer.copyLocationAndAnglesFrom(mc.player);

        if (copyInventory.get()) {
            fakePlayer.setHeldItem(Hand.MAIN_HAND, mc.player.getHeldItemMainhand().copy());
            fakePlayer.setHeldItem(Hand.OFF_HAND,  mc.player.getHeldItemOffhand().copy());
            for (int i = 0; i < 4; i++)
                fakePlayer.inventory.armorInventory.set(i, mc.player.inventory.armorInventory.get(i).copy());
        }

        mc.world.addEntity(fakePlayer.getEntityId(), fakePlayer);

        fakePlayer.addPotionEffect(new EffectInstance(Effects.REGENERATION, 9999, 2));
        fakePlayer.addPotionEffect(new EffectInstance(Effects.ABSORPTION,   9999, 4));
        fakePlayer.addPotionEffect(new EffectInstance(Effects.RESISTANCE,   9999, 1));
        return false;
    }

    @Override
    public boolean onDisable() {
        if (fakePlayer == null) return false;
        mc.world.removeEntityFromWorld(fakePlayer.getEntityId());
        fakePlayer = null;
        positions.clear();
        deathTime    = 0;
        movementTick = 0;
        return false;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onPacketReceive(EventPacket e) {
        if (fakePlayer == null) return;
        if (!(e.getPacket() instanceof SExplosionPacket)) return;

        SExplosionPacket explosion = (SExplosionPacket) e.getPacket();
        if (fakePlayer.hurtTime != 0) return;

        float damage = (float) ExplosionUtil.getAutoCrystalDamage(
                new Vector3d(explosion.getX(), explosion.getY(), explosion.getZ()),
                fakePlayer, 0, false
        );
        fakePlayer.attackEntityFrom(DamageSource.GENERIC, damage);

        if (fakePlayer.getHealth() <= 0) tryUseTotem();
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fakePlayer == null) return;

        if (record.get()) {
            positions.add(new PlayerState(
                    mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(),
                    mc.player.rotationYaw, mc.player.rotationPitch
            ));
            return;
        }

        if (play.get() && !positions.isEmpty()) {
            movementTick++;
            if (movementTick >= positions.size()) { movementTick = 0; return; }
            PlayerState p = positions.get(movementTick);
            fakePlayer.rotationYaw     = p.yaw;
            fakePlayer.rotationPitch   = p.pitch;
            fakePlayer.rotationYawHead = p.yaw;
            fakePlayer.setPositionAndUpdate(p.x, p.y, p.z);
        } else {
            movementTick = 0;
        }

        if (autoTotem.get() && fakePlayer.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING)
            fakePlayer.setHeldItem(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        if (fakePlayer.getHealth() <= 0) {
            deathTime++;
            if (deathTime > 10) disable();
        }
    }

    private void disable() {
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onAttack(AttackEvent e) {
        if (fakePlayer == null || e.getEntity() != fakePlayer) return;
        if (fakePlayer.hurtTime != 0) return;

        KillAura ka = getKillAura();
        float damage = (ka != null && ka.getAttackCooldown() >= 0.85f)
                ? InventoryUtil.getHitDamage(mc.player.getHeldItemMainhand(), fakePlayer)
                : 1f;

        fakePlayer.attackEntityFrom(DamageSource.GENERIC, damage);
        if (fakePlayer.getHealth() <= 0) tryUseTotem();
    }

    private void tryUseTotem() {
        if (fakePlayer.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING) {
            fakePlayer.setHealth(10f);
            fakePlayer.setHeldItem(Hand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private KillAura getKillAura() {
        try {
            return (KillAura) im.laura.Laura.getInstance()
                    .getFunctionRegistry().getFunctions()
                    .stream().filter(f -> f instanceof KillAura)
                    .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {}
}