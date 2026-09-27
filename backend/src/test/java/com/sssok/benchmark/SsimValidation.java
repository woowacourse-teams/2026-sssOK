package com.sssok.benchmark;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

// 자체 구현한 SSIM 이 맞는 값을 내는지 ffmpeg 의 ssim 필터와 맞대 본다.
//
// 이 검증이 없으면 "우리가 만든 지표로 우리가 포맷을 골랐다"가 되어, 결론을 뒷받침할 길이 없다.
// ffmpeg 의 필터는 8x8 균등 창이므로 Ssim.Window.UNIFORM_8 과 맞춰 비교한다. 이 값이 맞으면
// 휘도 추출과 창 통계가 제대로 도는 것이고, 같은 기계 위에 얹힌 가우시안 창도 믿을 수 있다.
//
//   ./gradlew ssimValidation -Psamples=<무손실 PNG 디렉터리> -Pout=결과.csv
public final class SsimValidation {

    // 열화 정도를 넓게 훑어야 한쪽으로 치우친 우연한 일치를 걸러낼 수 있다.
    private static final float[] QUALITIES = {0.30f, 0.50f, 0.70f, 0.85f, 0.95f};

    // 같은 두 장을 같은 방식(휘도 8x8)으로 재는 것이라 원래는 딱 맞아야 한다. 남는 차이는
    // 회색조 변환에서 정수로 반올림하는 지점 정도이고, 그보다 크게 벌어지면 계산이 틀린 것이다.
    private static final double TOLERANCE = 0.005;

    private SsimValidation() {
    }

    public static void main(String[] args) throws IOException {
        Path sampleDir = Path.of(System.getProperty("samples"));
        Path output = Path.of(System.getProperty("out", "ssim-validation.csv"));

        List<Path> samples;
        try (var stream = Files.list(sampleDir)) {
            samples = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                    .endsWith(".png"))
                .sorted(Comparator.comparing(Path::getFileName))
                .toList();
        }

        List<String> rows = new ArrayList<>();
        rows.add("sample,quality,ours_uniform8,ffmpeg_luma,absoluteDifference,ours_gaussian");
        double worst = 0;
        int compared = 0;

        for (Path sample : samples) {
            BufferedImage reference = DerivativeCodec.toRgb(
                ImageIO.read(sample.toFile()));
            for (float quality : QUALITIES) {
                // 손상 이미지를 JPEG 으로 만든다. 무엇으로 망가뜨리든 상관없고, 두 구현이 같은
                // 두 장을 보기만 하면 된다.
                byte[] encoded = DerivativeCodec.encodeJpeg(reference, quality);
                BufferedImage decoded = DerivativeCodec.decode(encoded);

                double ours = Ssim.between(reference, decoded, Ssim.Window.UNIFORM_8);
                double theirs = ffmpegSsim(reference, decoded);
                double gaussian = Ssim.between(reference, decoded, Ssim.Window.GAUSSIAN);
                double difference = Math.abs(ours - theirs);
                worst = Math.max(worst, difference);
                compared++;

                rows.add(String.format(Locale.ROOT, "%s,%.2f,%.5f,%.5f,%.5f,%.5f",
                    sample.getFileName(), quality, ours, theirs, difference, gaussian));
            }
            System.out.printf("  %s 검증 완료%n", sample.getFileName());
        }

        Files.writeString(output, String.join("\n", rows) + "\n");
        System.out.printf("%n비교 %d쌍, 최대 오차 %.5f (허용 %.5f) → %s%n",
            compared, worst, TOLERANCE, worst <= TOLERANCE ? "합격" : "불합격");
        System.out.println("결과: " + output.toAbsolutePath());
        if (worst > TOLERANCE) {
            throw new IllegalStateException("자체 SSIM 이 ffmpeg 과 어긋난다. 측정을 믿을 수 없다.");
        }
    }

    // 두 장을 PNG 임시 파일로 넘긴다. 여기서는 속도가 아니라 값이 맞는지만 본다.
    //
    // format=gray 로 먼저 눕히는 것이 핵심이다. RGB PNG 를 그냥 넣으면 ffmpeg 이 R·G·B 세
    // 평면으로 SSIM 을 내고, 그 평균("All")은 휘도만 보는 우리 값과 맞댈 대상이 아니다.
    // 회색조로 바꾸면 평면이 하나뿐이라 그 값이 곧 휘도 SSIM 이다.
    private static double ffmpegSsim(BufferedImage reference, BufferedImage distorted)
        throws IOException {
        Path referenceFile = Files.createTempFile("ssim-ref-", ".png");
        Path distortedFile = Files.createTempFile("ssim-dist-", ".png");
        try {
            ImageIO.write(reference, "png", referenceFile.toFile());
            ImageIO.write(distorted, "png", distortedFile.toFile());

            Process process = new ProcessBuilder("ffmpeg", "-hide_banner",
                "-i", distortedFile.toString(), "-i", referenceFile.toString(),
                "-lavfi", "[0:v]format=gray[a];[1:v]format=gray[b];[a][b]ssim",
                "-f", "null", "-")
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes());
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return parseLuma(output);
        } finally {
            Files.deleteIfExists(referenceFile);
            Files.deleteIfExists(distortedFile);
        }
    }

    private static double parseLuma(String output) throws IOException {
        int marker = output.lastIndexOf("Y:");
        if (marker < 0) {
            throw new IOException("ffmpeg 이 SSIM 을 내지 않았다: " + output);
        }
        String tail = output.substring(marker + "Y:".length()).trim();
        return Double.parseDouble(tail.split("\\s+")[0]);
    }

    private static BufferedImage read(byte[] content) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(content));
    }
}
