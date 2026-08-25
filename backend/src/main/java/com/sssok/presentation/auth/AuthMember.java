package com.sssok.presentation.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//memberId를 컨트롤러 파라미터로 주입받는다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthMember {

    boolean required() default true;

    // SSE(EventSource)처럼 커스텀 헤더를 붙일 수 없는 클라이언트를 위해
    // token 쿼리 파라미터로도 인증을 허용할지 여부. 기본은 false — 필요한 엔드포인트에서만 켠다.
    boolean allowQueryToken() default false;
}
