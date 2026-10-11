package link.e4steam.retro.mixin;
import link.e4steam.lan.LanServerTasks;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerLanRetroMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void e4steam$applyLanSettings(CallbackInfo ci) { LanServerTasks.tick(this); }
    @org.spongepowered.asm.mixin.injection.Redirect(method = "shareToLAN", at = @At(value="INVOKE",
            target="Lnet/minecraft/util/HttpUtil;getSuitableLanPort()I"))
    private int e4steam$lanPort() { return link.e4steam.lan.LanSettings.current().port(); }
}
