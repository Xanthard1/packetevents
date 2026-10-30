/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2026 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.test;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.test.base.BaseDummyAPITest;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerLoginSuccess;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMovement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange.EncodedBlock;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("1.7 server packet layouts")
public class LegacyServerWrapperTest extends BaseDummyAPITest {
    private static final ServerVersion[] LEGACY_SERVERS = {ServerVersion.V_1_7_2, ServerVersion.V_1_7_10};
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    public void loginSuccessUsesDashlessUuidOnProtocolFour() {
        byte[] fixture = bytes(out -> {
            string(out, "069a79f444e94726a5befca90e38aaf5");
            string(out, "Notch");
        });
        WrapperLoginServerLoginSuccess read = decode(new WrapperLoginServerLoginSuccess(PLAYER, "x"), ServerVersion.V_1_7_2, fixture);
        assertEquals(PLAYER, read.getUserProfile().getUUID());
        assertEquals("Notch", read.getUserProfile().getName());
        assertArrayEquals(fixture, encode(new WrapperLoginServerLoginSuccess(new UserProfile(PLAYER, "Notch")), ServerVersion.V_1_7_2));

        byte[] dashed = bytes(out -> {
            string(out, PLAYER.toString());
            string(out, "Notch");
        });
        assertEquals(PLAYER, decode(new WrapperLoginServerLoginSuccess(PLAYER, "x"), ServerVersion.V_1_7_10, dashed).getUserProfile().getUUID());
        assertArrayEquals(dashed, encode(new WrapperLoginServerLoginSuccess(new UserProfile(PLAYER, "Notch")), ServerVersion.V_1_7_10));
    }

    @Test
    public void entityMovementUsesIntIdsAndNoOnGround() {
        for (ServerVersion version : LEGACY_SERVERS) {
            byte[] relativeMove = bytes(out -> {
                out.writeInt(300);
                out.writeByte(32);
                out.writeByte(-16);
                out.writeByte(8);
            });
            WrapperPlayServerEntityRelativeMove move = decode(new WrapperPlayServerEntityRelativeMove(0, 0, 0, 0, false), version, relativeMove);
            assertEquals(300, move.getEntityId());
            assertEquals(1.0, move.getDeltaX(), 1e-9);
            assertEquals(-0.5, move.getDeltaY(), 1e-9);
            assertEquals(0.25, move.getDeltaZ(), 1e-9);
            assertFalse(move.isOnGround());
            assertArrayEquals(relativeMove, encode(new WrapperPlayServerEntityRelativeMove(300, 1.0, -0.5, 0.25, true), version));

            byte[] moveAndLook = bytes(out -> {
                out.writeInt(300);
                out.writeByte(32);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(64);
                out.writeByte(0);
            });
            WrapperPlayServerEntityRelativeMoveAndRotation both = decode(
                    new WrapperPlayServerEntityRelativeMoveAndRotation(0, 0, 0, 0, 0, 0, false), version, moveAndLook);
            assertEquals(300, both.getEntityId());
            assertEquals(90.0f, both.getYaw(), 1e-4);
            assertArrayEquals(moveAndLook, encode(new WrapperPlayServerEntityRelativeMoveAndRotation(300, 1.0, 0, 0, 90.0f, 0, true), version));

            byte[] look = bytes(out -> {
                out.writeInt(300);
                out.writeByte(64);
                out.writeByte(0);
            });
            assertEquals(300, decode(new WrapperPlayServerEntityRotation(0, 0, 0, false), version, look).getEntityId());
            assertArrayEquals(look, encode(new WrapperPlayServerEntityRotation(300, 90.0f, 0, true), version));

            byte[] headLook = bytes(out -> {
                out.writeInt(300);
                out.writeByte(64);
            });
            assertEquals(90.0f, decode(new WrapperPlayServerEntityHeadLook(0, 0), version, headLook).getHeadYaw(), 1e-4);
            assertArrayEquals(headLook, encode(new WrapperPlayServerEntityHeadLook(300, 90.0f), version));

            byte[] idle = bytes(out -> out.writeInt(300));
            assertEquals(300, decode(new WrapperPlayServerEntityMovement(0), version, idle).getEntityId());
            assertArrayEquals(idle, encode(new WrapperPlayServerEntityMovement(300), version));
        }
    }

