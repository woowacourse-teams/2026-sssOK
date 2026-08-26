package com.sssok.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ExceptionHierarchyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/sssok");

    @Test
    void 모든_예외는_SssOkException_을_상속한다() throws IOException {
        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            List<String> notInherited = sources
                .filter(path -> path.getFileName().toString().endsWith("Exception.java"))
                .filter(path -> !path.getFileName().toString().equals("SssOkException.java"))
                .filter(path -> !contains(path, "extends SssOkException"))
                .map(path -> path.getFileName().toString())
                .toList();

            assertThat(notInherited).isEmpty();
        }
    }

    private boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException e) {
            throw new IllegalStateException(path + " 을 읽을 수 없습니다", e);
        }
    }
}
