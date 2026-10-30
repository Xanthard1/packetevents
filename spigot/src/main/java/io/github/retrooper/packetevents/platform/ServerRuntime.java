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

package io.github.retrooper.packetevents.platform;

import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ServerRuntime {
    public static final boolean RELOCATED_LIBRARIES = !present("io.netty.channel.Channel")
            && present("net.minecraft.util.io.netty.channel.Channel");

    private static final String NETTY_PACKAGE = RELOCATED_LIBRARIES ? "net.minecraft.util.io.netty." : "io.netty.";
    private static final String AUTHLIB_PACKAGE = RELOCATED_LIBRARIES ? "net.minecraft.util.com.mojang.authlib." : "com.mojang.authlib.";
    private static final String IMPLEMENTATION_PACKAGE = RELOCATED_LIBRARIES
            ? "io.github.retrooper.packetevents.legacy." : "io.github.retrooper.packetevents.";

    private static final Method ONLINE_PLAYERS = onlinePlayersMethod();
    private static Supplier<Object> pooledBuffers;
    private static Boolean protocolPatch;

    private ServerRuntime() {
    }

    public static String nettyClass(String name) {
        return NETTY_PACKAGE + name;
    }

    public static String authlibClass(String name) {
        return AUTHLIB_PACKAGE + name;
    }

    // Spigot's 1.7.10 protocol patch accepts 1.8 clients natively and talks 1.8 to them, so those
    // connections are decoded in their own format rather than the server's.
    public static boolean usesClientWireFormat(ClientVersion clientVersion) {
        return clientVersion != null
                && clientVersion.getProtocolVersion() == ClientVersion.V_1_8.getProtocolVersion()
                && isProtocolPatch();
    }

    public static boolean isProtocolPatch() {
        Boolean patched = protocolPatch;
        if (patched == null) {
            patched = RELOCATED_LIBRARIES && hasNetworkManagerVersion();
            protocolPatch = patched;
        }
        return patched;
    }

    private static boolean hasNetworkManagerVersion() {
        String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
        String nmsVersion = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
        try {
            Method version = Class.forName("net.minecraft.server." + nmsVersion + ".NetworkManager").getMethod("getVersion");
            return version.getReturnType() == int.class;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            return false;
        }
    }

    public static SpigotInjector createInjector() {
        return instantiate("injector.SpigotChannelInjector");
    }

    public static NettyManager createNettyManager() {
        return instantiate("netty.NettyManagerImpl");
    }

    public static void destroyHandlers(Object channel) {
        try {
            Class.forName(IMPLEMENTATION_PACKAGE + "injector.connection.ServerConnectionInitializer")
                    .getMethod("destroyHandlers", Object.class)
                    .invoke(null, channel);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove packetevents handlers", exception);
        }
    }

    public static Object pooledBuffer() {
        Supplier<Object> supplier = pooledBuffers;
        if (supplier == null) {
            supplier = instantiate("netty.buffer.PooledBufferSupplier");
            pooledBuffers = supplier;
        }
        return supplier.get();
    }

    @SuppressWarnings("unchecked")
    public static Collection<? extends Player> onlinePlayers() {
        try {
            Object players = ONLINE_PLAYERS.invoke(null);
            if (players instanceof Player[]) {
                return Collections.unmodifiableList(Arrays.asList((Player[]) players));
            }
            return (Collection<? extends Player>) players;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to list online players", exception);
        }
    }

    // 1.7.2-1.7.9 only declare Player[] getOnlinePlayers(); a direct call compiled against Collection fails to link there.
    private static Method onlinePlayersMethod() {
        try {
            return Bukkit.class.getMethod("getOnlinePlayers");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T instantiate(String relativeName) {
        try {
            return (T) Class.forName(IMPLEMENTATION_PACKAGE + relativeName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create " + IMPLEMENTATION_PACKAGE + relativeName, exception);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, ServerRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }
}
