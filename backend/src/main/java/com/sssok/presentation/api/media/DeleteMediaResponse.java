package com.sssok.presentation.api.media;

import com.sssok.application.media.DeleteMediaResult;
import java.util.List;

public record DeleteMediaResponse(int deletedCount, List<Long> deletedMediaIds,
                                  List<Long> notFoundMediaIds) {

    public static DeleteMediaResponse from(DeleteMediaResult result) {
        return new DeleteMediaResponse(result.deletedCount(), result.deletedMediaIds(),
            result.notFoundMediaIds());
    }
}