    @Test
    public void entityEffectsUseIntIdsAndShortDuration() {
        for (ServerVersion version : LEGACY_SERVERS) {
            byte[] effect = bytes(out -> {
                out.writeInt(300);
                out.writeByte(1);
                out.writeByte(2);
                out.writeShort(600);
            });
            WrapperPlayServerEntityEffect read = decode(new WrapperPlayServerEntityEffect(0, PotionTypes.SPEED, 0, 0, (byte) 0), version, effect);
            assertEquals(300, read.getEntityId());
            assertEquals(PotionTypes.SPEED, read.getPotionType());
            assertEquals(2, read.getEffectAmplifier());
            assertEquals(600, read.getEffectDurationTicks());
            assertArrayEquals(effect, encode(new WrapperPlayServerEntityEffect(300, PotionTypes.SPEED, 2, 600, (byte) 0), version));

            byte[] removal = bytes(out -> {
                out.writeInt(300);
                out.writeByte(1);
            });
            assertEquals(300, decode(new WrapperPlayServerRemoveEntityEffect(0, PotionTypes.SPEED), version, removal).getEntityId());
            assertArrayEquals(removal, encode(new WrapperPlayServerRemoveEntityEffect(300, PotionTypes.SPEED), version));
        }
    }

    @Test
    public void spawnPlayerCarriesNameAndProperties() {
        byte[] protocolFive = bytes(out -> {
            varInt(out, 300);
            string(out, PLAYER.toString());
            string(out, "Notch");
            varInt(out, 1);
            string(out, "textures");
            string(out, "value");
            string(out, "signature");
            out.writeInt(32);
            out.writeInt(64);
            out.writeInt(-32);
            out.writeByte(0);
            out.writeByte(0);
            out.writeShort(0);
            out.writeByte(127);
        });
        WrapperPlayServerSpawnPlayer read = decode(
                new WrapperPlayServerSpawnPlayer(0, PLAYER, new Vector3d(0, 0, 0), 0, 0, new ArrayList<>()),
                ServerVersion.V_1_7_10, protocolFive);
        assertEquals(300, read.getEntityId());
        assertEquals(PLAYER, read.getUUID());
        assertEquals("Notch", read.getLegacyName());
        assertEquals(1, read.getLegacyProperties().size());
        assertEquals("signature", read.getLegacyProperties().get(0).getSignature());
        assertEquals(2.0, read.getPosition().getY(), 1e-9);

        byte[] protocolFour = bytes(out -> {
            varInt(out, 300);
            string(out, "069a79f444e94726a5befca90e38aaf5");
            string(out, "Notch");
            out.writeInt(32);
            out.writeInt(64);
            out.writeInt(-32);
            out.writeByte(0);
            out.writeByte(0);
            out.writeShort(0);
            out.writeByte(127);
        });
        WrapperPlayServerSpawnPlayer old = decode(
                new WrapperPlayServerSpawnPlayer(0, PLAYER, new Vector3d(0, 0, 0), 0, 0, new ArrayList<>()),
                ServerVersion.V_1_7_2, protocolFour);
        assertEquals(PLAYER, old.getUUID());
        assertEquals("Notch", old.getLegacyName());
        old.setServerVersion(ServerVersion.V_1_7_2);
        assertArrayEquals(protocolFour, encode(old, ServerVersion.V_1_7_2));
    }

