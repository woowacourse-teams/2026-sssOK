package com.sssok.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExifOrientationTest {

    private static final Map<String, Color> COLORS = Map.of(
        "RED", Color.RED,
        "GREEN", Color.GREEN,
        "BLUE", Color.BLUE,
        "YELLOW", Color.YELLOW);

    @ParameterizedTest
    @CsvSource({
        "1, 4, 2, RED, GREEN, BLUE, YELLOW",
        "2, 4, 2, GREEN, RED, YELLOW, BLUE",
        "3, 4, 2, YELLOW, BLUE, GREEN, RED",
        "4, 4, 2, BLUE, YELLOW, RED, GREEN",
        "5, 2, 4, RED, BLUE, GREEN, YELLOW",
        "6, 2, 4, BLUE, RED, YELLOW, GREEN",
        "7, 2, 4, YELLOW, GREEN, BLUE, RED",
        "8, 2, 4, GREEN, YELLOW, RED, BLUE"
    })
    void 각_방향이_픽셀과_크기를_변환한다(
        int value, int expectedWidth, int expectedHeight,
        String topLeft, String topRight, String bottomLeft, String bottomRight) {
        ExifOrientation orientation = ExifOrientation.from(value);
        BufferedImage source = quadrantImage();
        BufferedImage result = orientation.applyTo(source);

        assertThat(result.getWidth()).isEqualTo(expectedWidth);
        assertThat(result.getHeight()).isEqualTo(expectedHeight);
        assertThat(orientation.displayWidthOf(source)).isEqualTo(expectedWidth);
        assertThat(orientation.displayHeightOf(source)).isEqualTo(expectedHeight);
        assertThat(colorAt(result, 0, 0)).isEqualTo(color(topLeft));
        assertThat(colorAt(result, expectedWidth - 1, 0)).isEqualTo(color(topRight));
        assertThat(colorAt(result, 0, expectedHeight - 1)).isEqualTo(color(bottomLeft));
        assertThat(colorAt(result, expectedWidth - 1, expectedHeight - 1))
            .isEqualTo(color(bottomRight));
    }

    @Test
    void 값이_없거나_범위를_벗어나면_원본을_그대로_사용한다() {
        BufferedImage source = quadrantImage();

        assertThat(ExifOrientation.from(null).applyTo(source)).isSameAs(source);
        assertThat(ExifOrientation.from(0).applyTo(source)).isSameAs(source);
        assertThat(ExifOrientation.from(9).applyTo(source)).isSameAs(source);
    }

    private BufferedImage quadrantImage() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 2, 1);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(2, 0, 2, 1);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 1, 2, 1);
        graphics.setColor(Color.YELLOW);
        graphics.fillRect(2, 1, 2, 1);
        graphics.dispose();
        return image;
    }

    private Color colorAt(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    private Color color(String name) {
        return COLORS.get(name);
    }
}
