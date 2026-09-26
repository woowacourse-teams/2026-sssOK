package com.sssok.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// 포맷 결정이 이 계산 하나에 기대므로, ffmpeg 교차 검증을 돌리지 않는 CI 에서도
// 기본 성질만은 지킨다. 샘플 사진 없이 메모리에서 만든 64x64 로 끝나 몇 밀리초면 된다.
class SsimTest {

    private static final int SIZE = 64;

    @ParameterizedTest
    @EnumSource(Ssim.Window.class)
    @DisplayName("같은 이미지끼리의 SSIM 은 1 이다")
    void identicalImagesScoreOne(Ssim.Window window) {
        BufferedImage image = pattern();

        assertThat(Ssim.between(image, image, window)).isCloseTo(1.0, within(1e-9));
    }

    @ParameterizedTest
    @EnumSource(Ssim.Window.class)
    @DisplayName("훼손이 커질수록 SSIM 이 낮아진다")
    void moreDamageScoresLower(Ssim.Window window) {
        BufferedImage reference = pattern();

        double slight = Ssim.between(reference, noisy(reference, 4), window);
        double moderate = Ssim.between(reference, noisy(reference, 16), window);
        double severe = Ssim.between(reference, noisy(reference, 64), window);

        assertThat(slight).isLessThan(1.0);
        assertThat(moderate).isLessThan(slight);
        assertThat(severe).isLessThan(moderate);
    }

    @Test
    @DisplayName("해상도가 다르면 비교하지 않는다")
    void rejectsDifferentSizes() {
        BufferedImage reference = pattern();
        BufferedImage smaller = new BufferedImage(SIZE / 2, SIZE, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> Ssim.between(reference, smaller, Ssim.Window.GAUSSIAN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미지가 가우시안 창보다 작으면 비교하지 않는다")
    void rejectsImageSmallerThanWindow() {
        BufferedImage tiny = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> Ssim.between(tiny, tiny, Ssim.Window.GAUSSIAN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static BufferedImage pattern() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int base = (x * 4 + y * 2) % 256;
                int checker = ((x / 8 + y / 8) % 2 == 0) ? 40 : 0;
                image.setRGB(x, y, gray(base + checker));
            }
        }
        return image;
    }

    // 같은 씨앗으로 돌려 회차마다 값이 흔들리지 않게 한다.
    private static BufferedImage noisy(BufferedImage source, int amplitude) {
        Random random = new Random(42);
        BufferedImage damaged = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int noise = random.nextInt(2 * amplitude + 1) - amplitude;
                damaged.setRGB(x, y, gray((source.getRGB(x, y) & 0xFF) + noise));
            }
        }
        return damaged;
    }

    private static int gray(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        return (clamped << 16) | (clamped << 8) | clamped;
    }
}
