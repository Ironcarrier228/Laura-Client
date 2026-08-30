import sys
import subprocess
import os
import time
import requests
import zipfile
import hashlib
import json
import webbrowser
import threading
from tqdm import tqdm
from keyauth import Keyauth
import psutil
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
from datetime import datetime

# ======================== НАСТРОЙКИ ========================
loader_version = "1.1.0"  # GUI версия
cheat_version = "1.5.0"
VERSION_FILE_LOADER = "loader.version"
CLIENT_DIR = r"C:\LauraClient"
CLIENT_URL = "https://www.dropbox.com/scl/fi/800hclzzujbdj6hh44nt2/client.zip?rlkey=xvfy101lmuw1oafwqks4kre3i&st=ippl2vzk&dl=1"
CONFIG_FILE = "loader_config.json"
DEFAULT_JAVA_EXE = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot\bin\java.exe"

# ======================== GITHUB ===========================
GITHUB_USER = "Ironcarrier228"
GITHUB_REPO = "Laura-Client"
GITHUB_BRANCH = "master"
GITHUB_JAR_PATH = "client.jar"
GITHUB_API_URL = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/commits?path={GITHUB_JAR_PATH}&sha={GITHUB_BRANCH}&per_page=1"
GITHUB_DOWNLOAD = f"https://raw.githubusercontent.com/{GITHUB_USER}/{GITHUB_REPO}/{GITHUB_BRANCH}/{GITHUB_JAR_PATH}"
VERSION_FILE = os.path.join(CLIENT_DIR, ".jar_version")
LOADER_VERSION_URL = f"https://raw.githubusercontent.com/{GITHUB_USER}/{GITHUB_REPO}/{GITHUB_BRANCH}/loader_version.txt"
LOADER_DOWNLOAD_URL = f"https://raw.githubusercontent.com/{GITHUB_USER}/{GITHUB_REPO}/{GITHUB_BRANCH}/LauraLoader.exe"

# ======================== CONFIG ===========================
def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except:
            pass
    return {"java_path": DEFAULT_JAVA_EXE}

def save_config(config):
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=4)

config = load_config()
JAVA_EXE = config.get("java_path", DEFAULT_JAVA_EXE)

# ======================== KEYAUTH ==========================
def getchecksum():
    md5 = hashlib.md5()
    with open(sys.argv[0], "rb") as f:
        md5.update(f.read())
    return md5.hexdigest()

# Инициализация KeyAuth
keyauthapp = Keyauth(
    name="Elhan",
    owner_id="AtwJqdx5tK",
    version="1.0",
    secret="0c359ddd0637a8327a72258bb2e00863ffd190bbb8ae20b479af2b6da321c23d",
    file_hash=""
)

