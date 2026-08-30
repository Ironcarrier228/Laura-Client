package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.EventChangeWorld;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.DisplayUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "Search", type = Category.Render)
public class Search extends Function {

    private final SliderSetting radius = new SliderSetting("Радиус", 64, 16, 256, 16);
    private final ColorSetting foundColor = new ColorSetting("Цвет найденного", 0x80FFFF00);
    private final BooleanSetting showESP = new BooleanSetting("Показать ESP", true);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    
    private final ModeListSetting blocks = new ModeListSetting("Блоки",
            new BooleanSetting("Алмаз", true),
            new BooleanSetting("Золото", true),
            new BooleanSetting("Железо", true),
            new BooleanSetting("Редстоун", true),
            new BooleanSetting("Изумруд", true),
            new BooleanSetting("Уголь", false),
            new BooleanSetting("Лазурит", false),
            new BooleanSetting("Кварц", false),
            new BooleanSetting("Древние обломки", true),
            new BooleanSetting("Сундук", true),
            new BooleanSetting("Эндер сундук", true));

    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();

    public Search() {
        addSettings(radius, foundColor, showESP, fill, outline, blocks);
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        searchBlocks();
        return false;
    }

    @Subscribe
    private void onWorldLoad(EventChangeWorld e) {
        foundBlocks.clear();
    }

    @Subscribe
    private void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        // Обновляем поиск каждые 10 тиков
        if (mc.player.ticksExisted % 10 == 0) {
            searchBlocks();
        }

        if (showESP.get()) {
            renderFoundBlocks(e.getMatrixStack());
        }
    }

    private void searchBlocks() {
        foundBlocks.clear();

        int r = Math.round(radius.get());
        BlockPos playerPos = mc.player.getPosition();
        
        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
            for (int y = playerPos.getY() - r; y <= playerPos.getY() + r; y++) {
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    Block block = state.getBlock();
                    
                    if (shouldSearch(block)) {
                        foundBlocks.put(pos, block);
                    }
                }
            }
        }
    }

    private boolean shouldSearch(Block block) {
        if (block == Blocks.DIAMOND_ORE && getSettingValue("Алмаз")) return true;
        if (block == Blocks.GOLD_ORE && getSettingValue("Золото")) return true;
        if (block == Blocks.IRON_ORE && getSettingValue("Железо")) return true;
        if (block == Blocks.REDSTONE_ORE && getSettingValue("Редстоун")) return true;
        if (block == Blocks.EMERALD_ORE && getSettingValue("Изумруд")) return true;
        if (block == Blocks.COAL_ORE && getSettingValue("Уголь")) return true;
        if (block == Blocks.LAPIS_ORE && getSettingValue("Лазурит")) return true;
        if (block == Blocks.NETHER_QUARTZ_ORE && getSettingValue("Кварц")) return true;
        if (block == Blocks.ANCIENT_DEBRIS && getSettingValue("Древние обломки")) return true;
        if (block == Blocks.CHEST && getSettingValue("Сундук")) return true;
        if (block == Blocks.ENDER_CHEST && getSettingValue("Эндер сундук")) return true;
        
        return false;
    }

    private boolean getSettingValue(String name) {
        for (var setting : blocks.get()) {
            if (setting.getName().equals(name)) {
                return setting.get();
            }
        }
        return false;
    }

    private void renderFoundBlocks(MatrixStack matrixStack) {
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();

        Color color = new Color(foundColor.get(), true);
        Vector3d playerPos = mc.player.getPositionVec();

        for (Map.Entry<BlockPos, Block> entry : foundBlocks.entrySet()) {
            BlockPos pos = entry.getKey();

            // Проверяем, в радиусе ли рендера
            double dist = playerPos.squareDistanceTo(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
            if (dist > radius.get() * radius.get()) continue;

            AxisAlignedBB bb = new AxisAlignedBB(pos);

            if (fill.get()) {
                DisplayUtils.drawFilledBox(bb, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
            }

            if (outline.get()) {
                DisplayUtils.drawOutlinedBox(bb, color);
            }
        }

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
    }
}
