package dev.k2studio.e4steam.publicservers.client;

import dev.k2studio.e4steam.publicservers.common.PublicServersRuntime;
import dev.k2studio.e4steam.publicservers.contract.TextPolicy;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Explicit save/cancel dialog for the local public-world title. */
public final class ServerNameScreen extends Screen {
    private final Screen parent;
    private EditBox name;
    private Button save;
    private String draft = PublicServersRuntime.customServerName();
    private String messageKey = "e4steam_public_servers.name.help";
    public ServerNameScreen(Screen parent) {
        super(Component.translatable("e4steam_public_servers.name.title"));
        this.parent = parent;
    }
    @Override protected void init() {
        int w = Math.min(320, width - 32), x = (width - w) / 2, y = height / 2;
        name = addRenderableWidget(new EditBox(font, x, y - 20, w, 20, title));
        name.setMaxLength(48);
        name.setValue(draft);
        name.setHint(Component.literal(PublicServersRuntime.defaultServerName()));
        int bw = (w - 8) / 3;
        save = addRenderableWidget(Button.builder(Component.translatable("e4steam_public_servers.name.save"), ignored -> saveName()).bounds(x, y + 36, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("e4steam_public_servers.name.reset"), ignored -> name.setValue("")).bounds(x + bw + 4, y + 36, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("e4steam_public_servers.back"), ignored -> onClose()).bounds(x + 2 * (bw + 4), y + 36, w - 2 * (bw + 4), 20).build());
        name.setResponder(value -> {
            draft = value;
            try {
                if (!value.isBlank()) TextPolicy.title(value);
                save.active = true;
                messageKey = "e4steam_public_servers.name.help";
            } catch (IllegalArgumentException failure) {
                save.active = false;
                messageKey = "e4steam_public_servers.name.invalid";
            }
        });
    }
    private void saveName() {
        try { PublicServersRuntime.saveServerName(name.getValue()); onClose(); }
        catch (java.io.IOException failure) { messageKey = "e4steam_public_servers.name.save_failed"; }
        catch (IllegalArgumentException failure) { messageKey = "e4steam_public_servers.name.invalid"; }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int w = Math.min(320, width - 32), x = (width - w) / 2, y = height / 2;
        graphics.centeredText(font, title, width / 2, y - 44, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(font.plainSubstrByWidth(Component.translatable(messageKey).getString(), w)), width / 2, y + 8, 0xFFB7C6D5);
        String preview = draft.isBlank() ? PublicServersRuntime.defaultServerName() : draft;
        graphics.text(font, font.plainSubstrByWidth(preview, w), x, y + 22, 0xFF7DE1C7, false);
    }
}
