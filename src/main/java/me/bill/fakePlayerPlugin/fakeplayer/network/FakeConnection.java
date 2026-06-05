package me.bill.fakePlayerPlugin.fakeplayer.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.config.Config;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

public final class FakeConnection extends Connection {

    public FakeConnection(InetAddress address) {
        super(PacketFlow.SERVERBOUND);
        this.channel = new FakeChannel(null, address);
        this.address = new InetSocketAddress(address, 25565);

        Connection.configureSerialization(this.channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
    }

    @Override
    public void tick() {
        // Prevent parent Connection.tick() from running keepalive state machines.
        // Fake players have no real network client to respond to keepalive challenges.
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void disconnect(Component reason) {
        String reasonStr = reason != null ? reason.getString() : "null";
        Config.debugNmsConn("FakeConnection.disconnect: reason='" + reasonStr + "' for " + this.address);
    }

    @Override
    public void send(@NotNull Packet<?> packet) {}

    @Override
    public void send(@NotNull Packet<?> packet, @Nullable ChannelFutureListener listener) {}

    @Override
    public void send(@NotNull Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}

    public void send(@NotNull Packet<?> packet, @Nullable PacketSendListener listener) {}

    public void send(@NotNull Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {}
}
