package com.sssok.domain.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DownloadFileNames {

    private static final String ZIP_NAME_FORMAT = "ShareDrop_%s.zip";

    private DownloadFileNames() {
    }

    public static String zipNameOf(String roomCode) {
        return ZIP_NAME_FORMAT.formatted(roomCode);
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
