package im.laura.events;

import im.laura.Laura;

/**
 * Событие смены мира/подключения к серверу
 * Используется для обновления версии сервера через VersionManager
 */
public class EventChangeWorld {
    
    public EventChangeWorld() {
        // Обновляем версию сервера при смене мира
        if (Laura.getInstance() != null && Laura.getInstance().getVersionManager() != null) {
            Laura.getInstance().getVersionManager().onServerConnect();
        }
    }
}
