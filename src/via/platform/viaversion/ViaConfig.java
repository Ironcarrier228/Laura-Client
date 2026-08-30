/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.platform.viaversion;

import com.viaversion.viaversion.api.configuration.RateLimitConfig;
import com.viaversion.viaversion.api.minecraft.WorldIdentifiers;
import com.viaversion.viaversion.api.protocol.version.BlockedProtocolVersions;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.configuration.AbstractViaConfig;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ViaConfig extends AbstractViaConfig {
    private static final List<String> UNSUPPORTED = Arrays.asList(
        "anti-xray-patch", "bungee-ping-interval", "bungee-ping-save", "bungee-servers",
        "quick-move-action-fix", "nms-player-ticking", "velocity-ping-interval",
        "velocity-ping-save", "velocity-servers", "blockconnection-method",
        "change-1_9-hitbox", "change-1_14-hitbox", "show-shield-when-sword-in-hand",
        "left-handed-handling"
    );

    public ViaConfig(File configFile, Logger logger) {
        super(configFile, logger);
    }

    @Override
    protected void handleConfig(Map<String, Object> config) {
    }

    @Override
    public List<String> getUnsupportedOptions() {
        return UNSUPPORTED;
    }

    @Override
    public RateLimitConfig getPacketTrackerConfig() {
        return new RateLimitConfig(false, 0, "", 0, 0, 0L, "", "");
    }

    @Override
    public RateLimitConfig getPacketSizeTrackerConfig() {
        return new RateLimitConfig(false, 0, "", 0, 0, 0L, "", "");
    }

    @Override
    public BlockedProtocolVersions blockedProtocolVersions() {
        return new BlockedProtocolVersions() {
            @Override
            public boolean contains(ProtocolVersion protocolVersion) {
                return false;
            }

            @Override
            public ProtocolVersion blocksBelow() {
                return null;
            }

            @Override
            public ProtocolVersion blocksAbove() {
                return null;
            }

            @Override
            public Set<ProtocolVersion> singleBlockedVersions() {
                return Collections.emptySet();
            }
        };
    }

    @Override
    public com.viaversion.viaversion.libs.gson.JsonElement get1_17ResourcePackPrompt() {
        return null;
    }

    @Override
    public WorldIdentifiers get1_16WorldNamesMap() {
        return new WorldIdentifiers("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    }
}
