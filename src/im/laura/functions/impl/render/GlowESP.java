package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.command.friends.FriendStorage;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.AntiBot;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.DisplayUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@FunctionRegister(name = "GlowESP", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava"})
public class GlowESP extends Function {

    private final ModeListSetting targets = new ModeListSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Невидимые", false));

    private final ColorSetting playersColor = new ColorSetting("Цвет игроков", 0x80FF0000);
    private final ColorSetting friendsColor = new ColorSetting("Цвет друзей", 0x8000FF00);
    private final ColorSetting mobsColor = new ColorSetting("Цвет мобов", 0x80FF00FF);
    private final ColorSetting animalsColor = new ColorSetting("Цвет животных", 0x8000FFFF);

    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность заливки", 50.0f, 0.0f, 255.0f, 5.0f);
    private final SliderSetting glowRadius = new SliderSetting("Радиус свечения", 3.0f, 1.0f, 10.0f, 0.5f);

    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", true);

    public GlowESP() {
        addSettings(targets, playersColor, friendsColor, mobsColor, animalsColor,
                   fillAlpha, glowRadius, fill, outline, throughWalls);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        for (Entity entity : mc.world.getAllEntities()) {
            if (!shouldRender(entity)) continue;
            if (!throughWalls.get() && !mc.player.canEntityBeSeen(entity)) continue;

            renderGlow(entity);
        }
    }

    private boolean shouldRender(Entity entity) {
        if (entity == mc.player) return false;
        if (entity.isSpectator()) return false;

        AntiBot antiBot = im.laura.Laura.getInstance().getFunctionRegistry().getAntiBot();
        if (antiBot != null && antiBot.isState() && AntiBot.isBot(entity)) {
            return false;
        }

        if (entity instanceof PlayerEntity player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return targets.getValueByName("Друзья").get();
            }

            if (player.isInvisible()) {
                return targets.getValueByName("Невидимые").get();
            }

            return targets.getValueByName("Игроки").get();
        }

        if (entity instanceof IMob) {
            return targets.getValueByName("Мобы").get();
        }

        if (entity instanceof AnimalEntity) {
            return targets.getValueByName("Животные").get();
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    private void renderGlow(Entity entity) {
        Color color = getColorForEntity(entity);
        if (color == null) return;

        double x = entity.lastTickPosX + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks();
        double y = entity.lastTickPosY + (entity.getPosY() - entity.lastTickPosY) * mc.getRenderPartialTicks();
        double z = entity.lastTickPosZ + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks();

        float width = entity.getWidth() + glowRadius.get();
        float height = entity.getHeight() + glowRadius.get();

        AxisAlignedBB bb = new AxisAlignedBB(
            x - width / 2, y, z - width / 2,
            x + width / 2, y + height, z + width / 2
        );

        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();
        RenderSystem.depthMask(throughWalls.get());

        if (throughWalls.get()) {
            RenderSystem.disableDepthTest();
        }

        if (fill.get()) {
            DisplayUtils.drawFilledBox(bb, new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(fillAlpha.get())));
        }

        if (outline.get()) {
            DisplayUtils.drawOutlinedBox(bb, color);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();

        if (throughWalls.get()) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.popMatrix();
    }

    private Color getColorForEntity(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return new Color(friendsColor.get(), true);
            }
            return new Color(playersColor.get(), true);
        }

        if (entity instanceof IMob) {
            return new Color(mobsColor.get(), true);
        }

        if (entity instanceof AnimalEntity) {
            return new Color(animalsColor.get(), true);
        }

        return Color.WHITE;
    }
}
