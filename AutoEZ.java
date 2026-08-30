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
import net.minecraft.network.play.server.SChatPacket;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "AutoEZ", type = Category.Misc)
public class AutoEZ extends Function {

    public static ArrayList<String> EZWORDS = new ArrayList<>();

    public final BooleanSetting global = new BooleanSetting("Глобальный", true);
    private final ModeSetting   mode   = new ModeSetting   ("Режим",      "Basic",     "Basic", "Custom");
    private final ModeSetting   server = new ModeSetting   ("Сервер",     "Universal", "Universal", "FunnyGame");

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
            "%player% СПС ЗА ОТСОС"
    };

    private LivingEntity lastTarget;
    private boolean targetWasAlive;

    public AutoEZ() {
        addSettings(global, mode, server);
        loadEZ();
    }

    public static void loadEZ() {
        try {
            File file = new File("Laura/misc/AutoEZ.txt");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                    ArrayList<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) lines.add(line);
                    reader.close();

                    EZWORDS.clear();
                    ArrayList<String> result = new ArrayList<>();
                    StringBuilder chunk = new StringBuilder();
                    for (String l : lines) {
                        if (l.isEmpty()) {
                            if (chunk.length() > 0) {
                                result.add(chunk.toString().trim());
                                chunk = new StringBuilder();
                            }
                        } else {
                            chunk.append(l).append(" ");
                        }
                    }
                    if (chunk.length() > 0) result.add(chunk.toString().trim());
                    EZWORDS = result.isEmpty() ? lines : result;
                } catch (Exception ignored) {}
            }).start();
        } catch (IOException ignored) {}
    }

    @Override
    public void onEnable() {
        loadEZ();
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;
        if (!server.is("Universal")) return;

        KillAura ka = getKillAura();
        if (ka == null) return;

        LivingEntity target = ka.getTarget();

        if (target == null) {
            if (lastTarget != null && targetWasAlive && lastTarget.getHealth() <= 0) {
                sayEZ(lastTarget.getName().getString());
            }
            lastTarget = null;
            targetWasAlive = false;
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            targetWasAlive = target.getHealth() > 0;
        } else if (targetWasAlive && target.getHealth() <= 0) {
            sayEZ(target.getName().getString());
            targetWasAlive = false;
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onPacketReceive(EventPacket e) {
        if (fullNullCheck()) return;
        if (!server.is("FunnyGame")) return;
        if (e.isOutgoing()) return;

        if (e.getPacket() instanceof SChatPacket) {
            SChatPacket packet = (SChatPacket) e.getPacket();
            String message = packet.getChatComponent().getString();
            if (message.contains("Вы убили игрока")) {
                String name = solveName(message);
                if (!Objects.equals(name, "FATAL ERROR")) sayEZ(name);
            }
        }
    }

    public void sayEZ(String playerName) {
        if (mc.player == null || mc.player.connection == null) return;
        String finalWord;
        if (mode.is("Basic")) {
            finalWord = EZ[(int) (Math.random() * EZ.length)];
        } else {
            if (EZWORDS.isEmpty()) { sendMessage("Файл AutoEZ.txt пустой!"); return; }
            finalWord = EZWORDS.get(new Random().nextInt(EZWORDS.size()));
        }
        finalWord = finalWord.replace("%player%", playerName);
        String msg = global.get() ? "!" + finalWord : finalWord;
        mc.player.connection.sendChatMessage(msg);
    }

    private String solveName(String message) {
        try {
            String[] parts = message.split(" ");
            return parts[parts.length - 1];
        } catch (Exception e) { return "FATAL ERROR"; }
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
