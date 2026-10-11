package link.e4steam.retro.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.RetroSteamAuthentication;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerLoginServer;
import net.minecraft.util.ChatComponentText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Steam-authenticated login bridge for the Forge 1.8.x branch. */
@Mixin(NetHandlerLoginServer.class)
public abstract class NetHandlerLoginServerMixin {
    @Shadow @Final public NetworkManager networkManager;
    @Shadow private GameProfile loginGameProfile;

    @org.spongepowered.asm.mixin.Unique
    private final link.e4steam.skin.SkinRestoration.LoginState e4steam$skins = new link.e4steam.skin.SkinRestoration.LoginState();

    private long e4steam$authenticatedSteamId;

    private long e4steam$authenticatedSteamId() {
        long current = e4steam$authenticatedSteamId;
        if (current != 0L) return current;
        current = RetroSteamAuthentication.authenticatedPeer(
                networkManager.getRemoteAddress());
        if (current != 0L) e4steam$authenticatedSteamId = current;
        return current;
    }

    @Redirect(
            method = "processLoginStart",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;isServerInOnlineMode()Z"
            )
    )
    private boolean e4steam$useMojangAuthentication(MinecraftServer server) {
        return e4steam$authenticatedSteamId() == 0L
                && server.isServerInOnlineMode();
    }

    @Inject(method = "processLoginStart", at = @At("HEAD"), cancellable = true)
    private void e4steam$rejectDirectDedicatedLogin(CallbackInfo info) {
        if (e4steam$authenticatedSteamId() != 0L
                || !RetroSteamAuthentication.rejectUntrustedDedicatedIngress(
                        networkManager.getRemoteAddress())) return;
        networkManager.closeChannel(new ChatComponentText(
                "This server requires an authenticated e4steam connection"));
        info.cancel();
    }

    @Inject(method = "tryAcceptPlayer", at = @At("HEAD"), cancellable = true)
    private void e4steam$bindSteamIdentity(CallbackInfo info) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        if (loginGameProfile == null) return;
        if (authenticatedSteamId != 0L) loginGameProfile = link.e4steam.steam.SteamMinecraftProfile.bind(loginGameProfile, authenticatedSteamId);
        if (!e4steam$skins.prepare(loginGameProfile)) { info.cancel(); return; }
        loginGameProfile = (GameProfile)e4steam$skins.profile();
    }
}
