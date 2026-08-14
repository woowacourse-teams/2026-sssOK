package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DownloadFileNamesTest {

    @Test
    void zip_파일명은_방코드를_포함한다() {
        assertThat(DownloadFileNames.zipNameOf("A3F9K2M7")).isEqualTo("ShareDrop_A3F9K2M7.zip");
    }

    @Test
    void 겹치지_않으면_원본_파일명을_그대로_유지한다() {
        List<String> names = DownloadFileNames.deduplicate(List.of("cat.jpg", "dog.png"));

        assertThat(names).containsExactly("cat.jpg", "dog.png");
    }

    @Test
    void 이름이_겹치면_번호를_붙인다() {
        List<String> names =
            DownloadFileNames.deduplicate(List.of("cat.jpg", "cat.jpg", "cat.jpg"));

        assertThat(names).containsExactly("cat.jpg", "cat (1).jpg", "cat (2).jpg");
    }

    @Test
    void 확장자가_없어도_번호를_붙일_수_있다() {
        List<String> names = DownloadFileNames.deduplicate(List.of("사진", "사진"));

        assertThat(names).containsExactly("사진", "사진 (1)");
    }

    @Test
    void 이름에_점이_여러_개면_마지막_확장자_앞에_번호가_붙는다() {
        List<String> names =
            DownloadFileNames.deduplicate(List.of("2026.여름.png", "2026.여름.png"));

        assertThat(names).containsExactly("2026.여름.png", "2026.여름 (1).png");
    }

    @Test
    void 이미_번호가_붙은_이름이_섞여_있어도_겹치지_않는다() {
        List<String> names =
            DownloadFileNames.deduplicate(List.of("cat.jpg", "cat (1).jpg", "cat.jpg"));

        assertThat(names).containsExactly("cat.jpg", "cat (1).jpg", "cat (2).jpg");
    }

    @Test
    void 서로_다른_이름은_각자_번호를_센다() {
        List<String> names =
            DownloadFileNames.deduplicate(List.of("cat.jpg", "dog.jpg", "cat.jpg", "dog.jpg"));

        assertThat(names).containsExactly("cat.jpg", "dog.jpg", "cat (1).jpg", "dog (1).jpg");
    }
}