    @Test
    public void teleportsAreAbsoluteAtEyeHeight() {
        for (ServerVersion version : LEGACY_SERVERS) {
            byte[] teleport = bytes(out -> {
                out.writeDouble(10.5);
                out.writeDouble(64.0 + 1.6200000047683716);
                out.writeDouble(-3.5);
                out.writeFloat(90.0f);
                out.writeFloat(10.0f);
                out.writeBoolean(false);
            });
            WrapperPlayServerPlayerPositionAndLook read = decode(
                    new WrapperPlayServerPlayerPositionAndLook(0, 0, 0, 0, 0, (byte) 0, 0, false), version, teleport);
            assertEquals(64.0, read.getY(), 1e-9);
            assertEquals(0, read.getRelativeFlags().getFullMask());
            assertArrayEquals(teleport, encode(new WrapperPlayServerPlayerPositionAndLook(10.5, 64.0, -3.5, 90.0f, 10.0f, (byte) 0, 0, false), version));
        }
    }

    @Test
    public void blockChangesUseTheLegacyGlobalId() {
        for (ServerVersion version : LEGACY_SERVERS) {
            byte[] change = bytes(out -> {
                out.writeInt(100);
                out.writeByte(70);
                out.writeInt(-20);
                varInt(out, 35);
                out.writeByte(14);
            });
            WrapperPlayServerBlockChange read = decode(new WrapperPlayServerBlockChange(new Vector3i(0, 0, 0), 0), version, change);
            assertEquals(35 << 4 | 14, read.getBlockId());
            assertEquals(new Vector3i(100, 70, -20), read.getBlockPosition());
            assertArrayEquals(change, encode(new WrapperPlayServerBlockChange(new Vector3i(100, 70, -20), 35 << 4 | 14), version));

            byte[] multi = bytes(out -> {
                out.writeInt(6);
                out.writeInt(-2);
                out.writeShort(2);
                out.writeInt(8);
                out.writeInt(3 << 28 | 9 << 24 | 70 << 16 | (35 << 4 | 14));
                out.writeInt(15 << 28 | 0 << 24 | 255 << 16 | (1 << 4));
            });
            WrapperPlayServerMultiBlockChange records = decode(
                    new WrapperPlayServerMultiBlockChange(new Vector3i(0, 0, 0), null, new EncodedBlock[0]), version, multi);
            EncodedBlock[] blocks = records.getBlocks();
            assertEquals(2, blocks.length);
            assertEquals(35 << 4 | 14, blocks[0].getBlockId());
            assertEquals(6 * 16 + 3, blocks[0].getX());
            assertEquals(70, blocks[0].getY());
            assertEquals(-2 * 16 + 9, blocks[0].getZ());
            assertEquals(1 << 4, blocks[1].getBlockId());
            assertEquals(6 * 16 + 15, blocks[1].getX());
            assertEquals(255, blocks[1].getY());
            assertArrayEquals(multi, encode(records, version));
        }
    }

    private static <T extends PacketWrapper<?>> T decode(T wrapper, ServerVersion version, byte[] payload) {
        wrapper.setServerVersion(version);
        wrapper.setBuffer(Unpooled.wrappedBuffer(payload));
        wrapper.read();
        assertEquals(0, ((ByteBuf) wrapper.getBuffer()).readableBytes(), "trailing bytes after decoding " + wrapper.getClass().getSimpleName());
        return wrapper;
    }

    private static byte[] encode(PacketWrapper<?> wrapper, ServerVersion version) {
        ByteBuf out = Unpooled.buffer();
        wrapper.setServerVersion(version);
        wrapper.setBuffer(out);
        wrapper.write();
        return ByteBufUtil.getBytes(out);
    }

    private static byte[] bytes(Consumer<ByteBuf> writer) {
        ByteBuf out = Unpooled.buffer();
        writer.accept(out);
        return ByteBufUtil.getBytes(out);
    }

    private static void string(ByteBuf out, String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        varInt(out, raw.length);
        out.writeBytes(raw);
    }

    private static void varInt(ByteBuf out, int value) {
        while ((value & ~0x7F) != 0) {
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
