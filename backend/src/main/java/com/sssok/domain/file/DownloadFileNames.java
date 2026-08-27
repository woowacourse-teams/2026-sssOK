package com.sssok.domain.file;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DownloadFileNames {

    private static final String ZIP_NAME_FORMAT = "sssOK_%d.zip";
    private static final String CONTENT_DISPOSITION_FORMAT = "attachment; filename=\"%s\"; filename*=UTF-8''%s";

    private DownloadFileNames() {
    }

    public static String zipNameOf(Long roomId) {
        return ZIP_NAME_FORMAT.formatted(roomId);
    }

    // ASCII 폴백(filename)과 RFC 5987 UTF-8 인코딩(filename*)을 함께 채운다 — 한쪽만 있으면
    // 한글 등 비-ASCII 파일명이 브라우저별로 깨진다. 폴백이 비-ASCII면 확장자만 살려 대체한다.
    public static String contentDispositionOf(String fileName) {
        String asciiFallback = isAscii(fileName) ? sanitizeQuotes(fileName) : fallbackNameOf(fileName);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return CONTENT_DISPOSITION_FORMAT.formatted(asciiFallback, encoded);
    }

    private static boolean isAscii(String fileName) {
        return fileName.chars().allMatch(c -> c < 128);
    }

    private static String sanitizeQuotes(String fileName) {
        return fileName.replace("\"", "'");
    }

    private static String fallbackNameOf(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex >= 0 ? fileName.substring(extensionIndex) : "";
        return "download" + extension;
    }

    public static List<String> deduplicate(List<String> fileNames) {
        Map<String, Integer> usedCounts = new HashMap<>();
        List<String> result = new ArrayList<>(fileNames.size());

        for (String fileName : fileNames) {
            result.add(nextAvailableName(fileName, usedCounts));
        }
        return result;
    }

    private static String nextAvailableName(String fileName, Map<String, Integer> usedCounts) {
        if (!usedCounts.containsKey(fileName)) {
            usedCounts.put(fileName, 0);
            return fileName;
        }

        int sequence = usedCounts.get(fileName);
        String candidate;
        do {
            sequence++;
            candidate = numbered(fileName, sequence);
        } while (usedCounts.containsKey(candidate));

        usedCounts.put(fileName, sequence);
        usedCounts.put(candidate, 0);
        return candidate;
    }

    private static String numbered(String fileName, int sequence) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return "%s (%d)".formatted(fileName, sequence);
        }
        return "%s (%d)%s".formatted(
                fileName.substring(0, extensionIndex), sequence, fileName.substring(extensionIndex));
    }
}
