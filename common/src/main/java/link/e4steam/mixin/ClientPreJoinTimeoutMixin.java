package link.e4steam.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import link.e4steam.internal.api.CoreApiPlatform;
import link.e4steam.steam.SteamClientBridge;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Steam pre-login download may take longer than Minecraft's ordinary read timeout. */
@Mixin(Connection.class)
public abstract class ClientPreJoinTimeoutMixin {
    @Unique private boolean e4steam$extendedTimeout;
    @Unique private boolean e4steam$receivedPacket;
    @Inject(method = "channelActive", at = @At("TAIL"), require = 0)
    private void e4steam$preJoinTimeout(ChannelHandlerContext context, CallbackInfo callback) {
        CoreApiPlatform platform = CoreApiPlatform.current();
        if (platform == null || !platform.addonNetwork().hasPreJoinHandlers()) return;
        context.executor().schedule(() -> {
            if (!context.channel().isActive() || e4steam$receivedPacket) return;
            java.net.SocketAddress address = context.channel().remoteAddress();
            if (!SteamClientBridge.isLocalEndpoint(address)
                    && link.e4steam.steam.SteamMinecraftAuthentication.authenticatedPeer(address) == 0L) return;
            ReadTimeoutHandler timeout = context.pipeline().get(ReadTimeoutHandler.class);
            if (timeout != null) {
                context.pipeline().replace(timeout, "e4steam-prejoin-timeout", new ReadTimeoutHandler(660));
                e4steam$extendedTimeout = true;
            }
        }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), require = 0)
    private void e4steam$restoreTimeout(ChannelHandlerContext context,
            net.minecraft.network.protocol.Packet<?> packet, CallbackInfo callback) {
        e4steam$receivedPacket = true;
        if (!e4steam$extendedTimeout) return;
        ReadTimeoutHandler timeout = context.pipeline().get(ReadTimeoutHandler.class);
        if (timeout != null) context.pipeline().replace(timeout, "e4steam-game-timeout", new ReadTimeoutHandler(30));
        e4steam$extendedTimeout = false;
    }
}
