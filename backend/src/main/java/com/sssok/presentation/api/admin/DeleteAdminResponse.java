package com.sssok.presentation.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteAdminResponse(@Schema(description = "삭제된 관리자의 ID") Long deletedAdminId) {
}
