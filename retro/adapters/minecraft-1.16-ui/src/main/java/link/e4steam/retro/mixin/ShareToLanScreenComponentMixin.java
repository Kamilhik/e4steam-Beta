package link.e4steam.retro.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenComponentMixin {
    @Inject(method="init",at=@At("HEAD"),cancellable=true)
    private void e4steam$settings(CallbackInfo ci) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.getSingleplayerServer()==null)return;
        ci.cancel();minecraft.setScreen(new link.e4steam.retro.RetroLanScreen(null));
    }
}
