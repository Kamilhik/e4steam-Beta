package link.e4steam.mixin;

import link.e4steam.FriendsUiIcons;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.MinecraftVersion;
import link.e4steam.Mirror;
import link.e4steam.SteamFriendsScreens;
import link.e4steam.E4steamClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$addSteamFriends(CallbackInfo ci) {
        E4steamClient.ensureSocialPresence();
        int buttonX = width / 2 - 10;
        int buttonY = height / 4 + 120;

        for (GuiEventListener child : new ArrayList<>(children())) {
            if (!child.getClass().getName().equals("net.minecraft.client.gui.components.FriendsButton")) {
                continue;
            }
            // Minecraft 26.2 already owns the exact Snapshot Friends button.
            // Its action is redirected to e4steam by the 26.x compatibility mixin.
            if (MinecraftVersion.current().startsWith("26.")) {
                return;
            }
            try {
                Method getX = child.getClass().getMethod("getX");
                Method getY = child.getClass().getMethod("getY");
                buttonX = (int) getX.invoke(child);
                buttonY = (int) getY.invoke(child);
                removeWidget(child);
            } catch (ReflectiveOperationException ignored) {
            }
            break;
        }

        Button button = addRenderableWidget(MinecraftUiCompat.iconButton(
                FriendsUiIcons.friends(),
                Mirror.translatable("text.e4steam_minecraft.steamFriendsHelp"),
                ignored -> {
                    if (minecraft != null) {
                        MinecraftUiCompat.setScreen(minecraft, SteamFriendsScreens.create((Screen) (Object) this));
                    }
                },
                buttonX,
                buttonY,
                20,
                20,
                "minecraft:friends/friends",
                15,
                15
        ));
    }

}
