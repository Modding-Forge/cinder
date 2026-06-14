package com.cinder.client.hud;

import com.cinder.config.CinderConfig;
import com.cinder.config.OverlayCorner;
import com.cinder.config.TextContrast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits Cinder-owned HUD text into Mojang's 26.2 GUI extraction path.
 *
 * <p>Threading: called on the client render thread. All config reads come from
 * immutable snapshots.
 *
 * <p>Performance: small, bounded allocation per HUD frame only when the overlay
 * is enabled. It is not part of terrain or item hot paths.
 */
public final class CinderHudOverlay {

    private CinderHudOverlay() {
    }

    /**
     * Adds FPS and coordinate lines when enabled.
     */
    public static void extract(GuiGraphicsExtractor graphics,
                               Minecraft minecraft,
                               CinderConfig cfg) {
        if (!cfg.showFps() && !cfg.showCoords()) {
            return;
        }
        List<String> lines = new ArrayList<>(3);
        if (cfg.showFps()) {
            String fps = minecraft.getFps() + " FPS";
            if (cfg.showFpsExtended() && minecraft.getFps() > 0) {
                fps += " / " + Math.round(1000.0F / minecraft.getFps())
                        + " ms";
            }
            lines.add(fps);
        }
        if (cfg.showCoords()) {
            Player player = minecraft.player;
            if (player != null) {
                lines.add("XYZ " + format(player.getX()) + " "
                        + format(player.getY()) + " " + format(player.getZ()));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, minecraft.font.width(line));
        }
        int totalHeight = lines.size() * 10;
        int x = x(graphics, cfg.overlayCorner(), maxWidth);
        int y = y(graphics, cfg.overlayCorner(), totalHeight);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineX = cfg.overlayCorner() == OverlayCorner.TOP_RIGHT
                    || cfg.overlayCorner() == OverlayCorner.BOTTOM_RIGHT
                    ? x + maxWidth - minecraft.font.width(line)
                    : x;
            if (cfg.textContrast() == TextContrast.BACKDROP) {
                graphics.textWithBackdrop(minecraft.font,
                        Component.literal(line), lineX, y + i * 10,
                        minecraft.font.width(line), 0xFFFFFFFF);
            } else {
                graphics.text(minecraft.font, line, lineX, y + i * 10,
                        0xFFFFFFFF, true);
            }
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int x(GuiGraphicsExtractor graphics,
                         OverlayCorner corner,
                         int width) {
        return corner == OverlayCorner.TOP_RIGHT
                || corner == OverlayCorner.BOTTOM_RIGHT
                ? graphics.guiWidth() - width - 4
                : 4;
    }

    private static int y(GuiGraphicsExtractor graphics,
                         OverlayCorner corner,
                         int height) {
        return corner == OverlayCorner.BOTTOM_LEFT
                || corner == OverlayCorner.BOTTOM_RIGHT
                ? graphics.guiHeight() - height - 4
                : 4;
    }
}
