package link.e4steam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Selects the correct renderer for the Minecraft GUI generation in use. */
public final class SteamFriendsScreens {
    private static final String EXTRACTOR = "link.e4steam.SteamFriends26Screen";
    private static final String MODERN = "link.e4steam.ModernSteamFriendsScreen";
    private static final String LEGACY = "link.e4steam.LegacySteamFriendsScreen";

    private SteamFriendsScreens() {
    }

    public static Screen create(Screen parent) {
        String className = rendererClassName();
        try {
            Class<?> type = Class.forName(className, true, Screen.class.getClassLoader());
            Constructor<?> constructor = type.getConstructor(Screen.class);
            return (Screen) constructor.newInstance(parent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            E4steamClient.LOGGER.warn(
                    "The Steam Friends screen is unavailable on this Minecraft/mod combination",
                    exception
            );
            return parent;
        }
    }

    public static void showInvitationToast(
            long lobbyId,
            long friendSteamId,
            String friendName,
            String minecraftName,
            long invitationGeneration
    ) {
        String className = rendererClassName();
        try {
            Class<?> type = Class.forName(className, true, Screen.class.getClassLoader());
            Method method = type.getMethod(
                    "showInvitationToast",
                    long.class,
                    long.class,
                    String.class,
                    String.class,
                    long.class
            );
            method.invoke(null, lobbyId, friendSteamId, friendName, minecraftName, invitationGeneration);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            E4steamClient.LOGGER.debug("Native e4steam invitation toast is unavailable", exception);
            Minecraft.getInstance().gui.getChat().addMessage(Mirror.translatable(
                    "text.e4steam_minecraft.friends.invite.toast", friendName
            ));
        }
    }

    public static void showJoinRequestToast(
            long friendSteamId,
            String friendName,
            String minecraftName,
            long requestGeneration
    ) {
        String className = rendererClassName();
        try {
            Class<?> type = Class.forName(className, true, Screen.class.getClassLoader());
            Method method = type.getMethod(
                    "showJoinRequestToast",
                    long.class,
                    String.class,
                    String.class,
                    long.class
            );
            method.invoke(null, friendSteamId, friendName, minecraftName, requestGeneration);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            E4steamClient.LOGGER.debug("Native e4steam join-request toast is unavailable", exception);
            Minecraft.getInstance().gui.getChat().addMessage(Mirror.translatable(
                    "text.e4steam_minecraft.friends.join_request.toast", friendName
            ));
        }
    }

    private static String rendererClassName() {
        try {
            Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor", false, Screen.class.getClassLoader());
            return EXTRACTOR;
        } catch (ClassNotFoundException | LinkageError ignored) {
            try {
                Class.forName("net.minecraft.client.gui.GuiGraphics", false, Screen.class.getClassLoader());
                return MODERN;
            } catch (ClassNotFoundException | LinkageError legacy) {
                return LEGACY;
            }
        }
    }
}
