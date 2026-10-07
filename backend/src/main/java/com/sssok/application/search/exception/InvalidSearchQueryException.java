package com.sssok.application.search.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidSearchQueryException extends SssOkException {
    public InvalidSearchQueryException() {
        super(ErrorCode.INVALID_SEARCH_QUERY);
    }
}
