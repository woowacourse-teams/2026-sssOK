package com.sssok.application.media;

import java.util.List;

public record DeleteMediaResult(int deletedCount, List<Long> deletedMediaIds,
                                List<Long> notFoundMediaIds) {
}
