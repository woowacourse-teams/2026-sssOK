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
                .filter(path -> !inheritsSssOkException(path))
                .map(path -> path.getFileName().toString())
                .toList();

            assertThat(notInherited).isEmpty();
        }
    }

    private boolean inheritsSssOkException(Path path) {
        String className = "com.sssok." + SOURCE_ROOT.relativize(path).toString()
            .replace(java.io.File.separatorChar, '.').replaceAll("\\.java$", "");
        try {
            Class<?> exceptionType = Class.forName(className, false, getClass().getClassLoader());
            return SssOkException.class.isAssignableFrom(exceptionType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(className + " 을 읽을 수 없습니다", e);
        }
    }
}
