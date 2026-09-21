package com.sssok.presentation.api.mediafolder;

import com.sssok.application.mediafolder.AddMediaToFoldersResult;
import com.sssok.application.mediafolder.AddMediaToFoldersService;
import com.sssok.application.mediafolder.RemoveMediaFromFoldersResult;
import com.sssok.application.mediafolder.RemoveMediaFromFoldersService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어-폴더", description = "방 안 사진들을 폴더에 담거나 폴더에서 꺼내는 다건 처리")
@RestController
@RequestMapping("/rooms/{roomId}/media/folders")
@RequiredArgsConstructor
public class MediaFolderController {

    private final AddMediaToFoldersService addMediaToFoldersService;
    private final RemoveMediaFromFoldersService removeMediaFromFoldersService;

    @Operation(
        summary = "폴더에 담기",
        description = "selection.mode가 include면 ids만, exclude면 방 전체에서 ids를 제외한 미디어를 folderId에 담는다. "
            + "include+빈 ids는 아무것도 선택하지 않으며 exclude+빈 ids는 전체를 선택한다. 이미 속한 다른 폴더는 유지되며, "
            + "이미 이 폴더에 담긴 미디어는 오류 없이 alreadyInCount로만 집계된다(멱등). 존재하지 않는 mediaId는 "
            + "그 미디어만 건너뛰고 notFoundMediaIds로 알려준다 — 반면 folderId가 없는 폴더면 요청 전체를 "
            + "거부한다(404). selection 또는 folderId가 없거나 선택 값이 잘못되면 400이 난다. 빈 선택 결과에는 이벤트를 "
            + "발행하지 않는다. 성공하면 SSE로 "
            + "media.folders.updated(action: ADD) 이벤트가 발행된다."
    )
    @PutMapping
    public ApiResponse<AddToFoldersResponse> addToFolders(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody AddToFoldersRequest request
    ) {
        AddMediaToFoldersResult result =
            addMediaToFoldersService.add(roomId,
                request == null || request.selection() == null ? null : request.selection().toSelection(),
                request == null ? null : request.folderId());
        return ApiResponse.of(AddToFoldersResponse.from(result));
    }

    @Operation(
        summary = "폴더에서 꺼내기",
        description = "selection.mode가 include면 ids만, exclude면 방 전체에서 ids를 제외한 미디어를 folderIds가 "
            + "가리키는 폴더들에서 꺼낸다. include+빈 ids는 빈 선택, exclude+빈 ids는 전체 선택이다. "
            + "folderIds를 생략하거나 빈 배열을 "
            + "보내면 속한 모든 폴더에서 꺼내 루트로 보낸다. 이 요청으로 어떤 미디어가 결과적으로 폴더 소속이 "
            + "0개가 되면 movedToRootMediaIds에 담긴다(다른 폴더에 여전히 속해 있으면 포함되지 않는다). 존재하지 "
            + "않는 mediaId는 건너뛰고 notFoundMediaIds로 알려준다. selection이 잘못되면 400, folderIds 중 "
            + "하나라도 없는 폴더면 404가 난다. 성공하면 SSE로 media.folders.updated(action: REMOVE) 이벤트가 "
            + "발행된다."
    )
    @DeleteMapping
    public ApiResponse<RemoveFromFoldersResponse> removeFromFolders(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody RemoveFromFoldersRequest request
    ) {
        RemoveMediaFromFoldersResult result =
            removeMediaFromFoldersService.remove(roomId,
                request == null || request.selection() == null ? null : request.selection().toSelection(),
                request == null ? null : request.folderIds());
        return ApiResponse.of(RemoveFromFoldersResponse.from(result));
    }
}
