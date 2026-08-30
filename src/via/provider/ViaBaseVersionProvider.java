/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.provider;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import via.ViaLoadingBase;
import via.model.ComparableProtocolVersion;

public class ViaBaseVersionProvider implements VersionProvider {
    @Override
    public ProtocolVersion getClientProtocol(UserConnection connection) {
        ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        return target != null ? target : ProtocolVersion.getProtocol(754);
    }

    @Override
    public ProtocolVersion getServerProtocol(UserConnection connection) {
        ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        return target != null ? target : ProtocolVersion.getProtocol(754);
    }

    @Override
    public ProtocolVersion getClosestServerProtocol(UserConnection connection) {
        if (connection.isClientSide()) {
            ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
            return target != null ? target : ProtocolVersion.getProtocol(754);
        }
        ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        return target != null ? target : ProtocolVersion.getProtocol(754);
    }
}
