package link.e4steam.retro.mixin;
import link.e4steam.lan.LanServerTasks;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerLanRetroMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void e4steam$applyLanSettings(CallbackInfo ci) { LanServerTasks.tick(this); }
}