# ======================== GUI CLASS ========================
class LauraLoaderGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Laura Client Loader")
        self.root.geometry("700x550")
        self.root.resizable(False, False)
        
        # Цвета
        self.bg_color = "#1a1a2e"
        self.fg_color = "#ffffff"
        self.accent_color = "#e94560"
        self.secondary_color = "#16213e"
        self.success_color = "#0f3460"
        
        self.root.configure(bg=self.bg_color)
        
        # Стиль
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("TFrame", background=self.bg_color)
        style.configure("TLabel", background=self.bg_color, foreground=self.fg_color, font=("Segoe UI", 10))
        style.configure("Header.TLabel", font=("Segoe UI", 16, "bold"), foreground=self.accent_color)
        style.configure("TButton", background=self.accent_color, foreground="white", font=("Segoe UI", 10, "bold"), borderwidth=0, padding=10)
        style.map("TButton", background=[("active", "#ff6b6b")])
        style.configure("TEntry", fieldbackground=self.secondary_color, foreground=self.fg_color, font=("Segoe UI", 10), padding=5)
        style.configure("TProgressbar", background=self.accent_color, troughcolor=self.secondary_color)
        
        self.create_widgets()
        self.check_loader_update()
        
    def create_widgets(self):
        # Заголовок
        header_frame = ttk.Frame(self.root, padding="20")
        header_frame.pack(fill=tk.X)
        
        title_label = ttk.Label(header_frame, text="LAURA CLIENT", style="Header.TLabel")
        title_label.pack()
        
        version_label = ttk.Label(header_frame, text=f"Loader v{loader_version} | Cheat v{cheat_version}", font=("Segoe UI", 9))
        version_label.pack()
        
        # Статус бар
        self.status_var = tk.StringVar(value="Готов к работе")
        status_bar = ttk.Label(self.root, textvariable=self.status_var, relief=tk.SUNKEN, anchor=tk.W, padding=5)
        status_bar.pack(fill=tk.X, side=tk.BOTTOM)
        
        # Основной контент
        main_frame = ttk.Frame(self.root, padding="20")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Вкладки
        notebook = ttk.Notebook(main_frame)
        notebook.pack(fill=tk.BOTH, expand=True, pady=10)
        
        # Вкладка авторизации
        auth_frame = ttk.Frame(notebook, padding="20")
        notebook.add(auth_frame, text="🔑 Авторизация")
        self.create_auth_tab(auth_frame)
        
        # Вкладка запуска
        launch_frame = ttk.Frame(notebook, padding="20")
        notebook.add(launch_frame, text="🚀 Запуск")
        self.create_launch_tab(launch_frame)
        
        # Вкладка настроек
        settings_frame = ttk.Frame(notebook, padding="20")
        notebook.add(settings_frame, text="⚙️ Настройки")
        self.create_settings_tab(settings_frame)
        
        # Вкладка информации
        info_frame = ttk.Frame(notebook, padding="20")
        notebook.add(info_frame, text="ℹ️ Информация")
        self.create_info_tab(info_frame)
        
        # Логирование
        log_frame = ttk.LabelFrame(main_frame, text="Логи", padding="10")
        log_frame.pack(fill=tk.BOTH, expand=True, pady=10)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=8, bg=self.secondary_color, fg=self.fg_color, font=("Consolas", 9))
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
    def create_auth_tab(self, parent):
        # Ключ
        key_frame = ttk.LabelFrame(parent, text="Ключ активации", padding="15")
        key_frame.pack(fill=tk.X, pady=10)
        
        ttk.Label(key_frame, text="Введите ваш ключ:").pack(anchor=tk.W)
        
        self.key_entry = ttk.Entry(key_frame, font=("Segoe UI", 11))
        self.key_entry.pack(fill=tk.X, pady=5)
        self.key_entry.bind("<Return>", lambda e: self.auth())
        
        # Кнопка авторизации
        auth_btn = ttk.Button(key_frame, text="ВОЙТИ", command=self.auth)
        auth_btn.pack(fill=tk.X, pady=10)
        
        # Информация о пользователе
        self.user_info_frame = ttk.LabelFrame(parent, text="Информация", padding="15")
        self.user_info_frame.pack(fill=tk.X, pady=10)
        
        self.user_label = ttk.Label(self.user_info_frame, text="Не авторизован", font=("Segoe UI", 10))
        self.user_label.pack(anchor=tk.W)
        
        self.expiry_label = ttk.Label(self.user_info_frame, text="Срок: -", font=("Segoe UI", 10))
        self.expiry_label.pack(anchor=tk.W)
        
    def create_launch_tab(self, parent):
        # Статус клиента
        status_frame = ttk.LabelFrame(parent, text="Статус клиента", padding="15")
        status_frame.pack(fill=tk.X, pady=10)
        
        self.client_status_var = tk.StringVar(value="Проверка...")
        status_label = ttk.Label(status_frame, textvariable=self.client_status_var)
        status_label.pack(anchor=tk.W)
        
        # RAM
        ram_frame = ttk.LabelFrame(parent, text="Выделение памяти", padding="15")
        ram_frame.pack(fill=tk.X, pady=10)
        
        total_ram = int(psutil.virtual_memory().total / (1024 ** 3))
        self.ram_var = tk.IntVar(value=4)
        ram_scale = ttk.Scale(ram_frame, from_=1, to=total_ram-1, variable=self.ram_var, orient=tk.HORIZONTAL)
        ram_scale.pack(fill=tk.X)
        
        self.ram_label = ttk.Label(ram_frame, text=f"Выделено: {self.ram_var.get()} ГБ")
        self.ram_label.pack(anchor=tk.W)
        self.ram_var.trace('w', lambda *args: self.ram_label.config(text=f"Выделено: {self.ram_var.get()} ГБ"))
        
        # Кнопка запуска
        launch_btn = ttk.Button(parent, text="ЗАПУСТИТЬ ИГРУ", command=self.launch_game)
        launch_btn.pack(fill=tk.X, pady=20)
        
        # Прогресс
        self.progress = ttk.Progressbar(parent, mode='indeterminate')
        self.progress.pack(fill=tk.X, pady=10)
        
    def create_settings_tab(self, parent):
        # Java
        java_frame = ttk.LabelFrame(parent, text="Настройки Java", padding="15")
        java_frame.pack(fill=tk.X, pady=10)
        
        ttk.Label(java_frame, text="Путь к Java:").pack(anchor=tk.W)
        
        self.java_entry = ttk.Entry(java_frame, font=("Segoe UI", 9))
        self.java_entry.insert(0, JAVA_EXE)
        self.java_entry.pack(fill=tk.X, pady=5)
        
        btn_frame = ttk.Frame(java_frame)
        btn_frame.pack(fill=tk.X)
        
        ttk.Button(btn_frame, text="Обзор", command=self.browse_java).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="Найти автоматически", command=self.auto_find_java).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="Сохранить", command=self.save_java_path).pack(side=tk.LEFT, padx=2)
        
        # Синхронизация времени
        time_frame = ttk.LabelFrame(parent, text="Время", padding="15")
        time_frame.pack(fill=tk.X, pady=10)
        
        ttk.Button(time_frame, text="Синхронизировать время", command=self.sync_time).pack(fill=tk.X)
        
    def create_info_tab(self, parent):
        info_text = scrolledtext.ScrolledText(parent, bg=self.secondary_color, fg=self.fg_color, font=("Segoe UI", 10))
        info_text.pack(fill=tk.BOTH, expand=True)
        
        info = f"""
LAURA CLIENT LOADER
Версия: {loader_version}
Версия чита: {cheat_version}

GitHub: https://github.com/Ironcarrier228/Laura-Client

ИНСТРУКЦИЯ:
1. Введите ключ в разделе "Авторизация"
2. Нажмите "Войти"
3. Перейдите в раздел "Запуск"
4. Настройте память (рекомендуется 4-8 ГБ)
5. Нажмите "ЗАПУСТИТЬ ИГРУ"

НАСТРОЙКИ:
- Путь к Java можно изменить в разделе "Настройки"
- Рекомендуется Java 17 или 21
- Синхронизируйте время для корректной работы

ПОДДЕРЖКА:
- Discord: (укажите ваш Discord)
- Telegram: (укажите ваш Telegram)
        """
        info_text.insert("1.0", info)
        info_text.config(state=tk.DISABLED)
        
    def log(self, message):
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.root.update_idletasks()
        
    def set_status(self, message):
        self.status_var.set(message)
        self.root.update_idletasks()
        
    # ======================== AUTH =========================
    def auth(self):
        key = self.key_entry.get().strip()
        if not key:
            messagebox.showerror("Ошибка", "Введите ключ!")
            return
            
        self.set_status("Авторизация...")
        self.log(f"Попытка авторизации с ключом: {key[:10]}...")
        
        def auth_thread():
            try:
                keyauthapp.license(key)
                if keyauthapp.sessionid:
                    self.root.after(0, self.auth_success)
                else:
                    self.root.after(0, lambda: self.auth_failed("Неверный ключ"))
            except Exception as e:
                self.root.after(0, lambda: self.auth_failed(str(e)))
                
        threading.Thread(target=auth_thread, daemon=True).start()
        
    def auth_success(self):
        try:
            username = keyauthapp.user_data.username
            expiry = keyauthapp.user_data.expiry
            self.user_label.config(text=f"Пользователь: {username}")
            self.expiry_label.config(text=f"Срок: {expiry}")
            self.set_status("Авторизация успешна!")
            self.log("Авторизация успешна!")
            messagebox.showinfo("Успех", f"Добро пожаловать, {username}!")
        except:
            self.user_label.config(text="Пользователь: Unknown")
            self.expiry_label.config(text="Срок: Unknown")
            self.set_status("Авторизация успешна!")
            self.log("Авторизация успешна!")
            messagebox.showinfo("Успех", "Авторизация прошла успешно!")
            
    def auth_failed(self, error):
        self.set_status("Ошибка авторизации")
        self.log(f"Ошибка: {error}")
        messagebox.showerror("Ошибка", f"Не удалось авторизоваться:\n{error}")
        
    # ======================== LAUNCH =======================
    def launch_game(self):
        if not hasattr(keyauthapp, 'sessionid') or not keyauthapp.sessionid:
            messagebox.showerror("Ошибка", "Сначала авторизуйтесь!")
            return
            
        self.set_status("Подготовка...")
        self.progress.start()
        self.log("Запуск клиента...")
        
        def launch_thread():
            try:
                # Проверка клиента
                self.root.after(0, lambda: self.set_status("Проверка клиента..."))
                self.download_and_extract()
                
                # Поиск jar
                client_jar = self.find_client_jar()
                if not client_jar:
                    self.root.after(0, lambda: messagebox.showerror("Ошибка", "client.jar не найден!"))
                    self.root.after(0, lambda: self.set_status("Ошибка"))
                    self.root.after(0, self.progress.stop)
                    return
                
                # Запуск
                ram = self.ram_var.get()
                java_path = self.java_entry.get()
                
                self.root.after(0, lambda: self.set_status(f"Запуск с {ram} ГБ RAM..."))
                self.log(f"Java: {java_path}")
                self.log(f"RAM: {ram} ГБ")
                
                libs_dir = os.path.join(CLIENT_DIR, "libraries")
                libs = []
                if os.path.exists(libs_dir):
                    for root, _, files in os.walk(libs_dir):
                        for f in files:
                            if f.endswith(".jar"):
                                libs.append(os.path.join(root, f))
                
                separator = ";" if sys.platform == "win32" else ":"
                classpath = separator.join([client_jar] + libs)
                
                jvm_args = [f"-Xmx{ram}G"]
                jvm_args.extend(["-cp", classpath, "Start"])
                
                subprocess.Popen([java_path] + jvm_args, cwd=os.path.dirname(client_jar))
                
                self.root.after(0, lambda: self.set_status("Клиент запущен!"))
                self.root.after(0, lambda: messagebox.showinfo("Успех", "Клиент запущен!"))
                self.root.after(0, self.progress.stop)
                
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("Ошибка", f"Ошибка запуска: {e}"))
                self.root.after(0, lambda: self.set_status("Ошибка"))
                self.root.after(0, self.progress.stop)
                
        threading.Thread(target=launch_thread, daemon=True).start()
        
    def download_and_extract(self):
        if not os.path.exists(CLIENT_DIR):
            os.makedirs(CLIENT_DIR)
            
        for root, _, files in os.walk(CLIENT_DIR):
            for f in files:
                if f.lower() == "client.jar":
                    self.log("Клиент уже загружен")
                    return
                    
        zip_path = os.path.join(CLIENT_DIR, "client.zip")
        self.log("Скачивание клиента...")
        
        r = requests.get(CLIENT_URL, stream=True)
        total = int(r.headers.get("content-length", 0))
        
        with open(zip_path, "wb") as f:
            for chunk in r.iter_content(1024):
                f.write(chunk)
                
        self.log("Распаковка...")
        with zipfile.ZipFile(zip_path, "r") as z:
            z.extractall(CLIENT_DIR)
            
        os.remove(zip_path)
        self.log("Клиент загружен")
        
    def find_client_jar(self):
        for root, _, files in os.walk(CLIENT_DIR):
            for f in files:
                if f.lower() == "client.jar":
                    return os.path.join(root, f)
        return None
        
    # ======================== SETTINGS =====================
    def browse_java(self):
        from tkinter import filedialog
        path = filedialog.askopenfilename(
            title="Выберите java.exe",
            filetypes=[("Executable", "*.exe"), ("All files", "*.*")]
        )
        if path:
            self.java_entry.delete(0, tk.END)
            self.java_entry.insert(0, path)
            
    def auto_find_java(self):
        self.log("Поиск Java...")
        found = self.find_java_installations()
        if found:
            self.java_entry.delete(0, tk.END)
            self.java_entry.insert(0, found[0])
            self.log(f"Найдено: {found[0]}")
            messagebox.showinfo("Найдено", f"Java найдена:\n{found[0]}")
        else:
            self.log("Java не найдена")
            messagebox.showwarning("Не найдено", "Java не найдена на компьютере")
            
    def save_java_path(self):
        global JAVA_EXE, config
        JAVA_EXE = self.java_entry.get()
        config["java_path"] = JAVA_EXE
        save_config(config)
        self.log(f"Java сохранена: {JAVA_EXE}")
        messagebox.showinfo("Успех", "Путь к Java сохранён!")
        
    def sync_time(self):
        self.log("Синхронизация времени...")
        try:
            result = subprocess.run(["w32tm", "/resync", "/force"], capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                self.log("Время синхронизировано ✓")
                messagebox.showinfo("Успех", "Время синхронизировано!")
            else:
                self.log(f"Ошибка: {result.stderr}")
                messagebox.showerror("Ошибка", f"Не удалось синхронизировать:\n{result.stderr}")
        except Exception as e:
            self.log(f"Ошибка: {e}")
            messagebox.showerror("Ошибка", str(e))
            
    def find_java_installations(self):
        java_paths = []
        search_dirs = [
            r"C:\Program Files\Java",
            r"C:\Program Files\Eclipse Adoptium",
            r"C:\Program Files\Amazon Corretto",
            r"C:\Program Files\Microsoft"
        ]
        for base_dir in search_dirs:
            if os.path.exists(base_dir):
                for root, dirs, files in os.walk(base_dir):
                    if "java.exe" in files:
                        java_paths.append(os.path.join(root, "java.exe"))
        return java_paths
        
    # ======================== UPDATE =======================
    def check_loader_update(self):
        try:
            r = requests.get(LOADER_VERSION_URL, timeout=5)
            if r.status_code == 200:
                github_version = r.text.strip()
                if github_version != loader_version:
                    if messagebox.askyesno("Обновление", f"Найдено обновление {github_version}!\nОбновить?"):
                        self.update_loader(github_version)
        except:
            pass
            
    def update_loader(self, version):
        self.log("Обновление лоадера...")
        try:
            r = requests.get(LOADER_DOWNLOAD_URL, stream=True, timeout=30)
            with open("LauraLoader_new.exe", "wb") as f:
                for chunk in r.iter_content(8192):
                    f.write(chunk)
                    
            updater_script = f"""@echo off
timeout /t 2 /nobreak >nul
taskkill /F /IM "LauraLoader.exe" >nul 2>&1
timeout /t 1 /nobreak >nul
del "LauraLoader.exe" >nul 2>&1
ren "LauraLoader_new.exe" "LauraLoader.exe"
echo {version} > loader.version
cd /d "%~dp0"
start "" "LauraLoader.exe"
timeout /t 2 /nobreak >nul
del "%~f0"
"""
            with open("updater.bat", "w") as f:
                f.write(updater_script)
                
            subprocess.Popen(["updater.bat"], shell=True)
            self.root.quit()
        except Exception as e:
            self.log(f"Ошибка обновления: {e}")

# ======================== START ===========================
if __name__ == "__main__":
    root = tk.Tk()
    app = LauraLoaderGUI(root)
    root.mainloop()
