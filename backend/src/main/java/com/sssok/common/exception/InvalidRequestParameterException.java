package com.sssok.common.exception;

// 컨트롤러 진입 전(인터셉터 등)에서 경로 변수를 직접 파싱할 때 쓰는 예외.
// 스프링이 변환해 주는 구간의 MethodArgumentTypeMismatchException 과 같은 응답으로 맞춘다.
public class InvalidRequestParameterException extends SssOkException {

    public InvalidRequestParameterException(String parameterName) {
        super(ErrorCode.INVALID_REQUEST_PARAMETER, parameterName);
    }
}