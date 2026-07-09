package im.laura.command.friends;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import im.laura.utils.client.IMinecraft;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static java.io.File.separator;

@UtilityClass
public class FriendStorage implements IMinecraft {

    @Getter
    private int color = new Color(128, 255, 128).getRGB();

    @Getter
    private final Set<String> friends = new HashSet<>();

    private final File file = new File(mc.gameDir, separator + "laura" + separator + "files" + separator + "friends.cfg");

    @SneakyThrows
    public void load() {
        if (file.exists()) {
            friends.addAll(Files.readAllLines(file.toPath()));
        } else {
            // Исправлено: добавлены проверки для успешного создания папок и файла
            boolean dirsCreated = file.getParentFile().mkdirs();
            boolean fileCreated = file.createNewFile();

            if (!fileCreated && !file.exists()) {
                System.err.println("Не удалось создать файл конфигурации друзей: " + file.getAbsolutePath());
            }
        }
    }

    public void add(String name) {
        friends.add(name);
        save();
    }

    public void remove(String name) {
        friends.remove(name);
        save();
    }

    public void clear() {
        friends.clear();
        save();
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }

    @SneakyThrows
    private void save() {
        Files.write(file.toPath(), friends);
    }
}
