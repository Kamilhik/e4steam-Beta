package link.e4steam;

import com.mojang.blaze3d.platform.NativeImage;
import link.e4steam.steam.SteamSocialSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** GuiGraphics renderer used by Minecraft 1.19.4 and newer. */
public final class ModernSteamFriendsScreen extends SteamFriendsScreenBase {
    private final Map<Long, AvatarTexture> avatars = new HashMap<>();

    public ModernSteamFriendsScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ModernPainter painter = new ModernPainter(graphics);
        renderPanel(painter, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderButtonAvatars(painter);
    }

    @Override
    protected void releaseRenderResources() {
        if (minecraft == null) {
            avatars.clear();
            return;
        }
        for (AvatarTexture texture : avatars.values()) {
            minecraft.getTextureManager().release(texture.location);
        }
        avatars.clear();
    }

    private final class ModernPainter implements Painter {
        private final GuiGraphics graphics;

        private ModernPainter(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            graphics.fill(left, top, right, bottom, color);
        }

        @Override
        public void text(Component text, int x, int y, int color) {
            graphics.drawString(font, text, x, y, color, false);
        }

        @Override
        public void centered(Component text, int centerX, int y, int color) {
            graphics.drawCenteredString(font, text, centerX, y, color);
        }

        @Override
        public void avatar(long steamId, SteamSocialSnapshot.Avatar avatar, int x, int y, int size) {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xff111111);
            AvatarTexture texture = ModernSteamFriendsScreen.this.texture(steamId, avatar);
            if (texture == null) {
                fallbackFace(graphics, x, y, size, steamId);
                return;
            }
            graphics.blit(
                    texture.location,
                    x,
                    y,
                    0.0f,
                    0.0f,
                    size,
                    size,
                    texture.width,
                    texture.height
            );
        }

        @Override
        public void texture(ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                            int textureWidth, int textureHeight) {
            graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

    private AvatarTexture texture(long steamId, SteamSocialSnapshot.Avatar avatar) {
        if (!avatar.available() || minecraft == null) {
            return null;
        }
        AvatarTexture existing = avatars.get(steamId);
        if (existing != null && existing.width == avatar.width() && existing.height == avatar.height()) {
            return existing;
        }
        if (existing != null) {
            minecraft.getTextureManager().release(existing.location);
        }
        byte[] rgba = avatar.rgba();
        NativeImage image = new NativeImage(avatar.width(), avatar.height(), false);
        int offset = 0;
        for (int y = 0; y < avatar.height(); y++) {
            for (int x = 0; x < avatar.width(); x++) {
                int red = rgba[offset++] & 0xff;
                int green = rgba[offset++] & 0xff;
                int blue = rgba[offset++] & 0xff;
                int alpha = rgba[offset++] & 0xff;
                image.setPixelRGBA(x, y, alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
        DynamicTexture dynamic = new DynamicTexture(image);
        ResourceLocation location = minecraft.getTextureManager().register(
                "e4steam_steam_avatar_" + Long.toUnsignedString(steamId),
                dynamic
        );
        AvatarTexture created = new AvatarTexture(location, avatar.width(), avatar.height());
        avatars.put(steamId, created);
        return created;
    }

    private static void fallbackFace(GuiGraphics graphics, int x, int y, int size, long seed) {
        int skin = (seed & 1L) == 0L ? 0xffb8794f : 0xff9d633f;
        int hair = (seed & 2L) == 0L ? 0xff4b2a1b : 0xff2f2018;
        graphics.fill(x, y, x + size, y + size, skin);
        graphics.fill(x, y, x + size, y + Math.max(4, size / 4), hair);
        int eyeY = y + size / 2;
        int eye = Math.max(2, size / 8);
        graphics.fill(x + size / 4, eyeY, x + size / 4 + eye, eyeY + eye, 0xffeeeeee);
        graphics.fill(x + size * 3 / 4 - eye, eyeY, x + size * 3 / 4, eyeY + eye, 0xffeeeeee);
    }

    private record AvatarTexture(ResourceLocation location, int width, int height) {
    }
}
