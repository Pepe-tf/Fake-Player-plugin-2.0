package me.bill.fakePlayerPlugin.fakeplayer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Detects whether a skin uses the slim (Alex, 3px arms) or classic (Steve, 4px arms) player model.
 *
 * <p>Two independent strategies, used in this order:
 *
 * <ol>
 *   <li><b>Texture metadata</b> - a signed texture property's base64 value is a JSON document; slim
 *       skins carry {@code textures.SKIN.metadata.model = "slim"}, classic skins carry no metadata.
 *       Authoritative when a texture value is available, since this is the exact field the client
 *       itself renders from.
 *   <li><b>Pixel analysis</b> - for a raw skin PNG (no texture property yet, e.g. a pool skin about
 *       to be uploaded for signing): on modern 64x64 skins the classic model's right arm occupies
 *       x=50–53 while the extra column x=54–55 (rows 20–31) is painted only for 4px-arm skins; slim
 *       skins leave it fully transparent. Legacy 64x32 skins predate slim entirely → always classic.
 * </ol>
 */
public final class SkinModelDetector {

    public enum SkinModel {
        CLASSIC,
        SLIM,
        UNKNOWN;

        /** MineSkin/Mojang variant string ("classic"/"slim"), or "auto" when unknown. */
        public String variant() {
            return this == UNKNOWN ? "auto" : name().toLowerCase(Locale.ROOT);
        }
    }

    private SkinModelDetector() {}

    /** Detects the model from a signed texture property's base64 {@code value} JSON. */
    public static SkinModel detectFromTextureValue(String base64Value) {
        if (base64Value == null || base64Value.isBlank()) return SkinModel.UNKNOWN;
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return SkinModel.UNKNOWN;
            JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
            if (textures == null) return SkinModel.UNKNOWN;
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return SkinModel.UNKNOWN;
            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata != null
                    && metadata.has("model")
                    && "slim".equalsIgnoreCase(metadata.get("model").getAsString())) {
                return SkinModel.SLIM;
            }
            return SkinModel.CLASSIC;
        } catch (Exception e) {
            return SkinModel.UNKNOWN;
        }
    }

    /** Detects the model from raw skin PNG bytes via arm-pixel transparency. */
    public static SkinModel detectFromPng(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) return SkinModel.UNKNOWN;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) return SkinModel.UNKNOWN;
            return detectFromImage(image);
        } catch (Exception e) {
            return SkinModel.UNKNOWN;
        }
    }

    /** Detects the model from a decoded skin image via arm-pixel transparency. */
    public static SkinModel detectFromImage(BufferedImage image) {
        if (image == null || image.getWidth() < 64) return SkinModel.UNKNOWN;
        if (image.getHeight() == 32) return SkinModel.CLASSIC; // legacy format predates slim
        if (image.getHeight() < 64) return SkinModel.UNKNOWN;

        // The 4th arm-pixel column of the right arm's front face. Sample several rows so a single
        // stray transparent pixel in a classic skin can't flip the result.
        int opaque = 0;
        int transparent = 0;
        int[] xs = {54, 55};
        int[] ys = {20, 24, 28, 31};
        for (int x : xs) {
            for (int y : ys) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha == 0) transparent++;
                else opaque++;
            }
        }
        return transparent > opaque ? SkinModel.SLIM : SkinModel.CLASSIC;
    }
}
