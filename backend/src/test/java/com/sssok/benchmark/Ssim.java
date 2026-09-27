package com.sssok.benchmark;

import java.awt.image.BufferedImage;

// 두 이미지의 구조적 유사도(SSIM). 파일 크기만 보면 "작을수록 좋다"로 흘러가므로,
// 같은 해상도에서 화질을 하나의 수로 비교하기 위해 쓴다. 1.0 이 원본과 동일.
//
// 두 가지 창을 모두 계산한다.
//  - GAUSSIAN: Wang et al.(2004) 원 논문의 11x11 가우시안(σ=1.5). 논문과 대부분의 벤치마크가
//    보고하는 값이라 이쪽을 기본으로 쓴다.
//  - UNIFORM_8: ffmpeg 의 ssim 필터와 같은 8x8 균등 창. 이 값을 ffmpeg 과 맞대 보면 휘도 추출과
//    창 계산이 제대로 도는지 검증할 수 있다 (SsimValidation).
//
// 두 창을 다 두는 이유는, 자체 구현한 지표로 포맷을 고르면서 "그 지표가 맞나"를 검증할 길이
// 없으면 곤란해서다. 검증 가능한 쪽으로 기계를 맞춰 두고, 보고는 표준 쪽으로 한다.
final class Ssim {

    // 8비트 이미지의 동적 범위에 대한 안정화 상수. 논문의 K1=0.01, K2=0.03.
    private static final double C1 = Math.pow(0.01 * 255, 2);
    private static final double C2 = Math.pow(0.03 * 255, 2);

    private static final int GAUSSIAN_RADIUS = 5;
    private static final double GAUSSIAN_SIGMA = 1.5;
    private static final int UNIFORM_WINDOW = 8;
    // ffmpeg 은 8x8 창을 4픽셀씩 겹쳐 가며 옮긴다. 겹치지 않게 8씩 뛰면 블록 경계에 걸친
    // 열화를 덜 보게 되어 값이 후해진다.
    private static final int UNIFORM_STRIDE = 4;

    private Ssim() {
    }

    enum Window {
        // 논문 정의. 보고용.
        GAUSSIAN,
        // ffmpeg ssim 필터와 같은 방식. 교차 검증용.
        UNIFORM_8
    }

    static double between(BufferedImage reference, BufferedImage target, Window window) {
        if (reference.getWidth() != target.getWidth()
            || reference.getHeight() != target.getHeight()) {
            throw new IllegalArgumentException("SSIM 은 같은 해상도끼리만 비교한다.");
        }
        double[][] left = luminance(reference);
        double[][] right = luminance(target);
        return window == Window.GAUSSIAN ? gaussian(left, right) : uniform(left, right);
    }

    // 11x11 가우시안 창을 한 픽셀씩 옮겨 가며 계산한 SSIM 의 평균.
    private static double gaussian(double[][] left, double[][] right) {
        double[] kernel = gaussianKernel();
        int height = left.length;
        int width = left[0].length;
        int size = 2 * GAUSSIAN_RADIUS + 1;
        if (height < size || width < size) {
            throw new IllegalArgumentException("이미지가 창보다 작다.");
        }

        // 분리 가능한 커널이라 가로·세로로 나눠 흐린다. 2차원으로 한 번에 돌리면
        // 픽셀마다 121회를 곱하는데, 나누면 22회로 준다.
        double[][] muLeft = blur(left, kernel);
        double[][] muRight = blur(right, kernel);
        double[][] squaredLeft = blur(square(left), kernel);
        double[][] squaredRight = blur(square(right), kernel);
        double[][] crossed = blur(multiply(left, right), kernel);

        double total = 0;
        int counted = 0;
        // 가장자리는 창이 이미지 밖으로 나가 통계가 왜곡되므로 뺀다 (논문·레퍼런스 구현과 같다).
        for (int y = GAUSSIAN_RADIUS; y < height - GAUSSIAN_RADIUS; y++) {
            for (int x = GAUSSIAN_RADIUS; x < width - GAUSSIAN_RADIUS; x++) {
                double meanLeft = muLeft[y][x];
                double meanRight = muRight[y][x];
                double varLeft = squaredLeft[y][x] - meanLeft * meanLeft;
                double varRight = squaredRight[y][x] - meanRight * meanRight;
                double covariance = crossed[y][x] - meanLeft * meanRight;

                total += ((2 * meanLeft * meanRight + C1) * (2 * covariance + C2))
                    / ((meanLeft * meanLeft + meanRight * meanRight + C1)
                    * (varLeft + varRight + C2));
                counted++;
            }
        }
        return total / counted;
    }

