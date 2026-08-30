/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.platform;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.command.ViaCommandSender;
import com.viaversion.viaversion.api.configuration.ConfigurationProvider;
import com.viaversion.viaversion.api.configuration.ViaVersionConfig;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.UnsupportedSoftware;
import com.viaversion.viaversion.api.platform.ViaPlatform;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import im.laura.Laura;
import via.ViaLoadingBase;
import via.platform.viaversion.ViaAPIWrapper;
import via.platform.viaversion.ViaConfig;
import via.util.ViaTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings({"unused", "RedundantSuppression", "ClassCanBeRecord"})
public class ViaVersionPlatformImpl implements ViaPlatform<UUID> {
    private final ViaAPI<UUID> api = new ViaAPIWrapper();
    private final Logger logger;
    private final ViaConfig config;

    public ViaVersionPlatformImpl(Logger logger) {
        this.logger = logger;
        this.config = new ViaConfig(new File(ViaLoadingBase.getInstance().getRunDirectory(), "viaversion.yml"), logger);
    }

    public static List<ProtocolVersion> createVersionList() {
        List<ProtocolVersion> versions = new ArrayList<>(ProtocolVersion.getProtocols()).stream()
            .filter(protocolVersion -> protocolVersion != null && ProtocolVersion.getProtocols().indexOf(protocolVersion) >= 7)
            .collect(Collectors.toList());
        Collections.reverse(versions);
        return versions;
    }

    public ViaCommandSender[] getOnlinePlayers() {
        return new ViaCommandSender[0];
    }

    public void sendMessage(UUID uuid, String msg) {
        if (uuid == null) {
            this.getLogger().info(msg);
        } else {
            this.getLogger().info("[" + uuid + "] " + msg);
        }
    }

    public boolean kickPlayer(UUID uuid, String s) {
        return false;
    }

    public ViaTask runAsync(Runnable runnable) {
        return new ViaTask(Via.getManager().getScheduler().execute(runnable));
    }

    public ViaTask runRepeatingAsync(Runnable runnable, long ticks) {
        return new ViaTask(Via.getManager().getScheduler().scheduleRepeating(runnable, 0L, ticks * 50L, TimeUnit.MILLISECONDS));
    }

    public ViaTask runSync(Runnable runnable) {
        return this.runAsync(runnable);
    }

    public ViaTask runSync(Runnable runnable, long ticks) {
        return new ViaTask(Via.getManager().getScheduler().schedule(runnable, ticks * 50L, TimeUnit.MILLISECONDS));
    }

    public ViaTask runRepeatingSync(Runnable runnable, long ticks) {
        return this.runRepeatingAsync(runnable, ticks);
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public void onReload() {
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public ViaVersionConfig getConf() {
        return this.config;
    }

    @SuppressWarnings("unchecked")
    public ConfigurationProvider getConfigurationProvider() {
        return (ConfigurationProvider) this.config;
    }

    @Override
    public ViaAPI<UUID> getApi() {
        return this.api;
    }

    public File getDataFolder() {
        return ViaLoadingBase.getInstance().getRunDirectory();
    }

    public String getPluginVersion() {
        return "5.6.0";
    }

    public String getPlatformName() {
        return "ViaVersion by " + Laura.CLIENT_NAME;
    }

    public String getPlatformVersion() {
        return "5.6.0";
    }

    public boolean isPluginEnabled() {
        return true;
    }

    public boolean isOldClientsAllowed() {
        return true;
    }

    public Collection<UnsupportedSoftware> getUnsupportedSoftwareClasses() {
        return ViaPlatform.super.getUnsupportedSoftwareClasses();
    }

    public boolean hasPlugin(String s) {
        return false;
    }

    public JsonObject getDump() {
        if (ViaLoadingBase.getInstance().getDumpSupplier() == null) {
            return new JsonObject();
        }
        return ViaLoadingBase.getInstance().getDumpSupplier().get();
    }

    public boolean disconnect(UserConnection connection, String message) {
        return false;
    }
}
