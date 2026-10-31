/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2022 retrooper and contributors
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

package io.github.retrooper.packetevents.manager.server;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.LogManager;
import com.github.retrooper.packetevents.util.mappings.GlobalRegistryHolder;
import io.github.retrooper.packetevents.platform.ServerRuntime;
import org.bukkit.Bukkit;

public class ServerManagerImpl implements ServerManager {
    private ServerVersion serverVersion;

    private ServerVersion resolveVersionNoCache() {
        LogManager logger = PacketEvents.getAPI().getLogManager();
        String bukkitVersion = Bukkit.getBukkitVersion();
        ServerVersion fallbackVersion = ServerRuntime.LEGACY_SERVER ? legacyVersionFromPackage() : ServerVersion.V_1_8_8;

        String failureToDetectVersionMsg = "Your server software is preventing us from checking the Minecraft Server version. This is what we found: " + Bukkit.getBukkitVersion() + ". We will assume the Server version is " + fallbackVersion.name() + "...\n If you need assistance, join our Discord server: https://discord.gg/DVHxPPxHZc";

        if (bukkitVersion.contains("Unknown")) {
            ServerVersion reported = versionFromMinecraftVersion(Bukkit.getVersion());
            if (reported != null) {
                return reported;
            }
            if (ServerRuntime.LEGACY_SERVER) {
                return fallbackVersion;
            }
            logger.warn(failureToDetectVersionMsg);
            return fallbackVersion;
        }

        for (final ServerVersion val : ServerVersion.reversedValues()) {
            //For example "V_1_18" -> "1.18"
            if (bukkitVersion.contains(val.getReleaseName())) {
                return val;
            }
        }

        logger.warn(failureToDetectVersionMsg);
        return fallbackVersion;
    }

    private static ServerVersion versionFromMinecraftVersion(String version) {
        int start = version == null ? -1 : version.indexOf("MC: ");
        if (start == -1) {
            return null;
        }
        int end = version.indexOf(')', start);
        String release = version.substring(start + 4, end == -1 ? version.length() : end).trim();
        for (ServerVersion val : ServerVersion.reversedValues()) {
            if (release.equals(val.getReleaseName())) {
                return val;
            }
        }
        return null;
    }

    private static ServerVersion legacyVersionFromPackage() {
        String nmsVersion = ServerRuntime.nmsPackageVersion();
        if ("v1_7_R1".equals(nmsVersion)) {
            return ServerVersion.V_1_7_2;
        }
        if ("v1_7_R2".equals(nmsVersion)) {
            return ServerVersion.V_1_7_5;
        }
        if ("v1_7_R3".equals(nmsVersion)) {
            return ServerVersion.V_1_7_9;
        }
        return ServerVersion.V_1_7_10;
    }

    @Override
    public ServerVersion getVersion() {
        if (serverVersion == null) {
            serverVersion = resolveVersionNoCache();
        }
        return serverVersion;
    }

    @Override
    public Object getRegistryCacheKey(User user, ClientVersion version) {
        return GlobalRegistryHolder.getGlobalRegistryCacheKey(user, version);
    }
}
