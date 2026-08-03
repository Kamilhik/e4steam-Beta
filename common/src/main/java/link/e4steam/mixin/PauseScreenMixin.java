package link.e4steam.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.FriendsUiIcons;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.MinecraftVersion;
import link.e4steam.Mirror;
import link.e4steam.SteamFriendsScreens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique
    private Button e4steam$inviteButton;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$addInviteButton(CallbackInfo ci) {
        if (MinecraftVersion.current().startsWith("26.")) {
            for (GuiEventListener child : children()) {
                if (child.getClass().getName().equals("net.minecraft.client.gui.components.FriendsButton")) {
                    return;
                }
            }
        }
        e4steam$inviteButton = addRenderableWidget(
                MinecraftUiCompat.iconButton(
                        FriendsUiIcons.friends(),
                        Mirror.translatable("text.e4steam_minecraft.steamFriendsHelp"),
                        button -> {
                            if (minecraft != null) {
                                MinecraftUiCompat.setScreen(
                                        minecraft,
                                        SteamFriendsScreens.create((Screen) (Object) this)
                                );
                            }
                        },
                        Math.max(4, width - 26),
                        6,
                        20,
                        20,
                        "minecraft:friends/friends",
                        15,
                        15
                )
        );
    }
}
