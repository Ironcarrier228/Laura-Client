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
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@FunctionRegister(name = "ArmorHUD", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class ArmorHUD extends Function {

    private static final int MAX_RENDER_ERRORS = 50;

    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int BAR_TRACK_COLOR = 0xFF262626;
    private static final int COUNT_COLOR = 0xFFE8E8E8;
    private static final int NO_TOTEM_COLOR = 0xFFFF5555;
    private static final int ENCHANTED_COLOR = 0xFFC77DFF;
    private static final int ENCHANT_COLOR = 0xFFA9B7D0;

    // приоритет зачарований: показываем самое важное первым
    private static final Enchantment[] ENCH_PRIORITY = {
            Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.BLAST_PROTECTION,
            Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION, Enchantments.THORNS,
            Enchantments.SHARPNESS, Enchantments.POWER, Enchantments.PUNCH, Enchantments.FLAME,
            Enchantments.EFFICIENCY, Enchantments.FORTUNE, Enchantments.SILK_TOUCH,
            Enchantments.INFINITY, Enchantments.MENDING, Enchantments.UNBREAKING
    };
    private static final String[] ENCH_ABBR = {
            "Prot", "Proj", "Blast", "FF", "Resp", "Thorns",
            "Sharp", "Power", "Punch", "Flame",
            "Eff", "Fort", "Silk", "Inf", "Mend", "Unbr"
    };

    private final ModeSetting position = new ModeSetting("Позиция", "Низ центр",
            "Низ центр", "Низ слева", "Низ справа", "Верх центр", "Верх слева", "Верх справа");
    private final SliderSetting offsetX = new SliderSetting("Сдвиг X", 0.0f, -300.0f, 300.0f, 1.0f);
    private final SliderSetting offsetY = new SliderSetting("Сдвиг Y", 0.0f, -300.0f, 300.0f, 1.0f);
    private final ModeSetting orientation = new ModeSetting("Ориентация", "Горизонтально", "Горизонтально", "Вертикально");
    private final SliderSetting itemScale = new SliderSetting("Размер предметов", 1.0f, 0.5f, 2.0f, 0.05f);
    private final BooleanSetting mainHand = new BooleanSetting("Основная рука", true);
    private final BooleanSetting offHand = new BooleanSetting("Вторая рука", true);
    private final BooleanSetting totems = new BooleanSetting("Тотемы", true);
    private final BooleanSetting gapples = new BooleanSetting("Яблоки", true);
    private final ModeSetting durabilityMode = new ModeSetting("Прочность", "Оба", "Полоска", "Число", "Оба", "Выкл");
    private final BooleanSetting enchants = new BooleanSetting("Зачарования", true);
    private final BooleanSetting background = new BooleanSetting("Фон", true);

    private int renderErrors = 0;

    public ArmorHUD() {
        addSettings(position, offsetX, offsetY, orientation, itemScale,
                mainHand, offHand, totems, gapples,
                durabilityMode, enchants, background);
    }

    @Subscribe
    public void onWorldLoad(EventChangeWorld e) {
        renderErrors = 0;
    }

    @Subscribe
    public void onRender(EventDisplay e) {
        if (e.getType() != EventDisplay.Type.POST) return;
        if (mc.player == null || mc.world == null) return;
        if (renderErrors > MAX_RENDER_ERRORS) return;

        try {
            render(e.getMatrixStack());
        } catch (Exception ex) {
            renderErrors++;
            if (renderErrors == 1) {
                System.err.println("[ArmorHUD] render error: " + ex);
            }
        }
    }

    // ==================== ДАННЫЕ ====================

    private List<Slot> buildSlots() {
        List<Slot> slots = new ArrayList<>();
        NonNullList<ItemStack> armor = mc.player.inventory.armorInventory;

        // шлем, нагрудник, штаны, ботинки (armorInventory: 3=голова, 0=ноги)
        slots.add(slot(armor.get(3)));
        slots.add(slot(armor.get(2)));
        slots.add(slot(armor.get(1)));
        slots.add(slot(armor.get(0)));

        if (mainHand.get()) slots.add(slot(mc.player.getHeldItemMainhand()));
        if (offHand.get()) slots.add(slot(mc.player.getHeldItemOffhand()));

        if (totems.get()) {
            int count = countItem(Items.TOTEM_OF_UNDYING); // включает оффхенд
            slots.add(new Slot(new ItemStack(Items.TOTEM_OF_UNDYING),
                    String.valueOf(count), count == 0 ? NO_TOTEM_COLOR : COUNT_COLOR, false));
        }

        if (gapples.get()) {
            int normal = countItem(Items.GOLDEN_APPLE);
            int enchanted = countItem(Items.ENCHANTED_GOLDEN_APPLE);
            int total = normal + enchanted;
            int color = enchanted > 0 ? ENCHANTED_COLOR : (total == 0 ? 0xFF909090 : COUNT_COLOR);
            slots.add(new Slot(new ItemStack(Items.GOLDEN_APPLE), String.valueOf(total), color, false));
        }

        return slots;
    }

    private Slot slot(ItemStack stack) {
        String label = null;
        if (!stack.isEmpty() && stack.getCount() > 1) {
            label = "x" + stack.getCount();
        }
        return new Slot(stack, label, COUNT_COLOR, !stack.isEmpty() && stack.isDamageable());
    }

    private int countItem(Item item) {
        int count = 0;
        for (ItemStack s : mc.player.inventory.offHandInventory) {
            if (s.getItem() == item) count += s.getCount();
        }
        for (ItemStack s : mc.player.inventory.mainInventory) {
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    // ==================== РЕНДЕР ====================

    private void render(MatrixStack matrixStack) {
        List<Slot> slots = buildSlots();
        if (slots.isEmpty()) return;

        float scale = itemScale.get();
        float itemSize = 16f * scale;
        float gap = 3f * scale;
        float pad = 1.5f * scale;

        String durMode = durabilityMode.get();
        boolean showBar = durMode.equals("Полоска") || durMode.equals("Оба");
        boolean showNum = durMode.equals("Число") || durMode.equals("Оба");

        float numSize = Math.max(5f, 7f * scale);
        float numH = Fonts.montserrat.getHeight(numSize) + 1f;
        float enchSize = Math.max(4.5f, 6f * scale);
        float enchH = Fonts.montserrat.getHeight(enchSize) + 1f;
        float barH = Math.max(1.5f, 2.5f * scale);

        boolean vertical = orientation.get().equals("Вертикально");
        int n = slots.size();

        // раскладка: высота каждого слота
        List<SlotLayout> layout = new ArrayList<>(n);
        float maxH = 0f;
        float sumH = 0f;
        for (Slot slot : slots) {
            String ench = enchants.get() ? getEnchantLabel(slot.stack()) : null;
            boolean dur = slot.durability();
            float h = itemSize;
            if (dur && showBar) h += barH + 1f;
            if ((dur && showNum) || slot.label() != null) h += numH;
            if (ench != null) h += enchH;
            layout.add(new SlotLayout(slot, ench, dur, h));
            maxH = Math.max(maxH, h);
            sumH += h;
        }

        float totalW = vertical ? itemSize : n * itemSize + (n - 1) * gap;
        float totalH = vertical ? sumH + (n - 1) * gap : maxH;

        float sw = mc.getMainWindow().getScaledWidth();
        float sh = mc.getMainWindow().getScaledHeight();
        float dx = offsetX.get(), dy = offsetY.get();
        String pos = position.get();

        float x, y;
        switch (pos) {
            case "Верх центр" -> { x = (sw - totalW) / 2f + dx; y = 4f + dy; }
            case "Верх слева" -> { x = 4f + dx; y = 4f + dy; }
            case "Верх справа" -> { x = sw - totalW - 4f + dx; y = 4f + dy; }
            case "Низ слева" -> { x = 4f + dx; y = sh - totalH - 4f + dy; }
            case "Низ справа" -> { x = sw - totalW - 4f + dx; y = sh - totalH - 4f + dy; }
            default -> { x = (sw - totalW) / 2f + dx; y = sh - totalH - 55f + dy; } // "Низ центр" — над хотбаром и сердцами
        }

        // координаты слотов
        float[] sx = new float[n];
        float[] sy = new float[n];
        if (vertical) {
            float cursor = y;
            for (int i = 0; i < n; i++) {
                sx[i] = x;
                sy[i] = cursor;
                cursor += layout.get(i).height() + gap;
            }
        } else {
            for (int i = 0; i < n; i++) {
                sx[i] = x + i * (itemSize + gap);
                sy[i] = y + (maxH - layout.get(i).height()) / 2f;
            }
        }

        // === проход 1: фон слотов ===
        if (background.get()) {
            for (int i = 0; i < n; i++) {
                SlotLayout l = layout.get(i);
                drawRect(matrixStack, sx[i] - pad, sy[i] - pad,
                        itemSize + pad * 2f, l.height() + pad * 2f, BACKGROUND_COLOR);
            }
        }

        // === проход 2: предметы (ванильный рендер) ===
        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        for (int i = 0; i < n; i++) {
            ItemStack stack = layout.get(i).slot().stack();
            if (stack.isEmpty()) continue;
            RenderSystem.pushMatrix();
            RenderSystem.translatef(sx[i], sy[i], 0f);
            RenderSystem.scalef(scale, scale, 1f);
            mc.getItemRenderer().renderItemAndEffectIntoGUI(stack, 0, 0);
            RenderSystem.popMatrix();
        }
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        if (!depthWas) RenderSystem.disableDepthTest();

        // === проход 3: полоски, числа, зачарования ===
        for (int i = 0; i < n; i++) {
            SlotLayout l = layout.get(i);
            Slot slot = l.slot();
            float cx = sx[i];
            float cy = sy[i] + itemSize;

            if (l.durability() && showBar) {
                float ratio = durabilityRatio(slot.stack());
                drawRect(matrixStack, cx, cy, itemSize, barH, BAR_TRACK_COLOR);
                if (ratio > 0f) {
                    drawRect(matrixStack, cx, cy, itemSize * ratio, barH, durabilityColor(ratio));
                }
                cy += barH + 1f;
            }

            String numText = null;
            int numColor = COUNT_COLOR;
            if (l.durability() && showNum) {
                numText = String.valueOf(slot.stack().getMaxDamage() - slot.stack().getDamage());
                numColor = durabilityColor(durabilityRatio(slot.stack()));
            } else if (slot.label() != null) {
                numText = slot.label();
                numColor = slot.labelColor();
            }
            if (numText != null) {
                float w = Fonts.montserrat.getWidth(numText, numSize);
                Fonts.montserrat.drawText(matrixStack, numText, cx + (itemSize - w) / 2f, cy, numColor, numSize);
                cy += numH;
            }

            if (l.enchant() != null) {
                float w = Fonts.montserrat.getWidth(l.enchant(), enchSize);
                Fonts.montserrat.drawText(matrixStack, l.enchant(), cx + (itemSize - w) / 2f, cy, ENCHANT_COLOR, enchSize);
            }
        }
    }

    // ==================== ХЕЛПЕРЫ ====================

    private float durabilityRatio(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (max - stack.getDamage()) / (float) max));
    }

    private int durabilityColor(float ratio) {
        // зелёный -> жёлтый -> красный (как HP-бар в Nametags)
        return Color.HSBtoRGB(ratio / 3f, 0.85f, 0.95f) | 0xFF000000;
    }

    private String getEnchantLabel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (int i = 0; i < ENCH_PRIORITY.length; i++) {
            int lvl = EnchantmentHelper.getEnchantmentLevel(ENCH_PRIORITY[i], stack);
            if (lvl > 0) {
                return ENCH_ABBR[i] + " " + roman(lvl);
            }
        }
        return null;
    }

    private static String roman(int lvl) {
        return switch (lvl) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(lvl);
        };
    }

    @SuppressWarnings("deprecation")
    private void drawRect(MatrixStack matrixStack, float x, float y, float width, float height, int color) {
        Matrix4f matrix = matrixStack.getLast().getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();

        float a = (color >>> 24) / 255f, r = ((color >> 16) & 0xFF) / 255f,
                g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(matrix, x, y, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x, y + height, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x + width, y + height, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x + width, y, 0).color(r, g, b, a).endVertex();
        tessellator.draw();

        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private record Slot(ItemStack stack, String label, int labelColor, boolean durability) {}
    private record SlotLayout(Slot slot, String enchant, boolean durability, float height) {}
}