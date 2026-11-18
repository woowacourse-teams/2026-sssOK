package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

// 압축 한 건의 시간을 구간별로 나눠 모은다(#666). 장당 시간 중 무엇이 큰지 가려야 줄일 곳을 정할 수 있다.
// 원본 본문 읽기와 파트 업로드는 zip 쓰기 도중에 일어나므로 스트림을 감싸 따로 재고,
// 압축(DEFLATE)은 zip 쓰기 구간에서 그 둘을 뺀 나머지로 구한다.
// 한 워커 스레드 안에서만 쓰므로 동기화하지 않는다.
class CompressionStageClock {

    enum Stage {
        // 원본 GET 요청부터 응답 헤더까지(첫 바이트 대기)
        OPEN,
        // 원본 본문 받기
        READ,
        // zip 압축·쓰기(CPU)
        DEFLATE,
        // 멀티파트 시작·파트 업로드·완료
        UPLOAD,
        // 진행률 커밋
        PROGRESS
    }

    private long openNanos;
    private long readNanos;
    private long uploadOpenNanos;
    private long uploadWriteNanos;
    private long progressNanos;
    private long writeNanos;
    private int files;

    InputStream openSource(Supplier<InputStream> open) {
        long start = System.nanoTime();
        try {
            return new TimedInputStream(open.get());
        } finally {
            openNanos += System.nanoTime() - start;
        }
    }

    AbortableOutputStream openUpload(Supplier<AbortableOutputStream> open) {
        long start = System.nanoTime();
        try {
            return new TimedOutputStream(open.get());
        } finally {
            uploadOpenNanos += System.nanoTime() - start;
        }
    }

    // zip 쓰기 구간. 안에서 일어난 본문 읽기·파트 업로드는 감싼 스트림이 따로 센다.
    void write(IoAction action) throws IOException {
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            writeNanos += System.nanoTime() - start;
        }
    }

    void progress(Runnable commit) {
        long start = System.nanoTime();
        try {
            commit.run();
        } finally {
            progressNanos += System.nanoTime() - start;
        }
    }

    void fileDone() {
        files++;
    }

    int files() {
        return files;
    }

    Map<Stage, Duration> elapsed() {
        Map<Stage, Duration> result = new EnumMap<>(Stage.class);
        result.put(Stage.OPEN, Duration.ofNanos(openNanos));
        result.put(Stage.READ, Duration.ofNanos(readNanos));
        result.put(Stage.DEFLATE, Duration.ofNanos(Math.max(0, writeNanos - readNanos - uploadWriteNanos)));
        result.put(Stage.UPLOAD, Duration.ofNanos(uploadOpenNanos + uploadWriteNanos));
        result.put(Stage.PROGRESS, Duration.ofNanos(progressNanos));
        return result;
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws IOException;
    }

    private final class TimedInputStream extends FilterInputStream {

        private TimedInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            long start = System.nanoTime();
            try {
                return super.read();
            } finally {
                readNanos += System.nanoTime() - start;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            long start = System.nanoTime();
            try {
                return super.read(b, off, len);
            } finally {
                readNanos += System.nanoTime() - start;
            }
        }
    }

    private final class TimedOutputStream extends AbortableOutputStream {

        private final AbortableOutputStream out;

        private TimedOutputStream(AbortableOutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            long start = System.nanoTime();
            try {
                out.write(b);
            } finally {
                uploadWriteNanos += System.nanoTime() - start;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            long start = System.nanoTime();
            try {
                out.write(b, off, len);
            } finally {
                uploadWriteNanos += System.nanoTime() - start;
            }
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            long start = System.nanoTime();
            try {
                out.close();
            } finally {
                uploadWriteNanos += System.nanoTime() - start;
            }
        }

        @Override
        public void abort() {
            out.abort();
        }
    }
}
