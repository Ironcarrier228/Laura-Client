# Laura Client

**Minecraft 1.16.5 модифицированный клиент с поддержкой Lua-скриптинга**

## Обзор проекта

Laura Client — это кастомный клиент Minecraft версии 1.16.5, модифицированный для:
- Работы на мобильных устройствах через **PojavLauncher/Mojo**
- Поддержки **Lua-скриптов** (через `laura/` директорию)
- Мультиверсионной совместимости через **ViaVersion/ViaBackwards**
- Кастомной аутентификации (MinecraftAuth, authlib)
- **GUI лоадера** на Python (tkinter) с KeyAuth авторизацией

### Основные технологии

| Компонент | Версия/Описание |
|-----------|-----------------|
| Minecraft | 1.16.5 (pack_format: 6) |
| Java | JDK 21 (Hotspot) |
| Python | 3.x (для лоадера) |
| IDE | IntelliJ IDEA / VS Code |
| Lua-скриптинг | Кастомный модуль `laura` |
| Прокси-версии | ViaVersion-5.6.0, ViaBackwards-5.6.0 |
| LWJGL | 3.2.2 |
| KeyAuth | Для авторизации лоадера |

### Структура проекта

```
Laura Client/
├── src/                      # Исходный код Minecraft + модификации
│   ├── net/minecraft/        # Основной код Minecraft
│   ├── im/laura/             # Кастомные модули Laura Client
│   │   ├── functions/        # Чит-функции (Combat, Movement, Render...)
│   │   ├── events/           # Event система
│   │   ├── ui/               # GUI компоненты (ClickGUI, HUD...)
│   │   ├── utils/            # Утилиты
│   │   ├── commands/         # Консольные команды
│   │   └── scripts/          # Lua скрипты
│   ├── via/                  # ViaVersion интеграция
│   └── Start.java            # Точка входа для разработки
├── libraries/                # Все зависимости (.jar)
├── lib/                      # Дополнительные библиотеки
├── assets/                   # Ресурсы Minecraft
├── out/                      # Скомпилированные артефакты
│   └── artifacts/client_jar/client.jar
├── build/                    # Папка сборки
├── scripts/                  # Lua-скрипты (test.lua)
├── laura/                    # Конфигурации Lua-модуля
├── saves/                    # Миры Minecraft
├── crash-reports/            # Отчёты о крашах
├── LauraLoader_GUI.py        # Python GUI лоадер
├── build-client-for-mojo.ps1 # PowerShell скрипт сборки
└── QWEN.md                   # Документация проекта
```

## Сборка и запуск

### Требования

- **JDK 21** (Eclipse Adoptium или аналог)
- **PowerShell** (для скрипта сборки)
- **IntelliJ IDEA** или **VS Code** с расширением Java
- **Python 3.x** (для GUI лоадера)

### Сборка для PojavLauncher/Mojo

```powershell
.\build-client-for-mojo.ps1
```

Скрипт создаёт `client.zip` в корне проекта, который содержит:
- `client.jar` — скомпилированный клиент
- `libraries/` — необходимые зависимости (без Windows natives)
- `assets/` — ресурсы Minecraft
- `LauraClient.json` — версия JSON для лаунчера

**Исключаются из сборки:**
- Windows natives (`lwjgl-*-natives-windows.jar`)
- ImGui библиотеки
- `.dll` файлы

### Запуск в IDE

**IntelliJ IDEA:**
1. Открыть проект как Java проект
2. Использовать конфигурацию `Start` (mainClass: `net.minecraft.client.main.Main`)
3. VM аргументы:
   ```
   -Djava.library.path="libraries/natives/" -XX:+UseParallelGC -XX:GCTimeRatio=4 
   -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true -Xmx6G 
   -Xms100m -Dfile.encoding=UTF-8
   ```

**VS Code:**
- Использовать конфигурацию "Expensive" из `.vscode/launch.json`
- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot`

### Запуск GUI лоадера (Python)

```cmd
# Установка зависимостей
pip install requests tqdm keyauth psutil

# Запуск
python LauraLoader_GUI.py
```

**KeyAuth конфигурация:**
- Name: `Elhan`
- Owner ID: `AtwJqdx5tK`
- Secret: `0c359ddd0637a8327a72258bb2e00863ffd190bbb8ae20b479af2b6da321c23d`

### Установка на Android (Mojo/PojavLauncher)

1. Запустить `build-client-for-mojo.ps1`
2. Распаковать `client.zip` в папку Mojo
3. Убедиться, что Mojo использует **Android natives**

## Разработка

### Модульная архитектура

```
im.laura.functions.impl/
├── combat/          # KillAura, AimAssist, AutoArmor...
├── movement/        # Fly, Speed, Jesus...
├── player/          # AutoTool, ChestStealer...
├── render/          # ESP, Tracers, HUD...
├── misc/            # AutoEZ, HitSound...
└── api/             # Базовые классы Function, Category
```

### Lua-скриптинг

Модуль `laura` предоставляет API для Lua-скриптов:

```lua
module = module.register("module testing")

function onEvent(event)
    print(event:getName())
end
```

Скрипты размещаются в `scripts/` директории.

### Конфигурация

- `laura/staffs.cfg` — список сотрудников/администраторов
- `laura/configs/` — конфигурационные файлы модуля
- `options.txt` — настройки клиента Minecraft
- `loader_config.json` — настройки Python лоадера

### Зависимости

Основные библиотеки в `libraries/`:
- `1.16.5.jar` — ядро Minecraft
- `lwjgl-*.jar` — графическая библиотека
- `ViaVersion-5.6.0.jar`, `ViaBackwards-5.6.0.jar` — поддержка других версий
- `MinecraftAuth-2.1.6.jar`, `authlib-2.1.28.jar` — аутентификация
- `gson-2.10.1.jar`, `guava-21.0.jar` — утилиты

## GUI Лоадер

**LauraLoader_GUI.py** — графический интерфейс на tkinter с:

- 🔑 Вкладкой авторизации (KeyAuth)
- 🚀 Вкладкой запуска (настройка RAM, запуск клиента)
- ⚙️ Вкладкой настроек (Java path, синхронизация времени)
- ℹ️ Вкладкой информации
- 📝 Логированием событий

## Известные проблемы

В директории `crash-reports/` содержатся отчёты о крашах (40+ файлов). Основные даты крашей:
- Январь 2024
- Февраль 2024, 2026

## Структура конфигов

| Файл | Описание |
|------|----------|
| `options.txt` | Настройки Minecraft (графика, управление, звук) |
| `optionsof.txt` | Настройки OptiFine |
| `servers.dat` | Список серверов |
| `usercache.json` | Кэш пользователей |
| `loader_config.json` | Настройки Python лоадера |

## Экспорт проекта

В корне доступны файлы экспорта QWEN Code:
- `qwen-code-export-*.md` — история экспортов контекста проекта
