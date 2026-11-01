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

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// PacketWrapper(event) reads the packet inside super(), before the subclass's field initialisers run, so an
// initialiser or statement after that call silently replaces what was read and a re-encode sends the default.
@DisplayName("Wrappers keep what their event constructor read")
public class WrapperReadOrderTest {
    private static final String SEND_EVENT = "(Lcom/github/retrooper/packetevents/event/PacketSendEvent;)V";
    private static final String RECEIVE_EVENT = "(Lcom/github/retrooper/packetevents/event/PacketReceiveEvent;)V";

    @Test
    public void nothingRunsAfterTheReadingSuperCall() throws IOException {
        List<String> found = new ArrayList<>();
        try (ScanResult scan = new ClassGraph().acceptPackages("com.github.retrooper.packetevents.wrapper").scan()) {
            for (ClassInfo info : scan.getSubclasses(PacketWrapper.class.getName())) {
                found.addAll(statementsAfterRead(new DataInputStream(new ByteArrayInputStream(info.getResource().load()))));
            }
        }
        assertEquals(new ArrayList<String>(), found, "give these fields their defaults in the other constructors instead");
    }

    private static List<String> statementsAfterRead(DataInputStream in) throws IOException {
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        int[] first = new int[count];
        int[] second = new int[count];
        String[] utf = new String[count];
        for (int index = 1; index < count; index++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:
                    utf[index] = in.readUTF();
                    break;
                case 3:
                case 4:
                    in.readInt();
                    break;
                case 5:
                case 6:
                    in.readLong();
                    index++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    first[index] = in.readUnsignedShort();
                    break;
                case 15:
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                    break;
                default:
                    first[index] = in.readUnsignedShort();
                    second[index] = in.readUnsignedShort();
                    break;
            }
        }
        in.readUnsignedShort();
        String self = utf[first[in.readUnsignedShort()]];
        String parent = utf[first[in.readUnsignedShort()]];
        in.skipBytes(2 * in.readUnsignedShort());
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.skipBytes(6);
            skipAttributes(in);
        }
        List<String> found = new ArrayList<>();
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.readUnsignedShort();
            String name = utf[in.readUnsignedShort()];
            String descriptor = utf[in.readUnsignedShort()];
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                String attribute = utf[in.readUnsignedShort()];
                byte[] body = new byte[in.readInt()];
                in.readFully(body);
                boolean eventConstructor = name.equals("<init>") && (descriptor.equals(SEND_EVENT) || descriptor.equals(RECEIVE_EVENT));
                if (!eventConstructor || !attribute.equals("Code")) continue;
                byte[] code = new byte[((body[4] & 0xFF) << 24) | ((body[5] & 0xFF) << 16) | ((body[6] & 0xFF) << 8) | (body[7] & 0xFF)];
                System.arraycopy(body, 8, code, 0, code.length);
                if (code.length < 6 || (code[0] & 0xFF) != 0x2A || (code[1] & 0xFF) != 0x2B || (code[2] & 0xFF) != 0xB7) continue;
                int method = ((code[3] & 0xFF) << 8) | (code[4] & 0xFF);
                String owner = utf[first[first[method]]];
                String called = utf[first[second[method]]];
                String calledDescriptor = utf[second[second[method]]];
                if (!owner.equals(parent) || !called.equals("<init>") || !calledDescriptor.equals(descriptor)) continue;
                if ((code[5] & 0xFF) != 0xB1) found.add(self.replace('/', '.') + "(" + descriptor.substring(descriptor.lastIndexOf('/') + 1, descriptor.length() - 3) + ")");
            }
        }
        return found;
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            in.readUnsignedShort();
            in.skipBytes(in.readInt());
        }
    }
}
