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

package io.github.retrooper.packetevents.netty.channel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

final class FailedWriteReporter implements ChannelFutureListener {
    static final FailedWriteReporter INSTANCE = new FailedWriteReporter();
    private static final long INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final AtomicLong nextReportAt = new AtomicLong(Long.MIN_VALUE);
    private final AtomicInteger unreported = new AtomicInteger();

    private FailedWriteReporter() {
    }

    static Object watch(Object future) {
        ((ChannelFuture) future).addListener(INSTANCE);
        return future;
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) return;
        Throwable cause = future.cause();
        Channel channel = future.channel();
        if (cause == null || cause instanceof ClosedChannelException || !channel.isOpen()) return;
        int failures = unreported.incrementAndGet();
        long now = System.nanoTime();
        long due = nextReportAt.get();
        if (due != Long.MIN_VALUE && now - due < 0) return;
        if (!nextReportAt.compareAndSet(due, now + INTERVAL_NANOS)) return;
        unreported.addAndGet(-failures);
        PacketEvents.getAPI().getLogger().log(Level.WARNING, "A packet a plugin sent to " + playerName(channel)
                + " never reached the connection (" + failures + " failed in the last 10 s)", cause);
    }

    private static String playerName(Channel channel) {
        try {
            User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
            if (user != null && user.getName() != null) return user.getName();
        } catch (RuntimeException ignored) {
        }
        return "a player";
    }
}
