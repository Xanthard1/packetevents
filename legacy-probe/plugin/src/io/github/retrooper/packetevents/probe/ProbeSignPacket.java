package io.github.retrooper.packetevents.probe;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

public final class ProbeSignPacket extends PacketWrapper<ProbeSignPacket> {
    private static final String[] TEXT_KEYS = {"Text1", "Text2", "Text3", "Text4"};
    private Vector3i position;
    private String[] lines;

    public ProbeSignPacket(PacketSendEvent event) { super(event); }

    public ProbeSignPacket(Vector3i position, NBTCompound data) {
        super(PacketType.Play.Server.UPDATE_SIGN);
        this.position = position;
        lines = new String[4];
        for (int i = 0; i < lines.length; i++) lines[i] = data.getStringTagValueOrDefault(TEXT_KEYS[i], null);
    }

    @Override
    public void read() {
        position = legacy() ? new Vector3i(readInt(), readShort(), readInt()) : readBlockPosition();
        lines = new String[4];
        for (int i = 0; i < lines.length; i++) lines[i] = readString();
    }

    @Override
    public void write() {
        if (legacy()) {
            writeInt(position.getX());
            writeShort(position.getY());
            writeInt(position.getZ());
        } else {
            writeBlockPosition(position);
        }
        for (String line : lines) writeString(line != null ? line : legacy() ? "" : "{\"text\":\"\"}");
    }

    // 1.7 sends the sign as int, short, int and plain-text lines; 1.8 packs the position and sends JSON lines.
    private boolean legacy() {
        return serverVersion.isOlderThan(ServerVersion.V_1_8);
    }

    @Override
    public void copy(ProbeSignPacket other) {
        position = other.position;
        lines = other.lines.clone();
    }

    public Vector3i position() { return position; }

    public NBTCompound data() {
        NBTCompound data = new NBTCompound();
        for (int i = 0; i < lines.length; i++) if (lines[i] != null) data.setTag(TEXT_KEYS[i], new NBTString(lines[i]));
        return data;
    }
}
