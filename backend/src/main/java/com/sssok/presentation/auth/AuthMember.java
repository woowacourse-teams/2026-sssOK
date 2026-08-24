package com.sssok.presentation.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Authorization: Bearer {accessToken} 에서 뽑아낸 memberId를 컨트롤러 파라미터로 주입받는다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthMember {
}
