package im.laura.functions.impl.misc;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventPacket;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.KillAura;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.util.text.ITextComponent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "AutoEZ", type = Category.Misc)
public class AutoEZ extends Function {

    public static ArrayList<String> EZWORDS = new ArrayList<>();

    public final BooleanSetting global = new BooleanSetting("Глобальный",  true);
    private final ModeSetting   mode   = new ModeSetting   ("Режим",        "Basic",     "Basic", "Custom");
    private final ModeSetting   server = new ModeSetting   ("Сервер",       "Universal", "Universal", "FunnyGame");

    private final String[] EZ = {
            "%player% РЕКОД",
            "%player% ТВОЯ МАТЬ БУДЕТ СЛЕДУЮЩЕЙ",
            "%player% БИЧАРА БЕЗ LAURA",
            "%player% ЧЕ ТАК БЫСТРО СЛИЛСЯ",
            "%player% ПЛАЧЬ",
            "%player% ЗАБЫЛ КИЛЛКУ ВЫРУБИТЬ",
            "ОДНОЛЕТОШНЫЙ %player% БЫЛ ВПЕНЕН",
            "%player% ИЗИ",
            "%player% БОЖЕ МНЕ ТЕБЯ ЖАЛКО",
            "%player% ОПРАВДЫВАЙСЯ",
            "%player% СПАСИБО ЗА ИГРУ"
    };

    private LivingEntity lastTarget;
    private boolean targetWasAlive;
    private long lastEZTime = 0;

    public AutoEZ() {
        addSettings(global, mode, server);
        loadEZ();
    }

    public static void loadEZ() {
        File file = new File("Laura/misc/AutoEZ.txt");
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            }
        } catch (IOException ignored) { return; }

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

                ArrayList<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);

                ArrayList<String> result = new ArrayList<>();
                StringBuilder chunk = new StringBuilder();
                for (String l : lines) {
                    if (l.isEmpty()) {
                        if (!chunk.isEmpty()) {
                            result.add(chunk.toString().trim());
                            chunk = new StringBuilder();
                        }
                    } else {
                        chunk.append(l).append(" ");
                    }
                }
                if (!chunk.isEmpty()) result.add(chunk.toString().trim());

                EZWORDS.clear();
                EZWORDS.addAll(result.isEmpty() ? lines : result);
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        loadEZ();
        lastTarget = null;
        targetWasAlive = false;
        return false;
    }

    // ─── Universal: детектируем смерть цели ─────────────────────────────────
    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;
        if (!server.is("Universal")) return;

        KillAura ka = getKillAura();
        if (ka == null) return;

        LivingEntity target = ka.getTarget();

        if (target == null) {
            if (lastTarget != null && targetWasAlive
                    && (!lastTarget.isAlive() || lastTarget.getHealth() <= 0)) {
                sayEZ(lastTarget.getName().getString());
            }
            lastTarget = null;
            targetWasAlive = false;
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            targetWasAlive = target.isAlive() && target.getHealth() > 0;
        } else if (targetWasAlive && (!target.isAlive() || target.getHealth() <= 0)) {
            sayEZ(target.getName().getString());
            targetWasAlive = false;
        }
    }

    // ─── FunnyGame: системный чат ────────────────────────────────────────────
    @Subscribe
    @SuppressWarnings("unused")
    public void onPacket(EventPacket e) {
        if (fullNullCheck()) return;
        if (!server.is("FunnyGame")) return;

        // Фильтруем только входящие пакеты чата
        if (!e.isReceive()) return;
        if (!(e.getPacket() instanceof SChatPacket packet)) return;

        ITextComponent component = packet.getChatComponent();
        if (component == null) return;
        String message = component.getString();

        if (message.contains("убил") || message.contains("Вы убили")) {
            String name = extractName(message);
            if (name != null && !name.equals(mc.player.getName().getString())) {
                sayEZ(name);
            }
        }
    }

    // ─── Отправка ────────────────────────────────────────────────────────────
    public void sayEZ(String playerName) {
        if (mc.player == null || mc.player.connection == null) return;

        long now = System.currentTimeMillis();
        if (now - lastEZTime < 2000) return;
        lastEZTime = now;

        String finalWord;
        if (mode.is("Basic")) {
            finalWord = EZ[(int) (Math.random() * EZ.length)];
        } else {
            if (EZWORDS.isEmpty()) return;
            finalWord = EZWORDS.get(new Random().nextInt(EZWORDS.size()));
        }

        finalWord = finalWord.replace("%player%", playerName);
        String msg = global.get() ? "!" + finalWord : finalWord;

        mc.player.connection.sendPacket(new CChatMessagePacket(msg));
    }

    private String extractName(String message) {
        try {
            String[] parts = message.split(" ");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].contains("убил")) return parts[i + 1];
            }
            return parts[parts.length - 1];
        } catch (Exception e) { return null; }
    }

    private KillAura getKillAura() {
        try {
            return (KillAura) im.laura.Laura.getInstance()
                    .getFunctionRegistry().getFunctions()
                    .stream().filter(f -> f instanceof KillAura)
                    .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }
}