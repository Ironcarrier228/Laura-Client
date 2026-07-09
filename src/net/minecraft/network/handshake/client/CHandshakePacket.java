package net.minecraft.network.handshake.client;

import java.io.IOException;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.handshake.IHandshakeNetHandler;
import net.minecraft.util.SharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import via.ViaLoadingBase;
import via.model.ComparableProtocolVersion;

public class CHandshakePacket implements IPacket<IHandshakeNetHandler>
{
    private static final Logger LOGGER = LogManager.getLogger();
    private int protocolVersion;
    private String ip;
    private int port;
    private ProtocolType requestedState;

    public CHandshakePacket()
    {
    }

    public CHandshakePacket(String p_i47613_1_, int p_i47613_2_, ProtocolType p_i47613_3_)
    {
        ComparableProtocolVersion viaVersion = ViaLoadingBase.getInstance() != null ? ViaLoadingBase.getInstance().getTargetVersion() : null;
        this.protocolVersion = viaVersion != null ? viaVersion.getVersion() : SharedConstants.getVersion().getProtocolVersion();
        this.ip = p_i47613_1_;
        this.port = p_i47613_2_;
        this.requestedState = p_i47613_3_;
        
        LOGGER.info("[ViaVersion] Handshake protocol version: {} (target: {})", this.protocolVersion, viaVersion);
    }

    /**
     * Reads the raw packet data from the data stream.
     */
    public void readPacketData(PacketBuffer buf) throws IOException
    {
        this.protocolVersion = buf.readVarInt();
        this.ip = buf.readString(255);
        this.port = buf.readUnsignedShort();
        this.requestedState = ProtocolType.getById(buf.readVarInt());
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    public void writePacketData(PacketBuffer buf) throws IOException
    {
        buf.writeVarInt(this.protocolVersion);
        buf.writeString(this.ip);
        buf.writeShort(this.port);
        buf.writeVarInt(this.requestedState.getId());
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void processPacket(IHandshakeNetHandler handler)
    {
        handler.processHandshake(this);
    }

    public ProtocolType getRequestedState()
    {
        return this.requestedState;
    }

    public int getProtocolVersion()
    {
        return this.protocolVersion;
    }
}
