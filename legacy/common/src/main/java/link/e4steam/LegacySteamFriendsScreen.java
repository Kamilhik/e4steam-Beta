package link.e4steam;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import link.e4steam.steam.SteamSocialSnapshot;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** PoseStack renderer used by the Java 16 Minecraft 1.17-1.18.2 build. */
public final class LegacySteamFriendsScreen extends SteamFriendsScreenBase {
    private final Map<Long, AvatarTexture> avatars = new HashMap<>();

    public LegacySteamFriendsScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        LegacyPainter painter = new LegacyPainter(pose);
        renderPanel(painter, mouseX, mouseY);
        super.render(pose, mouseX, mouseY, partialTick);
        renderButtonAvatars(painter);
    }

    @Override
    protected void releaseRenderResources() {
        if (minecraft != null) {
            for (AvatarTexture texture : avatars.values()) {
                minecraft.getTextureManager().release(texture.location);
            }
        }
        avatars.clear();
    }

    private final class LegacyPainter implements Painter {
        private final PoseStack pose;

        private LegacyPainter(PoseStack pose) {
            this.pose = pose;
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            GuiComponent.fill(pose, left, top, right, bottom, color);
        }

        @Override
        public void text(Component text, int x, int y, int color) {
            font.draw(pose, text, x, y, color);
        }

        @Override
        public void centered(Component text, int centerX, int y, int color) {
            font.draw(pose, text, centerX - font.width(text) / 2.0f, y, color);
        }

        @Override
        public void avatar(long steamId, SteamSocialSnapshot.Avatar avatar, int x, int y, int size) {
            GuiComponent.fill(pose, x - 1, y - 1, x + size + 1, y + size + 1, 0xff111111);
            AvatarTexture texture = LegacySteamFriendsScreen.this.texture(steamId, avatar);
            if (texture == null) {
                fallbackFace(pose, x, y, size, steamId);
                return;
            }
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShaderTexture(0, texture.location);
            GuiComponent.blit(pose, x, y, 0.0f, 0.0f, size, size, texture.width, texture.height);
        }

        @Override
        public void texture(ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                            int textureWidth, int textureHeight) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShaderTexture(0, texture);
            GuiComponent.blit(pose, x, y, u, v, width, height, textureWidth, textureHeight);
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

    private static void fallbackFace(PoseStack pose, int x, int y, int size, long seed) {
        int skin = (seed & 1L) == 0L ? 0xffb8794f : 0xff9d633f;
        int hair = (seed & 2L) == 0L ? 0xff4b2a1b : 0xff2f2018;
        GuiComponent.fill(pose, x, y, x + size, y + size, skin);
        GuiComponent.fill(pose, x, y, x + size, y + Math.max(4, size / 4), hair);
        int eyeY = y + size / 2;
        int eye = Math.max(2, size / 8);
        GuiComponent.fill(pose, x + size / 4, eyeY, x + size / 4 + eye, eyeY + eye, 0xffeeeeee);
        GuiComponent.fill(pose, x + size * 3 / 4 - eye, eyeY, x + size * 3 / 4, eyeY + eye, 0xffeeeeee);
    }

    private static final class AvatarTexture {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        private AvatarTexture(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }
}
