package link.e4steam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/** Snapshot 7 tab visuals backed by the vanilla 26.2 Friends resources. */
final class FriendsUi26TabButton extends Button {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("friends/button"),
            Identifier.withDefaultNamespace("friends/button_disabled"),
            Identifier.withDefaultNamespace("friends/button_highlighted"),
            Identifier.withDefaultNamespace("friends/button_highlighted")
    );

    private final BooleanSupplier selected;

    FriendsUi26TabButton(
            int x,
            int y,
            int width,
            Component message,
            Runnable onPress,
            BooleanSupplier selected
    ) {
        super(x, y, width, 20, message, input -> onPress.run(), DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = selected.getAsBoolean();
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SPRITES.get(isSelected, isHoveredOrFocused()),
                getX(), getY(), getWidth(), getHeight()
        );
        int color = active ? 0xffffffff : 0xffa0a0a0;
        int textY = getY() + (isSelected ? 6 : 7);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, textY, color);
        if (isSelected) {
            int underlineWidth = Math.min(Minecraft.getInstance().font.width(getMessage()), getWidth() - 4);
            int underlineX = getX() + (getWidth() - underlineWidth) / 2;
            graphics.fill(underlineX, getY() + getHeight() - 2,
                    underlineX + underlineWidth, getY() + getHeight() - 1, color);
        }
    }
}
