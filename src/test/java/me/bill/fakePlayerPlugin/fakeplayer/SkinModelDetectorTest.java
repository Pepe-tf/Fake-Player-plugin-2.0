package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import me.bill.fakePlayerPlugin.fakeplayer.SkinModelDetector.SkinModel;

class SkinModelDetectorTest {

    private static BufferedImage skinCanvas(int height, boolean paintSlimColumn) {
        BufferedImage image = new BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB);
        // Fill the whole canvas opaque, then optionally clear the 4th arm-pixel column that only
        // classic (4px arm) skins paint.
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFF888888);
            }
        }
        if (!paintSlimColumn) {
            for (int x = 54; x <= 55; x++) {
                for (int y = 16; y <= 31; y++) {
                    image.setRGB(x, y, 0x00000000);
                }
            }
        }
        return image;
    }

    @Test
    void opaqueArmColumnIsClassic() {
        assertEquals(SkinModel.CLASSIC, SkinModelDetector.detectFromImage(skinCanvas(64, true)));
    }

    @Test
    void transparentArmColumnIsSlim() {
        assertEquals(SkinModel.SLIM, SkinModelDetector.detectFromImage(skinCanvas(64, false)));
    }

    @Test
    void legacy32PixelSkinsAreAlwaysClassic() {
        assertEquals(SkinModel.CLASSIC, SkinModelDetector.detectFromImage(skinCanvas(32, false)));
    }

    private static String textureValue(String skinJson) {
        return Base64.getEncoder().encodeToString(skinJson.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void textureMetadataModelSlimIsDetected() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://x/y.png\",\"metadata\":{\"model\":\"slim\"}}}}";
        assertEquals(SkinModel.SLIM, SkinModelDetector.detectFromTextureValue(textureValue(json)));
    }

    @Test
    void textureWithoutMetadataIsClassic() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://x/y.png\"}}}";
        assertEquals(SkinModel.CLASSIC, SkinModelDetector.detectFromTextureValue(textureValue(json)));
    }

    @Test
    void malformedInputIsUnknown() {
        assertEquals(SkinModel.UNKNOWN, SkinModelDetector.detectFromTextureValue("not-base64!!"));
        assertEquals(SkinModel.UNKNOWN, SkinModelDetector.detectFromPng(new byte[] {1, 2, 3}));
        assertEquals(SkinModel.UNKNOWN, SkinModelDetector.detectFromTextureValue(null));
    }
}
