/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.model;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionType;
import com.viaversion.viaversion.api.protocol.version.SubVersionRange;
import via.ViaLoadingBase;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class ComparableProtocolVersion extends ProtocolVersion {
    private final int index;

    public ComparableProtocolVersion(int version, String name, int index) {
        super(VersionType.RELEASE, version, -1, name, new SubVersionRange(getMajorMinorVersion(name), 0, 1));
        this.index = index;
    }

    private static String getMajorMinorVersion(String name) {
        String[] parts = name.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        } else if (parts.length == 1) {
            return parts[0] + ".0";
        } else {
            return "1.0";
        }
    }

    public boolean isOlderThan(ProtocolVersion other) {
        return this.getIndex() > ViaLoadingBase.fromProtocolVersion(other).getIndex();
    }

    public boolean isOlderThanOrEqualTo(ProtocolVersion other) {
        return this.getIndex() >= ViaLoadingBase.fromProtocolVersion(other).getIndex();
    }

    public boolean isNewerThan(ProtocolVersion other) {
        return this.getIndex() < ViaLoadingBase.fromProtocolVersion(other).getIndex();
    }

    public boolean isNewerThanOrEqualTo(ProtocolVersion other) {
        return this.getIndex() <= ViaLoadingBase.fromProtocolVersion(other).getIndex();
    }

    public boolean isEqualTo(ProtocolVersion other) {
        return this.getIndex() == ViaLoadingBase.fromProtocolVersion(other).getIndex();
    }

    public int getIndex() {
        return this.index;
    }
}
