package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DownloadFileNamesTest {

    @Test
    void zip_파일명은_방ID를_포함한다() {
        assertThat(DownloadFileNames.zipNameOf(1024L)).isEqualTo("sssOK_1024.zip");
    }

    @Test
    void ASCII_파일명은_그대로_두_형식에_다_쓰인다() {
        String disposition = DownloadFileNames.contentDispositionOf("IMG_0421.jpg");

        assertThat(disposition).isEqualTo(
            "attachment; filename=\"IMG_0421.jpg\"; filename*=UTF-8''IMG_0421.jpg");
    }

    @Test
    void 한글_파일명은_ASCII_폴백과_UTF8_인코딩을_함께_담는다() {
        String disposition = DownloadFileNames.contentDispositionOf("사진.jpg");

        assertThat(disposition).isEqualTo(
            "attachment; filename=\"download.jpg\"; filename*=UTF-8''%EC%82%AC%EC%A7%84.jpg");
    }

    @Test
    void 확장자가_없는_비ASCII_파일명은_확장자_없이_폴백한다() {
        String disposition = DownloadFileNames.contentDispositionOf("사진");

        assertThat(disposition).isEqualTo(
            "attachment; filename=\"download\"; filename*=UTF-8''%EC%82%AC%EC%A7%84");
    }

    @Test
    void ASCII_파일명에_큰따옴표가_있으면_작은따옴표로_치환한다() {
        String disposition = DownloadFileNames.contentDispositionOf("my \"vacation\".jpg");

        assertThat(disposition).contains("filename=\"my 'vacation'.jpg\"");
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