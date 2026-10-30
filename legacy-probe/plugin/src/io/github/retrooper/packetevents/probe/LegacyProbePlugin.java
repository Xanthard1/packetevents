package io.github.retrooper.packetevents.probe;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class LegacyProbePlugin extends JavaPlugin {
    private final Map<PacketTypeCommon, Function<PacketReceiveEvent, PacketWrapper<?>>> incoming = new HashMap<>();
    private final Map<PacketTypeCommon, Function<PacketSendEvent, PacketWrapper<?>>> outgoing = new HashMap<>();
    private final Map<String, int[]> stats = new TreeMap<>();

    @Override
    public void onEnable() {
        incoming.put(PacketType.Play.Client.PLAYER_FLYING, WrapperPlayClientPlayerFlying::new);
        incoming.put(PacketType.Play.Client.PLAYER_POSITION, WrapperPlayClientPlayerFlying::new);
        incoming.put(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION, WrapperPlayClientPlayerFlying::new);
        incoming.put(PacketType.Play.Client.PLAYER_ROTATION, WrapperPlayClientPlayerFlying::new);
        incoming.put(PacketType.Play.Client.ANIMATION, WrapperPlayClientAnimation::new);
        incoming.put(PacketType.Play.Client.KEEP_ALIVE, WrapperPlayClientKeepAlive::new);
        incoming.put(PacketType.Play.Client.PLUGIN_MESSAGE, WrapperPlayClientPluginMessage::new);
        incoming.put(PacketType.Play.Client.PLAYER_DIGGING, WrapperPlayClientPlayerDigging::new);
        incoming.put(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT, WrapperPlayClientPlayerBlockPlacement::new);
        incoming.put(PacketType.Play.Client.HELD_ITEM_CHANGE, WrapperPlayClientHeldItemChange::new);
        incoming.put(PacketType.Play.Client.ENTITY_ACTION, WrapperPlayClientEntityAction::new);
        incoming.put(PacketType.Play.Client.CLICK_WINDOW, WrapperPlayClientClickWindow::new);
        incoming.put(PacketType.Play.Client.INTERACT_ENTITY, WrapperPlayClientInteractEntity::new);
        incoming.put(PacketType.Play.Client.CLIENT_SETTINGS, WrapperPlayClientSettings::new);

        outgoing.put(PacketType.Play.Server.JOIN_GAME, WrapperPlayServerJoinGame::new);
        outgoing.put(PacketType.Play.Server.KEEP_ALIVE, WrapperPlayServerKeepAlive::new);
        outgoing.put(PacketType.Play.Server.CHUNK_DATA, WrapperPlayServerChunkData::new);
        outgoing.put(PacketType.Play.Server.MAP_CHUNK_BULK, WrapperPlayServerChunkDataBulk::new);
        outgoing.put(PacketType.Play.Server.BLOCK_CHANGE, WrapperPlayServerBlockChange::new);
        outgoing.put(PacketType.Play.Server.MULTI_BLOCK_CHANGE, WrapperPlayServerMultiBlockChange::new);
        outgoing.put(PacketType.Play.Server.PLAYER_POSITION_AND_LOOK, WrapperPlayServerPlayerPositionAndLook::new);
        outgoing.put(PacketType.Play.Server.PLAYER_ABILITIES, WrapperPlayServerPlayerAbilities::new);
        outgoing.put(PacketType.Play.Server.UPDATE_HEALTH, WrapperPlayServerUpdateHealth::new);
        outgoing.put(PacketType.Play.Server.SPAWN_PLAYER, WrapperPlayServerSpawnPlayer::new);
        outgoing.put(PacketType.Play.Server.SPAWN_LIVING_ENTITY, WrapperPlayServerSpawnLivingEntity::new);
        outgoing.put(PacketType.Play.Server.ENTITY_METADATA, WrapperPlayServerEntityMetadata::new);
        outgoing.put(PacketType.Play.Server.ENTITY_VELOCITY, WrapperPlayServerEntityVelocity::new);
        outgoing.put(PacketType.Play.Server.ENTITY_TELEPORT, WrapperPlayServerEntityTeleport::new);
        outgoing.put(PacketType.Play.Server.ENTITY_RELATIVE_MOVE, WrapperPlayServerEntityRelativeMove::new);
        outgoing.put(PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION, WrapperPlayServerEntityRelativeMoveAndRotation::new);
        outgoing.put(PacketType.Play.Server.ENTITY_ROTATION, WrapperPlayServerEntityRotation::new);
        outgoing.put(PacketType.Play.Server.ENTITY_HEAD_LOOK, WrapperPlayServerEntityHeadLook::new);
        outgoing.put(PacketType.Play.Server.DESTROY_ENTITIES, WrapperPlayServerDestroyEntities::new);
        outgoing.put(PacketType.Play.Server.ENTITY_EFFECT, WrapperPlayServerEntityEffect::new);
        outgoing.put(PacketType.Play.Server.WINDOW_CONFIRMATION, WrapperPlayServerWindowConfirmation::new);
        outgoing.put(PacketType.Play.Server.ENTITY_ANIMATION, WrapperPlayServerEntityAnimation::new);
        outgoing.put(PacketType.Play.Server.ENTITY_STATUS, WrapperPlayServerEntityStatus::new);
        outgoing.put(PacketType.Play.Server.SET_SLOT, WrapperPlayServerSetSlot::new);
        outgoing.put(PacketType.Play.Server.WINDOW_ITEMS, WrapperPlayServerWindowItems::new);
        outgoing.put(PacketType.Play.Server.PLAYER_INFO, WrapperPlayServerPlayerInfo::new);
        outgoing.put(PacketType.Play.Server.SPAWN_ENTITY, WrapperPlayServerSpawnEntity::new);

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                Function<PacketReceiveEvent, PacketWrapper<?>> decoder = incoming.get(event.getPacketType());
                record(event, decoder == null ? null : () -> decoder.apply(event));
            }

            @Override
            public void onPacketSend(PacketSendEvent event) {
                Function<PacketSendEvent, PacketWrapper<?>> decoder = outgoing.get(event.getPacketType());
                record(event, decoder == null ? null : () -> decoder.apply(event));
            }
        });
        getLogger().info("probing " + PacketEvents.getAPI().getServerManager().getVersion());
    }

    private synchronized void record(ProtocolPacketEvent event, Supplier<PacketWrapper<?>> decoder) {
        String key = (event instanceof PacketReceiveEvent ? "C>S " : "S>C ") + event.getPacketType().getName()
                + "@" + event.getServerVersion().getReleaseName();
        int[] counts = stats.computeIfAbsent(key, k -> new int[4]);
        counts[0]++;
        if (decoder == null) return;
        try {
            boolean copied = event.getLastUsedWrapper() != null;
            PacketWrapper<?> wrapper = decoder.get();
            int left = ByteBufHelper.readableBytes(wrapper.getBuffer());
            if (left != 0 && !copied) {
                counts[2]++;
                if (counts[2] <= 3) getLogger().warning(key + " left " + left + " bytes unread");
            }
            describe(key, wrapper, counts);
            event.setLastUsedWrapper(null);
        } catch (Throwable failure) {
            counts[1]++;
            if (counts[1] <= 3) getLogger().log(Level.WARNING, key + " failed to decode", failure);
        }
    }

    private void describe(String key, PacketWrapper<?> wrapper, int[] counts) {
        if (counts[3]++ >= 2) return;
        if (wrapper instanceof WrapperPlayServerChunkData) {
            WrapperPlayServerChunkData chunk = (WrapperPlayServerChunkData) wrapper;
            getLogger().info(key + " column " + chunk.getColumn().getX() + "," + chunk.getColumn().getZ()
                    + " full=" + chunk.getColumn().isFullChunk() + " " + sample(chunk.getColumn().getChunks()));
        } else if (wrapper instanceof WrapperPlayServerChunkDataBulk) {
            WrapperPlayServerChunkDataBulk bulk = (WrapperPlayServerChunkDataBulk) wrapper;
            getLogger().info(key + " columns=" + bulk.getX().length + " first=" + bulk.getX()[0] + "," + bulk.getZ()[0]
                    + " " + sample(bulk.getChunks()[0]));
        } else if (wrapper instanceof WrapperPlayServerPlayerPositionAndLook) {
            WrapperPlayServerPlayerPositionAndLook teleport = (WrapperPlayServerPlayerPositionAndLook) wrapper;
            getLogger().info(key + " feet=" + teleport.getX() + "," + teleport.getY() + "," + teleport.getZ()
                    + " flags=" + teleport.getRelativeFlags().getFullMask());
        } else if (wrapper instanceof WrapperPlayClientPlayerFlying) {
            WrapperPlayClientPlayerFlying flying = (WrapperPlayClientPlayerFlying) wrapper;
            getLogger().info(key + " pos=" + flying.getLocation().getPosition() + " ground=" + flying.isOnGround());
        } else if (wrapper instanceof WrapperPlayServerBlockChange) {
            WrapperPlayServerBlockChange change = (WrapperPlayServerBlockChange) wrapper;
            getLogger().info(key + " " + change.getBlockPosition() + " -> " + change.getBlockState().getType().getName());
        } else {
            getLogger().info(key + " decoded");
        }
    }

    private static String sample(BaseChunk[] sections) {
        int present = 0;
        String bottom = "none";
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) continue;
            present++;
            if (bottom.equals("none")) {
                int id = sections[i].getBlockId(0, 0, 0);
                bottom = "y" + (i * 16) + "=" + id + "/" + WrappedBlockState.getByGlobalId(
                        PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), id).getType().getName();
            }
        }
        return "sections=" + present + " " + bottom;
    }

    @Override
    public synchronized void onDisable() {
        getLogger().info("probe report (seen / failed / leftover)");
        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            int[] c = entry.getValue();
            getLogger().info(String.format("  %-60s %6d %4d %4d", entry.getKey(), c[0], c[1], c[2]));
        }
    }
}
