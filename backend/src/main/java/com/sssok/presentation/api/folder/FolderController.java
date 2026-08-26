package com.sssok.presentation.api.folder;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.DeleteFolderResult;
import com.sssok.application.folder.DeleteFolderService;
import com.sssok.application.folder.RenameFolderService;
import com.sssok.domain.folder.Folder;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "폴더", description = "방 안에서 사진을 정리하는 폴더의 생성·이름변경·삭제")
@RestController
@RequestMapping("/rooms/{roomId}/folders")
@RequiredArgsConstructor
public class FolderController {

    private final CreateFolderService createFolderService;
    private final RenameFolderService renameFolderService;
    private final DeleteFolderService deleteFolderService;

    @Operation(
        summary = "폴더 생성",
        description = "방 안에 새 폴더를 만든다. 이름은 1~12자(공백 제거)여야 하고, 같은 방 안에 같은 이름의 "
            + "폴더가 있으면 409가 난다. 입장하지 않은 사용자는 403, 없는 방은 404, 만료·삭제된 방은 410이 난다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CreateFolderRequest request
    ) {
        Folder folder = createFolderService.create(roomId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(FolderResponse.from(folder, 0)));
    }

    @Operation(
        summary = "폴더 이름 변경",
        description = "폴더 이름을 바꾼다. 자기 자신과 같은 이름으로 바꾸는 요청은 중복으로 보지 않고 그대로 "
            + "허용한다. 없는 폴더는 404, 그 외 실패 케이스(403/404/410/409)는 생성과 동일하다."
    )
    @PatchMapping("/{folderId}")
    public ApiResponse<FolderResponse> renameFolder(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "폴더 생성 응답의 id") @PathVariable Long folderId,
        @RequestBody RenameFolderRequest request
    ) {
        Folder folder = renameFolderService.rename(roomId, folderId, request.name());
        return ApiResponse.of(FolderResponse.from(folder, 0));
    }

    @Operation(
        summary = "폴더 삭제",
        description = "폴더만 삭제하고 폴더에 담겨 있던 사진은 지우지 않는다 — 사진들은 폴더 소속만 없어지고 "
            + "루트로 옮겨진다. 없는 폴더는 404, 그 외 실패 케이스(403/404/410)는 생성과 동일하다."
    )
    @DeleteMapping("/{folderId}")
    public ApiResponse<DeleteFolderResponse> deleteFolder(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "폴더 생성 응답의 id") @PathVariable Long folderId
    ) {
        DeleteFolderResult result = deleteFolderService.delete(roomId, folderId);
        return ApiResponse.of(DeleteFolderResponse.from(result));
    }
}
