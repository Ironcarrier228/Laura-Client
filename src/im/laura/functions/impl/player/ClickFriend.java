package im.laura.functions.impl.player;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventKey;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.settings.impl.BindSetting;
import im.laura.utils.player.PlayerUtils;
import net.minecraft.entity.player.PlayerEntity;
import im.laura.command.friends.FriendStorage;
import im.laura.functions.api.FunctionRegister;

@FunctionRegister(name = "ClickFriend", type = Category.Player)
public class ClickFriend extends Function {
    final BindSetting throwKey = new BindSetting("Кнопка", -98);
    public ClickFriend() {
        addSettings(throwKey);
    }
    @Subscribe
    public void onKey(EventKey e) {
        if (e.getKey() == throwKey.get() && mc.pointedEntity instanceof PlayerEntity) {

            if (mc.player == null || mc.pointedEntity == null) {
                return;
            }

            String playerName = mc.pointedEntity.getName().getString();

            if (!PlayerUtils.isNameValid(playerName)) {
                print("Невозможно добавить бота в друзья, увы, как бы вам не хотелось это сделать");
                return;
            }

            if (FriendStorage.isFriend(playerName)) {
                FriendStorage.remove(playerName);
                printStatus(playerName, true);
            } else {
                FriendStorage.add(playerName);
                printStatus(playerName, false);
            }
        }
    }

    void printStatus(String name, boolean remove) {
        if (remove) print(name + " удалён из друзей");
        else print(name + " добавлен в друзья");
    }
}