    // 8x8 균등 창을 4픽셀씩 겹쳐 옮겨 가며 계산한 평균. ffmpeg 의 ssim 필터와 같은 방식이다.
    private static double uniform(double[][] left, double[][] right) {
        double total = 0;
        int windows = 0;
        for (int y = 0; y + UNIFORM_WINDOW <= left.length; y += UNIFORM_STRIDE) {
            for (int x = 0; x + UNIFORM_WINDOW <= left[0].length; x += UNIFORM_STRIDE) {
                total += windowSsim(left, right, x, y);
                windows++;
            }
        }
        return windows == 0 ? 1.0 : total / windows;
    }

    private static double windowSsim(double[][] left, double[][] right, int originX, int originY) {
        int pixels = UNIFORM_WINDOW * UNIFORM_WINDOW;
        double sumLeft = 0;
        double sumRight = 0;
        for (int y = originY; y < originY + UNIFORM_WINDOW; y++) {
            for (int x = originX; x < originX + UNIFORM_WINDOW; x++) {
                sumLeft += left[y][x];
                sumRight += right[y][x];
            }
        }
        double meanLeft = sumLeft / pixels;
        double meanRight = sumRight / pixels;

        double varLeft = 0;
        double varRight = 0;
        double covariance = 0;
        for (int y = originY; y < originY + UNIFORM_WINDOW; y++) {
            for (int x = originX; x < originX + UNIFORM_WINDOW; x++) {
                double deltaLeft = left[y][x] - meanLeft;
                double deltaRight = right[y][x] - meanRight;
                varLeft += deltaLeft * deltaLeft;
                varRight += deltaRight * deltaRight;
                covariance += deltaLeft * deltaRight;
            }
        }
        // ffmpeg 과 맞추려면 모분산(n)이 아니라 표본분산(n-1)이어야 한다.
        varLeft /= pixels - 1;
        varRight /= pixels - 1;
        covariance /= pixels - 1;

        return ((2 * meanLeft * meanRight + C1) * (2 * covariance + C2))
            / ((meanLeft * meanLeft + meanRight * meanRight + C1) * (varLeft + varRight + C2));
    }

    private static double[] gaussianKernel() {
        int size = 2 * GAUSSIAN_RADIUS + 1;
        double[] kernel = new double[size];
        double sum = 0;
        for (int i = 0; i < size; i++) {
            int offset = i - GAUSSIAN_RADIUS;
            kernel[i] = Math.exp(-(offset * offset) / (2 * GAUSSIAN_SIGMA * GAUSSIAN_SIGMA));
            sum += kernel[i];
        }
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    // 가장자리는 어차피 결과에서 빼므로, 창이 밖으로 나가면 가장 가까운 픽셀로 늘려 잡는다.
    private static double[][] blur(double[][] values, double[] kernel) {
        int height = values.length;
        int width = values[0].length;
        double[][] horizontal = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sampleX = clamp(x + k - GAUSSIAN_RADIUS, width);
                    sum += kernel[k] * values[y][sampleX];
                }
                horizontal[y][x] = sum;
            }
        }
        double[][] blurred = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sampleY = clamp(y + k - GAUSSIAN_RADIUS, height);
                    sum += kernel[k] * horizontal[sampleY][x];
                }
                blurred[y][x] = sum;
            }
        }
        return blurred;
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(limit - 1, value));
    }

    private static double[][] square(double[][] values) {
        return multiply(values, values);
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        double[][] product = new double[left.length][left[0].length];
        for (int y = 0; y < left.length; y++) {
            for (int x = 0; x < left[0].length; x++) {
                product[y][x] = left[y][x] * right[y][x];
            }
        }
        return product;
    }

    // ITU-R BT.601 휘도. 사람 눈이 가장 민감한 성분이라 SSIM 은 보통 휘도만 본다.
    // ffmpeg 도 YUV 로 바꾼 뒤 Y 평면을 쓰므로 같은 계수여야 맞대 볼 수 있다.
    private static double[][] luminance(BufferedImage image) {
        double[][] values = new double[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                values[y][x] = 0.299 * ((rgb >> 16) & 0xFF)
                    + 0.587 * ((rgb >> 8) & 0xFF)
                    + 0.114 * (rgb & 0xFF);
            }
        }
        return values;
    }
}
