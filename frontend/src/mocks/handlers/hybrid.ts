import { bypass, http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import type { Media } from "@/entities/media";
import {
  addRegisteredMedia,
  originalUrlOf,
  registeredMediaOf,
  thumbnailUrlOf,
  type GalleryMedia,
} from "../db";

/**
 * 실서버에 붙은 채로 **미디어 목록 조회 한 구멍만** 메우는 핸들러다 (`MOCK=hybrid`).
 *
 * 백엔드에는 `GET /rooms/{roomId}/media` 가 없다. 갤러리가 통째로 그 위에 서 있어서
 * 목이 없으면 방에 들어가자마자 화면이 깨진다. 여기 없는 경로는 모두 진짜 서버로 나간다 —
 * 인증·방 생성·방 조회·입장·업로드 3종은 실제 응답을 받는다.
 *
 * 서버에 목록 API 가 생기면 이 파일과 `MOCK_MODE` 의 `hybrid` 를 통째로 지운다.
 */

/**
 * 등록 응답의 미디어를 갤러리가 그릴 수 있는 모양으로 맞춘다.
 *
 * 등록 직후에는 워커가 아직 안 돌아 파생 URL 이 비어 있고 상태도 `PROCESSING` 이다.
 * 갤러리는 `READY` 만 그리므로 여기서 채워 넣는다.
 *
 * 채워 넣는 썸네일은 **올린 사진이 아니다.** 목록 API 가 없어 실물 주소를 받아올 창구가 없고,
 * 다운로드 API(`/media/{mediaId}/download`)는 Authorization 헤더를 요구해서 `<img>` 로 못 건다.
 * 그림은 자리표시자로 보고, 파일명·용량·올린 사람·시각이 맞는지로 연동을 확인한다.
 */
const PLACEHOLDER_DIMENSIONS = {
  IMAGE: { width: 4032, height: 3024 },
  VIDEO: { width: 1920, height: 1080 },
} as const;

const galleryEntryOf = (media: Media): GalleryMedia => ({
  ...media,
  thumbnailUrl: media.thumbnailUrl ?? thumbnailUrlOf(media.mediaId),
  originalUrl: media.originalUrl ?? originalUrlOf(media.mediaId, media.type),
  // 치수도 워커가 채우는 값이라 등록 직후에는 비어 있다. 갤러리 격자가 무너지지 않게 메운다.
  width: media.width ?? PLACEHOLDER_DIMENSIONS[media.type].width,
  height: media.height ?? PLACEHOLDER_DIMENSIONS[media.type].height,
  status: "READY",
});

interface CompleteUploadBody {
  data?: { registered?: Media[] };
}

export const hybridHandlers = [
  /**
   * 완료 등록은 **진짜 서버가 처리한다.** 목은 오가는 응답을 옆에서 볼 뿐이다.
   *
   * 그래야 등록된 미디어를 아래 목록 핸들러가 돌려줄 수 있다. 이걸 안 하면
   * 실서버로 올린 사진이 갤러리에 영영 나타나지 않는다.
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/media`, async ({ request, params }) => {
    // bypass 를 씌워야 이 핸들러가 자기 요청을 다시 가로채지 않는다.
    const response = await fetch(bypass(request));

    // 실패는 손대지 않고 그대로 흘려보낸다. 프론트가 서버 에러를 그대로 봐야 한다.
    if (!response.ok) {
      return response;
    }

    const body = (await response.json().catch(() => null)) as CompleteUploadBody | null;

    for (const media of body?.data?.registered ?? []) {
      addRegisteredMedia(Number(params.roomId), galleryEntryOf(media));
    }

    // 본문을 이미 읽어버려 원본 응답은 재사용할 수 없다. 같은 내용으로 다시 만든다.
    return HttpResponse.json(body, { status: response.status });
  }),

  /** 서버에 없는 목록 조회. 이번 세션에 등록된 것만 최신순으로 돌려준다. */
  http.get(`${API_BASE_URL}/rooms/:roomId/media`, ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return HttpResponse.json(
        { code: "UNAUTHORIZED", message: "인증이 필요합니다." },
        { status: 401 },
      );
    }

    return HttpResponse.json({ data: { items: registeredMediaOf(Number(params.roomId)) } });
  }),
];
