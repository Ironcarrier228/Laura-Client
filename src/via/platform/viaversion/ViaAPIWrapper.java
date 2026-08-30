/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.platform.viaversion;

import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.ServerProtocolVersion;
import com.viaversion.viaversion.api.legacy.LegacyViaAPI;
import io.netty.buffer.ByteBuf;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import via.ViaLoadingBase;
import via.model.ComparableProtocolVersion;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class ViaAPIWrapper implements ViaAPI<UUID> {

    private ProtocolVersion getClientVersion() {
        ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        return target != null ? target : ProtocolVersion.getProtocol(754);
    }

    /**
     * Явное переопределение для разрешения конфликта default-методов для
     * getPlayerVersion
     */
    @Override
    public int getPlayerVersion(UUID player) {
        return getClientVersion().getVersion();
    }

    @Override
    public UserConnection getConnection(UUID uuid) {
        return null;
    }

    @Override
    public SortedSet<ProtocolVersion> getFullSupportedProtocolVersions() {
        return new TreeSet<>(ProtocolVersion.getProtocols());
    }

    @Override
    public ProtocolVersion getPlayerProtocolVersion(UUID player) {
        return getClientVersion();
    }

    @Override
    public ServerProtocolVersion getServerVersion() {
        ProtocolVersion clientVer = getClientVersion();
        return new ServerProtocolVersion() {
            public ProtocolVersion protocolVersion() {
                return clientVer;
            }

            public String versionString() {
                return clientVer.getName();
            }

            @Override
            public ProtocolVersion lowestSupportedProtocolVersion() {
                return ProtocolVersion.getProtocol(47);
            }

            @Override
            public ProtocolVersion highestSupportedProtocolVersion() {
                return ProtocolVersion.getProtocol(774);
            }

            @Override
            public SortedSet<ProtocolVersion> supportedProtocolVersions() {
                return new TreeSet<>(ProtocolVersion.getProtocols());
            }
        };
    }

    @Override
    public SortedSet<ProtocolVersion> getSupportedProtocolVersions() {
        return new TreeSet<>(ProtocolVersion.getProtocols());
    }

    @Override
    public String getVersion() {
        return "5.6.0";
    }

    @Override
    public boolean isInjected(UUID uuid) {
        return true;
    }

    @Override
    public void sendRawPacket(UUID uuid, ByteBuf packet) {
    }

    @Override
    public LegacyViaAPI<UUID> legacyAPI() {
        return null;
    }
}
