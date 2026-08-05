package link.e4steam.mixin;

import link.e4steam.E4steamClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releases Steam and its LAN bridges before vanilla waits for the integrated server. */
@Mixin(Minecraft.class)
public abstract class MinecraftShutdownMixin {
    @Inject(method = "destroy", at = @At("HEAD"), require = 0)
    private void e4steam$shutdownBeforeWorldDisconnect(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null && server.isRunning()) {
            // NeoForge can otherwise enter Minecraft#disconnect's wait loop
            // before its JVM shutdown hook has told the integrated server to stop.
            server.halt(false);
        }
        E4steamClient.shutdown();
    }
}
