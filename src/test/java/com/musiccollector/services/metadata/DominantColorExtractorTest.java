package com.musiccollector.services.metadata;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DominantColorExtractorTest {

    private final DominantColorExtractor extractor = new DominantColorExtractor();

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 120, 120);
        g.dispose();
        return image;
    }

    @Test
    void aDarkSleeveAsksForDarkChrome() throws IOException {
        Optional<CoverPalette> palette = extractor.extract(png(solid(new Color(0x2e, 0x2b, 0x24))));

        assertThat(palette).hasValueSatisfying(p -> {
            assertThat(p.dominantColor()).isEqualTo("#2e2b24");
            assertThat(p.luminance()).isLessThan(CoverPalette.DARK_CHROME_THRESHOLD);
            assertThat(p.dark()).isTrue();
        });
    }

    @Test
    void aPaleSleeveAsksForLightChrome() throws IOException {
        Optional<CoverPalette> palette = extractor.extract(png(solid(new Color(0xec, 0xe8, 0xe0))));

        assertThat(palette).hasValueSatisfying(p -> {
            assertThat(p.dominantColor()).isEqualTo("#ece8e0");
            assertThat(p.dark()).isFalse();
        });
    }

    @Test
    void picksTheDominantToneWhenTwoColoursCompete() throws IOException {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x14, 0x13, 0x11));
        g.fillRect(0, 0, 120, 120);
        // A quarter of the sleeve in a bright accent — colourful, but not dominant.
        g.setColor(new Color(0xd0, 0x8a, 0x5f));
        g.fillRect(0, 0, 60, 60);
        g.dispose();

        Optional<CoverPalette> palette = extractor.extract(png(image));

        assertThat(palette).hasValueSatisfying(p -> {
            assertThat(p.dominantColor()).isEqualTo("#141311");
            // The accent is drawn from the artwork, not from the dominant tone.
            assertThat(p.accentColor()).isEqualTo("#d08a5f");
            assertThat(p.dark()).isTrue();
        });
    }

    @Test
    void fallsBackToTheDominantToneWhenNothingIsColourfulEnough() throws IOException {
        Optional<CoverPalette> palette = extractor.extract(png(solid(new Color(0x80, 0x80, 0x80))));

        assertThat(palette).hasValueSatisfying(p -> assertThat(p.accentColor()).isEqualTo(p.dominantColor()));
    }

    @Test
    void returnsEmptyForBytesThatAreNotAnImage() {
        assertThat(extractor.extract("not an image".getBytes())).isEmpty();
    }

    @Test
    void whiteAndBlackSitAtTheEndsOfTheLuminanceScale() {
        assertThat(DominantColorExtractor.relativeLuminance(255, 255, 255)).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(DominantColorExtractor.relativeLuminance(0, 0, 0)).isEqualTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
