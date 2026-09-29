package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IssueUploadUrlsRequest(
    // 파일별 메타데이터는 여기서 거르지 않는다. 일부만 잘못됐을 때 issued/rejected 로 나눠
    // 내려주는 게 이 API 의 계약이라, 그 판단은 서비스가 파일별로 한다.
    @Schema(description = "업로드할 파일 목록")
    List<@NotNull(message = "파일 정보가 올바르지 않습니다") UploadFileRequest> files,

    @Schema(description = "업로드와 동시에 담을 폴더 ID 목록. 생략하면 루트")
    List<@NotNull(message = "폴더 ID 목록이 올바르지 않습니다") Long> folderIds
) {
    public record UploadFileRequest(
        @Schema(description = "원본 파일명", example = "IMG_0421.jpg") String fileName,
        @Schema(description = "파일 MIME 타입", example = "image/jpeg") String mimeType,
        @Schema(description = "파일 바이트 크기", example = "3840219") Long size
    ) {
    }
}
