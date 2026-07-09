/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.platform.viaversion;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import via.ViaLoadingBase;
import via.model.ComparableProtocolVersion;
import java.util.SortedSet;
import java.util.TreeSet;

public class ViaInjector implements com.viaversion.viaversion.api.platform.ViaInjector {
    @Override
    public void inject() {
    }

    @Override
    public void uninject() {
    }

    @Override
    public String getDecoderName() {
        return "via-decoder";
    }

    @Override
    public String getEncoderName() {
        return "via-encoder";
    }

    @Override
    public SortedSet<ProtocolVersion> getServerProtocolVersions() {
        SortedSet<ProtocolVersion> versions = new TreeSet<>();
        for (ProtocolVersion value : ProtocolVersion.getProtocols()) {
            if (value.getVersion() < 74) continue;
            versions.add(value);
        }
        return versions;
    }

    @Override
    public ProtocolVersion getServerProtocolVersion() {
        ComparableProtocolVersion target = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        return target != null ? target : ProtocolVersion.getProtocol(754);
    }

    @Override
    public JsonObject getDump() {
        return new JsonObject();
    }
}
