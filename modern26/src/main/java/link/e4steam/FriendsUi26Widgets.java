package link.e4steam;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/** Minecraft 26.x widget boundary for native sprite-only Friends buttons. */
public final class FriendsUi26Widgets {
    private FriendsUi26Widgets() {
    }

    public static Button iconButton(
            Component tooltip,
            Consumer<Button> onPress,
            int x,
            int y,
            int width,
            int height,
            String sprite,
            int spriteWidth,
            int spriteHeight
    ) {
        Button[] holder = new Button[1];
        SpriteIconButton button = SpriteIconButton.builder(
                        tooltip,
                        input -> onPress.accept(holder[0]),
                        true
                )
                .size(width, height)
                .sprite(sprite.indexOf(':') >= 0
                        ? Identifier.parse(sprite)
                        : Identifier.fromNamespaceAndPath("e4steam_minecraft", sprite), spriteWidth, spriteHeight)
                .tooltip(tooltip)
                .build();
        button.setPosition(x, y);
        holder[0] = button;
        return button;
    }

    public static Button iconButton(
            Component tooltip,
            Consumer<Button> onPress,
            int x,
            int y,
            int width,
            int height,
            String sprite,
            String highlightedSprite,
            int spriteWidth,
            int spriteHeight
    ) {
        Button[] holder = new Button[1];
        WidgetSprites sprites = new WidgetSprites(Identifier.parse(sprite), Identifier.parse(highlightedSprite));
        SpriteIconButton button = SpriteIconButton.builder(
                        tooltip,
                        input -> onPress.accept(holder[0]),
                        true
                )
                .size(width, height)
                .sprite(sprites, spriteWidth, spriteHeight)
                .tooltip(tooltip)
                .switchToLoadingAfterPress()
                .build();
        button.setPosition(x, y);
        holder[0] = button;
        return button;
    }
}
