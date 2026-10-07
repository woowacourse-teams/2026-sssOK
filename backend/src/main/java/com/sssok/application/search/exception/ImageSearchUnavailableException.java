package com.sssok.application.search.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class ImageSearchUnavailableException extends SssOkException {
    public ImageSearchUnavailableException() {
        super(ErrorCode.IMAGE_SEARCH_UNAVAILABLE);
    }
}
