import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// A minimal offline-mode 1.7 client: logs in, answers keep-alives, accepts the spawn teleport,
// then walks in a square, jumps and swings so the server sees every movement packet shape.
public final class LegacyBot {
    private final DataInputStream in;
    private final DataOutputStream out;
    private final int protocol;
    private double x, y, z;
    private boolean spawned;
    private int entityId;
    private int otherPlayer = -1;

    private LegacyBot(Socket socket, int protocol) throws IOException {
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.protocol = protocol;
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25570;
        int protocol = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int seconds = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        String name = args.length > 4 ? args[4] : "LegacyBot";
        try (Socket socket = new Socket(host, port)) {
            socket.setTcpNoDelay(true);
            LegacyBot bot = new LegacyBot(socket, protocol);
            bot.login(host, port, name);
            bot.play(seconds * 1000L);
        }
    }

    private void login(String host, int port, String name) throws IOException {
        send(0x00, b -> {
            varInt(b, protocol);
            string(b, host);
            b.writeShort(port);
            varInt(b, 2);
        });
        send(0x00, b -> string(b, name));
        while (true) {
            Packet packet = read();
            if (packet.id == 0x02) {
                System.out.println("logged in as " + string(packet.data) + " " + string(packet.data));
                return;
            }
            if (packet.id == 0x00) throw new IOException("disconnected during login: " + string(packet.data));
        }
    }

    private void play(long millis) throws IOException {
        long end = System.currentTimeMillis() + millis;
        long nextTick = 0;
        int tick = 0;
        while (System.currentTimeMillis() < end) {
            while (in.available() > 0) handle(read());
            long now = System.currentTimeMillis();
            if (spawned && now >= nextTick) {
                nextTick = now + 50;
                tick++;
                double step = 0.2;
                int leg = (tick / 20) % 4;
                if (leg == 0) x += step; else if (leg == 1) z += step; else if (leg == 2) x -= step; else z -= step;
                boolean jumping = tick % 40 < 6 && tick % 40 > 0;
                double feet = y + (jumping ? 0.42 * Math.min(tick % 40, 3) / 3 : 0);
                float yaw = (tick * 9) % 360;
                double fx = x, fz = z;
                send(0x06, b -> {
                    b.writeDouble(fx);
                    b.writeDouble(feet);
                    b.writeDouble(feet + 1.62);
                    b.writeDouble(fz);
                    b.writeFloat(yaw);
                    b.writeFloat(10f);
                    b.writeBoolean(!jumping);
                });
                if (tick % 10 == 0) send(0x0A, b -> {
                    b.writeInt(entityId);
                    b.writeByte(1);
                });
                if (tick % 7 == 0) send(0x03, b -> b.writeBoolean(true));
                actions(tick, fx, feet, fz);
            } else {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
        System.out.println("finished after " + tick + " ticks at " + x + "," + y + "," + z);
    }

    // Every other serverbound shape Fortress decodes, in the 1.7 layouts.
    private void actions(int tick, double x, double y, double z) throws IOException {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y) - 1, bz = (int) Math.floor(z);
        if (tick == 2) {
            send(0x15, b -> {
                string(b, "en_US");
                b.writeByte(4);
                b.writeByte(0);
                b.writeBoolean(true);
                b.writeByte(2);
                b.writeBoolean(true);
            });
            byte[] brand = "vanilla".getBytes(StandardCharsets.UTF_8);
            send(0x17, b -> {
                string(b, "MC|Brand");
                b.writeShort(brand.length);
                b.write(brand);
            });
        }
        if (tick % 13 == 0) {
            int status = (tick / 13) % 2 == 0 ? 0 : 1;
            send(0x07, b -> {
                b.writeByte(status);
                b.writeInt(bx);
                b.writeByte(by);
                b.writeInt(bz);
                b.writeByte(1);
            });
        }
        if (tick % 17 == 0) send(0x08, b -> {
            b.writeInt(bx);
            b.writeByte(by);
            b.writeInt(bz);
            b.writeByte(1);
            b.writeShort(-1);
            b.writeByte(8);
            b.writeByte(16);
            b.writeByte(8);
        });
        if (tick % 19 == 0) send(0x09, b -> b.writeShort((tick / 19) % 9));
        if (tick % 23 == 0) {
            int action = (tick / 23) % 2 == 0 ? 1 : 2;
            send(0x0B, b -> {
                b.writeInt(entityId);
                b.writeByte(action);
                b.writeInt(0);
            });
        }
        if (tick % 29 == 0) send(0x0E, b -> {
            b.writeByte(0);
            b.writeShort(36);
            b.writeByte(0);
            b.writeShort(tick);
            b.writeByte(0);
            b.writeShort(-1);
        });
        if (tick % 31 == 0 && otherPlayer >= 0) send(0x02, b -> {
            b.writeInt(otherPlayer);
            b.writeByte(1);
        });
    }

    private void handle(Packet packet) throws IOException {
        DataInputStream data = packet.data;
        switch (packet.id) {
            case 0x00: {
                int id = data.readInt();
                send(0x00, b -> b.writeInt(id));
                break;
            }
            case 0x01:
                entityId = data.readInt();
                break;
            case 0x08: {
                x = data.readDouble();
                double eye = data.readDouble();
                z = data.readDouble();
                float yaw = data.readFloat();
                float pitch = data.readFloat();
                boolean ground = data.readBoolean();
                y = eye - 1.62;
                send(0x06, b -> {
                    b.writeDouble(x);
                    b.writeDouble(y);
                    b.writeDouble(eye);
                    b.writeDouble(z);
                    b.writeFloat(yaw);
                    b.writeFloat(pitch);
                    b.writeBoolean(ground);
                });
                if (!spawned) System.out.println("spawned at " + x + "," + y + "," + z);
                spawned = true;
                break;
            }
            case 0x0C:
                otherPlayer = readVarInt(data);
                break;
            case 0x40:
                throw new IOException("kicked: " + string(data));
            default:
                break;
        }
    }

    private interface Body {
        void write(DataOutputStream body) throws IOException;
    }

    private void send(int id, Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(bytes);
        varInt(payload, id);
        body.write(payload);
        byte[] raw = bytes.toByteArray();
        varInt(out, raw.length);
        out.write(raw);
        out.flush();
    }

    private static final class Packet {
        final int id;
        final DataInputStream data;

        Packet(int id, DataInputStream data) {
            this.id = id;
            this.data = data;
        }
    }

    private Packet read() throws IOException {
        int length = readVarInt(in);
        byte[] raw = new byte[length];
        in.readFully(raw);
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(raw));
        return new Packet(readVarInt(data), data);
    }

    private static void varInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        varInt(out, raw.length);
        out.write(raw);
    }

    private static String string(DataInputStream in) throws IOException {
        byte[] raw = new byte[readVarInt(in)];
        in.readFully(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }
}
