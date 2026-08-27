import { bypass, http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import type { Media } from "@/entities/media";
import { addRegisteredMedia, registeredMediaOf } from "../db";
import { galleryEntryOf, rememberUploadTarget, rememberUploadedBytes } from "../uploadedMedia";

/**
 * 실서버에 붙은 채로 **미디어 목록 조회 한 구멍만** 메우는 핸들러다 (`MOCK=hybrid`).
 *
 * 백엔드에는 `GET /rooms/{roomId}/media` 가 없다. 갤러리가 통째로 그 위에 서 있어서
 * 목이 없으면 방에 들어가자마자 화면이 깨진다. 여기 없는 경로는 모두 진짜 서버로 나간다 —
 * 인증·방 생성·방 조회·입장은 실제 응답을 받는다.
 *
 * 업로드 3종과 스토리지 PUT 도 **진짜가 처리한다.** 목은 지나가는 것을 옆에서 볼 뿐이다.
 * 무엇을 왜 챙겨두는지는 `../uploadedMedia.ts` 에 있다.
 *
 * 서버에 목록 API 가 생기면 이 파일과 `MOCK_MODE` 의 `hybrid` 를 통째로 지운다.
 */

/** R2 서명 URL. 계정 ID 가 서브도메인이라 호스트를 통째로 적을 수 없다. */
const R2_UPLOAD_URL = /r2\.cloudflarestorage\.com/;

interface IssuedBody {
  data?: { issued?: { mediaId: number; uploadUrl: string }[] };
}

interface ReissuedBody {
  data?: { mediaId: number; uploadUrl: string };
}

interface RegisteredBody {
  data?: { registered?: Media[] };
}

/**
 * 요청을 진짜 서버로 넘기고, 오가는 응답을 읽은 뒤 그대로 돌려준다.
 * 본문을 한 번 읽으면 원본 응답은 재사용할 수 없어 같은 내용으로 다시 만든다.
 */
const relay = async <T>(request: Request, onBody: (body: T) => void) => {
  // bypass 를 씌워야 이 핸들러가 자기 요청을 다시 가로채지 않는다.
  const response = await fetch(bypass(request));

  // 실패는 손대지 않고 흘려보낸다. 프론트가 서버 에러를 그대로 봐야 한다.
  if (!response.ok) {
    return response;
  }

  const body: unknown = await response.json().catch(() => null);

  if (body !== null) {
    onBody(body as T);
  }

  return HttpResponse.json(body as Record<string, unknown>, { status: response.status });
};

export const hybridHandlers = [
  /** 발급. 어느 mediaId 가 어느 스토리지 경로로 갈지 적어둔다. */
  http.post(`${API_BASE_URL}/rooms/:roomId/media/upload-urls`, ({ request }) =>
    relay<IssuedBody>(request, (body) => {
      for (const { mediaId, uploadUrl } of body.data?.issued ?? []) {
        rememberUploadTarget(mediaId, uploadUrl);
      }
    }),
  ),

  /** 재발급. 스토리지 키가 갈리므로 새 경로로 덮는다. */
  http.post(`${API_BASE_URL}/rooms/:roomId/media/:mediaId/upload-url`, ({ request }) =>
    relay<ReissuedBody>(request, (body) => {
      if (body.data !== undefined) {
        rememberUploadTarget(body.data.mediaId, body.data.uploadUrl);
      }
    }),
  ),

  /**
   * 스토리지로 나가는 PUT. **바이트는 그대로 진짜 R2 로 간다.**
   * 목은 지나가는 김에 사본을 떠서 갤러리 썸네일로 쓴다.
   */
  http.put(R2_UPLOAD_URL, async ({ request }) => {
    const contentType = request.headers.get("Content-Type") ?? "";
    const body = await request.arrayBuffer();

    // 본문을 이미 읽어 원본 요청은 다시 못 보낸다. 같은 내용으로 새로 만든다.
    const response = await fetch(
      bypass(new Request(request.url, { method: "PUT", headers: request.headers, body })),
    );

    if (response.ok) {
      rememberUploadedBytes(request.url, contentType, body);
    }

    return response;
  }),

  /** 완료 등록. 서버가 등록한 미디어에 챙겨둔 실물을 이어 붙여 목록에 쌓는다. */
  http.post(`${API_BASE_URL}/rooms/:roomId/media`, ({ request, params }) =>
    relay<RegisteredBody>(request, (body) => {
      for (const media of body.data?.registered ?? []) {
        addRegisteredMedia(Number(params.roomId), galleryEntryOf(media));
      }
    }),
  ),

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
