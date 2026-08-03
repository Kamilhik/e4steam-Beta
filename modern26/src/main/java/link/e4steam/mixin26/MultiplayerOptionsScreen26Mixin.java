package link.e4steam.mixin26;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAccessMode;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 26.x replacement for the removed ShareToLanScreen integration. */
@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreen26Mixin extends Screen {
    @Unique
    private CycleButton<SteamAccessMode> e4steam$accessButton;

    protected MultiplayerOptionsScreen26Mixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$addSteamAccessMode(CallbackInfo ci) {
        if (!E4steamClient.isHostEnabled()) return;
        SteamAccessMode initial = E4steamClient.selectedAccessMode;
        if (initial == null) {
            initial = SteamAccessMode.FRIENDS_ONLY;
            E4steamClient.selectedAccessMode = initial;
        }
        e4steam$accessButton = CycleButton.<SteamAccessMode>builder(
                        mode -> Component.translatable(mode.translationKey()), initial)
                .withValues(
                        SteamAccessMode.LOCAL_ONLY,
                        SteamAccessMode.FRIENDS_ONLY,
                        SteamAccessMode.INVITE_ONLY
                )
                .create(
                        width / 2 - 102,
                        height - 58,
                        204,
                        20,
                        Component.translatable("text.e4steam_minecraft.accessMode"),
                        (button, mode) -> E4steamClient.selectedAccessMode = mode
                );
        e4steam$accessButton.setTooltip(Tooltip.create(
                Component.translatable("text.e4steam_minecraft.accessModeHelp")));
        addRenderableWidget(e4steam$accessButton);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void e4steam$repositionSteamAccessMode(CallbackInfo ci) {
        if (e4steam$accessButton != null) {
            e4steam$accessButton.setX(width / 2 - 102);
            e4steam$accessButton.setY(height - 58);
        }
    }
}
