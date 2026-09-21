package com.sssok.presentation.api.admin;

import com.sssok.domain.admin.Admin;
import java.util.List;

public record AdminAccountsResponse(List<AdminAccountResponse> accounts) {

    public static AdminAccountsResponse from(List<Admin> admins) {
        return new AdminAccountsResponse(admins.stream().map(AdminAccountResponse::from).toList());
    }
}
