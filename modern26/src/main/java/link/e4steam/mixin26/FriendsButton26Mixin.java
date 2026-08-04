package link.e4steam.mixin26;

import link.e4steam.OptionalCompatibility;
import link.e4steam.SteamFriends26Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects Minecraft 26.2's original Friends button to the Steam-backed overlay. */
@Mixin(SpriteIconButton.class)
public abstract class FriendsButton26Mixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void e4steam$openSteamFriends(InputWithModifiers input, CallbackInfo ci) {
        if (!((Object) this instanceof FriendsButton)) {
            return;
        }
        OptionalCompatibility.run("minecraft-26-friends-button", () -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen parent = minecraft.gui.screen();
            minecraft.gui.setScreen(new SteamFriends26Screen(parent));
            ci.cancel();
        });
    }
}
