package dev.k2studio.e4steam.publicservers.mixin;
import dev.k2studio.e4steam.publicservers.client.PublicServersScreen;
import dev.k2studio.e4steam.publicservers.client.ServerNameScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Draw after background effects and native controls on every supported GUI API. */
@Mixin(Screen.class)
public abstract class PublicServersRenderMixin {
    @Inject(method = "/^(render|method_25394|m_88315_)$/", at = @At("TAIL"), require = 0, remap = false)
    private void publicServers$cards(@Coerce Object graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof PublicServersScreen) ((PublicServersScreen) (Object) this).renderCatalog(graphics, mouseX, mouseY);
        if ((Object) this instanceof ServerNameScreen) ((ServerNameScreen) (Object) this).renderLabels(graphics);
    }
}
