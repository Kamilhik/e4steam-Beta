package link.e4steam.mixin;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.lan.LanSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 folded multiplayer options into the broader world options screen. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.WorldOptionsScreen", remap = false)
public abstract class WorldOptionsScreenMixin extends Screen {
    protected WorldOptionsScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void e4steam$addLanSettings(CallbackInfo ci) {
        if (minecraft.getSingleplayerServer() == null) return;
        addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.lan.settings"),
                button -> MinecraftUiCompat.setScreen(minecraft, new LanSettingsScreen(this)),
                4, 6, 150, 20));
    }
}
