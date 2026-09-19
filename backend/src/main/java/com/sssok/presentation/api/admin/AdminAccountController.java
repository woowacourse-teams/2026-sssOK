package com.sssok.presentation.api.admin;

import com.sssok.application.admin.AdminAccountService;
import com.sssok.domain.admin.Admin;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 계정", description = "관리자 계정 조회·생성·수정·삭제. 생성부터는 슈퍼관리자만 할 수 있다.")
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(
        summary = "관리자 계정 목록 조회",
        description = """
            등록된 관리자 계정을 모두 조회한다. 조회는 일반 관리자도 할 수 있다 — 누가 같이 \
            일하는지는 서로 알아야 하기 때문이다. 생성·수정·삭제만 슈퍼관리자로 제한한다.

            비밀번호는 평문도 해시도 내려주지 않는다. 익명 사용자 토큰으로 호출하면 403이 난다.\
            """
    )
    @GetMapping
    public ApiResponse<AdminAccountsResponse> findAll(@Parameter(hidden = true) @AuthAdmin Long adminId) {
        List<Admin> admins = adminAccountService.findAll(adminId);
        return ApiResponse.of(AdminAccountsResponse.from(admins));
    }

    @Operation(
        summary = "관리자 계정 생성",
        description = """
            슈퍼관리자가 새 관리자 계정을 만든다. 아이디는 4~20자의 영소문자·숫자·밑줄이어야 하고 \
            전체 관리자 중 유일해야 한다. 비밀번호는 8~72바이트이며 받는 즉시 해싱해 저장한다.

            role 을 생략하면 ADMIN 이다. 일반 관리자가 호출하면 403, 아이디가 겹치면 409가 난다.\
            """
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAccountResponse> create(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @RequestBody CreateAdminRequest request
    ) {
        Admin created = adminAccountService.create(
            adminId, request.loginId(), request.password(), request.name(), request.role());
        return ApiResponse.of(AdminAccountResponse.from(created));
    }

    @Operation(
        summary = "관리자 계정 수정",
        description = """
            슈퍼관리자가 관리자 계정의 이름·비밀번호·역할을 바꾼다. 보낸 항목만 바뀌는 부분 수정이고, \
            하나도 보내지 않으면 400이 난다. 로그인 아이디는 식별자라 바꿀 수 없다.

            마지막 남은 슈퍼관리자를 ADMIN 으로 강등하면 409가 난다. 계정을 관리할 수 있는 사람이 \
            아무도 없어지면 DB 를 직접 고치는 것 말고는 복구 수단이 없기 때문이다.

            비밀번호를 바꿔도 이미 발급된 토큰은 만료 전까지 살아 있다.\
            """
    )
    @PatchMapping("/{targetAdminId}")
    public ApiResponse<AdminAccountResponse> update(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "수정할 관리자의 ID") @PathVariable Long targetAdminId,
        @RequestBody UpdateAdminRequest request
    ) {
        Admin updated = adminAccountService.update(
            adminId, targetAdminId, request.name(), request.password(), request.role());
        return ApiResponse.of(AdminAccountResponse.from(updated));
    }

    @Operation(
        summary = "관리자 계정 삭제",
        description = """
            슈퍼관리자가 관리자 계정을 삭제한다. 삭제된 계정으로는 더 이상 로그인할 수 없다.

            마지막 남은 슈퍼관리자는 삭제할 수 없다(409). 본인이 본인을 지우는 경우도 같은 규칙을 탄다.

            삭제해도 이미 발급된 토큰 자체는 만료 전까지 유효하지만, 관리자 API 는 요청마다 계정을 \
            다시 읽어 권한을 판정하므로 삭제된 계정의 토큰으로는 아무것도 할 수 없다.\
            """
    )
    @DeleteMapping("/{targetAdminId}")
    public ApiResponse<DeleteAdminResponse> delete(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "삭제할 관리자의 ID") @PathVariable Long targetAdminId
    ) {
        Long deletedId = adminAccountService.delete(adminId, targetAdminId);
        return ApiResponse.of(new DeleteAdminResponse(deletedId));
    }
}
