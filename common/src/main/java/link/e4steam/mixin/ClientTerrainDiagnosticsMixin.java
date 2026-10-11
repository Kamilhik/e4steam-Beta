package link.e4steam.mixin;

import link.e4steam.steam.SteamConnectionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts main-thread terrain application separately from network decoding. */
@Mixin(ClientPacketListener.class)
public abstract class ClientTerrainDiagnosticsMixin {
    @Unique private long e4steam$handledChunks;
    @Unique private long e4steam$missingAfterHandle;
    @Unique private int e4steam$centerX;
    @Unique private int e4steam$centerZ;
    @Unique private int e4steam$radius = -1;
    @Unique private boolean e4steam$hasCenter;
    @Unique private long e4steam$nextTerrainLog;

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void e4steam$chunkApplied(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level == null) return;
        e4steam$handledChunks++;
        if (minecraft.level.getChunkSource().getChunk(packet.getX(), packet.getZ(), ChunkStatus.FULL, false) == null)
            e4steam$missingAfterHandle++;
    }

    @Inject(method = "handleSetChunkCacheCenter", at = @At("TAIL"))
    private void e4steam$cacheCenter(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS || !Minecraft.getInstance().isSameThread()) return;
        e4steam$centerX = packet.getX();
        e4steam$centerZ = packet.getZ();
        e4steam$hasCenter = true;
    }

    @Inject(method = "handleSetChunkCacheRadius", at = @At("TAIL"))
    private void e4steam$cacheRadius(ClientboundSetChunkCacheRadiusPacket packet, CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS || !Minecraft.getInstance().isSameThread()) return;
        e4steam$radius = packet.getRadius();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void e4steam$terrainState(CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long now = System.currentTimeMillis();
        if (now < e4steam$nextTerrainLog) return;
        e4steam$nextTerrainLog = now + 5_000L;
        int x = minecraft.player.blockPosition().getX() >> 4;
        int z = minecraft.player.blockPosition().getZ() >> 4;
        boolean playerChunk = minecraft.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null;
        LogManager.getLogger("e4steam").info(
                "[E4NET-TERRAIN] listener={} handled={} missingAfterHandle={} loaded={} playerChunk={} "
                        + "centerSeen={} centerDeltaX={} centerDeltaZ={} radius={} y={} velocityY={} onGround={} blockBelowAir={}",
                Integer.toHexString(System.identityHashCode(this)), e4steam$handledChunks,
                e4steam$missingAfterHandle, minecraft.level.getChunkSource().getLoadedChunksCount(), playerChunk,
                e4steam$hasCenter, e4steam$hasCenter ? e4steam$centerX - x : 0,
                e4steam$hasCenter ? e4steam$centerZ - z : 0, e4steam$radius,
                minecraft.player.getY(), minecraft.player.getDeltaMovement().y, link.e4steam.lan.LanReflection.read(minecraft.player, "onGround", "isOnGround", "method_24828", "m_20096_", "f_19861_", "field_5952"),
                playerChunk && minecraft.level.getBlockState(minecraft.player.blockPosition().below()).isAir());
    }
}
