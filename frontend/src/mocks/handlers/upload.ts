import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import { nicknameOf } from "./auth";
import { MOCK_HOST_ID, hasJoinedRoom, roomStatusOfId, uploadPolicyOfId } from "./room";

/**
 * backend `MediaType` enum 과 같은 목록이다.
 * 목이 여기서만 관대해지면 프론트에서 통과한 파일이 실제 서버에서 거절된다.
 */
const MIME_TYPES: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  mp4: "video/mp4",
  webm: "video/webm",
  mov: "video/quicktime",
};

const IMAGE_MAX_BYTES = 10 * 1024 * 1024;
const VIDEO_MAX_BYTES = 1024 * 1024 * 1024;

/** application.yml 의 `upload.presigned-url-ttl: 10m` 과 같은 값이다. */
const PRESIGNED_TTL_SECONDS = 600;

/** 서명 URL 재발급 한도. 넘으면 429 로 막는다 — 재발급 응답의 `maxRetryCount` 다. */
const MAX_RETRY_COUNT = 5;

/**
 * 폴더 API 가 아직 없어서 목이 아는 폴더만 통과시킨다.
 * 없는 폴더로 발급을 요청하면 404 FOLDER_NOT_FOUND 가 나는 걸 확인할 수 있다.
 */
export const MOCK_FOLDER_IDS = [31, 32];

/**
 * 실제 업로드 주소는 `https://<account-id>.r2.cloudflarestorage.com/<bucket>/<key>` 다.
 * 목은 계정이 없으니 가짜 호스트를 쓰되, 경로 모양(path-style)은 그대로 맞춘다.
 */
const MOCK_R2_ORIGIN = "https://mock-r2.sssok.dev";
const MOCK_R2_BUCKET = "sssok-dev";

export const MOCK_R2_BASE_URL = `${MOCK_R2_ORIGIN}/${MOCK_R2_BUCKET}`;

/**
 * 파일 이름에 넣으면 목이 특정 시나리오로 답한다. 실패 흐름을 손으로 확인할 때 쓴다.
 * 실제 사진 이름과 겹치지 않도록 밑줄 두 개로 감쌌다.
 *
 * 표식은 **최초 발급에만** 걸린다. 재발급받은 URL 은 멀쩡해서,
 * "깨진 뒤 다시 올려 성공하는" 흐름까지 손으로 따라갈 수 있다.
 */
export const UPLOAD_MOCK_MARKERS = {
  /** PUT 이 500 으로 깨지고 미디어가 FAILED 로 넘어간다 — 실패 UI·재시도 확인용 */
  putFailure: "__fail__",
  /** 이미 만료된 URL 을 발급한다 — 만료 403 흐름 확인용 */
  expiredUrl: "__expired__",
} as const;

type MediaStatus = "RESERVED" | "PROCESSING" | "READY" | "FAILED";

interface UploadUrlRequestFile {
  fileName: string;
  /** 서버는 확장자로 타입을 정하므로 이 값은 쓰지 않는다. 형식을 맞추려고 받기만 한다. */
  mimeType: string;
  size: number;
}

interface UploadUrlsRequest {
  files: UploadUrlRequestFile[];
  folderIds?: number[];
}

interface RegisterMediaRequest {
  mediaIds: number[];
}

interface ReissueRequest {
  size?: number;
}

/** 발급 시점에 만들어지는 미디어 한 건. 세 API 가 같은 기록을 본다. */
interface MockMedia {
  mediaId: number;
  roomId: number;
  uploaderId: number;
  fileName: string;
  mimeType: string;
  /** 발급 요청에 실려온 신고 크기. 재발급 때 갱신될 수 있다. */
  size: number;
  folderIds: number[];
  /** 재발급하면 새 키로 갈아끼운다 — 옛 PUT 이 뒤늦게 도착해도 새 파일을 덮지 않는다. */
  storageKey: string;
  status: MediaStatus;
  retryCount: number;
  /** 현재 storageKey 로 PUT 이 끝난 바이트 수. 아직이면 null 이다. */
  uploadedBytes: number | null;
  failOnPut: boolean;
  expiredUrl: boolean;
}

const mediaById = new Map<number, MockMedia>();
/** 옛 키까지 남겨둔다. 뒤늦게 도착한 PUT 이 어느 미디어 것이었는지 알아보려면 필요하다. */
const mediaIdByStorageKey = new Map<string, number>();

let nextMediaId = 5012;
let nextKeySequence = 1;

/** 테스트끼리 발급 기록과 번호가 이어지지 않도록 되돌린다. */
export const resetUploads = () => {
  mediaById.clear();
  mediaIdByStorageKey.clear();
  nextMediaId = 5012;
  nextKeySequence = 1;
};

const extensionOf = (fileName: string) => {
  const lastDot = fileName.lastIndexOf(".");

  if (lastDot === -1) {
    return "";
  }

  return fileName
    .slice(lastDot + 1)
    .trim()
    .toLowerCase();
};

const mimeTypeOf = (fileName: string) => MIME_TYPES[extensionOf(fileName)];

const maxBytesOf = (mimeType: string) =>
  mimeType.startsWith("image/") ? IMAGE_MAX_BYTES : VIDEO_MAX_BYTES;

/** backend `StorageKey.generate` 와 같은 모양이다. 목은 UUID 대신 순번을 써서 값이 예측된다. */
const generateStorageKey = (roomId: number, fileName: string) =>
  `rooms/${roomId}/mock-upload-${nextKeySequence++}.${extensionOf(fileName)}`;

/** `X-Amz-Date` 는 `20260826T053000Z` 형태의 ISO8601 basic 표기다. */
const formatAmzDate = (date: Date) =>
  date
    .toISOString()
    .replace(/\.\d+Z$/, "Z")
    .replace(/[-:]/g, "");

const AMZ_DATE_PATTERN = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/;

const parseAmzDate = (value: string | null) => {
  const matched = value === null ? null : AMZ_DATE_PATTERN.exec(value);

  if (matched === null) {
    return null;
  }

  const [, year, month, day, hour, minute, second] = matched.map(Number);

  return Date.UTC(year, month - 1, day, hour, minute, second);
};

/**
 * 만료는 URL 자체에 적혀 있다. 실제 R2 도 발급 후에는 바꿀 수 없는 값이라
 * 목도 별도 상태 대신 URL 을 그대로 읽는다.
 */
const isExpired = (url: URL) => {
  const issuedAt = parseAmzDate(url.searchParams.get("X-Amz-Date"));
  const ttlSeconds = Number(url.searchParams.get("X-Amz-Expires"));

  if (issuedAt === null || !Number.isFinite(ttlSeconds) || ttlSeconds <= 0) {
    return true;
  }

  return Date.now() > issuedAt + ttlSeconds * 1000;
};

const buildUploadUrl = (storageKey: string, issuedAt: Date) => {
  const query = new URLSearchParams({
    "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
    "X-Amz-Date": formatAmzDate(issuedAt),
    "X-Amz-Expires": String(PRESIGNED_TTL_SECONDS),
    // 발급 때 Content-Type 을 서명에 넣었다는 표시다. PUT 도 같은 헤더를 실어야 한다.
    "X-Amz-SignedHeaders": "content-type;host",
    "X-Amz-Signature": "mock-signature",
  });

  return `${MOCK_R2_BASE_URL}/${storageKey}?${query}`;
};

const storageKeyOf = (url: URL) =>
  decodeURIComponent(url.pathname.slice(`/${MOCK_R2_BUCKET}/`.length));

/**
 * 표식이 든 이름으로 처음 발급할 때만 과거 시각으로 서명한다.
 * 재발급본은 멀쩡해야 재시도 성공까지 확인할 수 있다.
 */
const signedAtFor = (media: MockMedia, now: Date) =>
  media.expiredUrl && media.retryCount === 0
    ? new Date(now.getTime() - PRESIGNED_TTL_SECONDS * 2 * 1000)
    : now;

/** 발급·재발급 응답이 공유하는 모양. `headers` 를 그대로 PUT 에 실으면 된다. */
const issuedPayload = (media: MockMedia) => ({
  mediaId: media.mediaId,
  fileName: media.fileName,
  uploadUrl: buildUploadUrl(media.storageKey, signedAtFor(media, new Date())),
  method: "PUT",
  headers: { "Content-Type": media.mimeType },
  expiresIn: PRESIGNED_TTL_SECONDS,
});

const error = (status: number, code: string, message: string) =>
  HttpResponse.json({ code, message }, { status });

const TOKEN_PATTERN = /^Bearer mock-token-(\d+)$/;

/** 목 토큰은 `mock-token-{userId}` 형태다. 회원 번호를 알아야 업로더를 가릴 수 있다. */
const memberIdOf = (authorization: string | null) => {
  const matched = authorization === null ? null : TOKEN_PATTERN.exec(authorization);

  return matched === null ? null : Number(matched[1]);
};

/**
 * 세 API 가 공유하는 방·권한 검사. 통과하면 null 이다.
 * 스펙의 실패 표에는 없지만 개요의 "권한: 참여자" 를 그대로 강제한다 —
 * 입장을 건너뛴 업로드는 실제 서버에서도 403 이다.
 */
const guardRoom = (roomId: number, memberId: number, token: string) => {
  const status = roomStatusOfId(roomId);

  if (status === null) {
    return error(404, "ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
  }

  if (status === "EXPIRED") {
    return error(410, "ROOM_EXPIRED", "만료된 방입니다.");
  }

  if (status === "DELETED") {
    return error(410, "ROOM_ALREADY_DELETED", "삭제된 방입니다.");
  }

  if (!hasJoinedRoom(token, roomId)) {
    return error(403, "ROOM_MEMBERSHIP_REQUIRED", "방에 입장한 뒤에 올릴 수 있어요.");
  }

  if (uploadPolicyOfId(roomId) === "host" && memberId !== MOCK_HOST_ID) {
    return error(403, "UPLOAD_NOT_ALLOWED", "방장만 업로드할 수 있는 방입니다.");
  }

  return null;
};

interface RejectedFile {
  fileName: string;
  code: "FILE_TOO_LARGE" | "UNSUPPORTED_MEDIA_TYPE" | "INVALID_PARAM";
  message: string;
}

/**
 * 파일 한 건을 검증한다. 걸리면 rejected 항목, 통과하면 null 이다.
 * 요청 전체를 실패시키지 않는 게 이 API 의 핵심이라 던지지 않고 값으로 돌려준다.
 */
const rejectionOf = (file: UploadUrlRequestFile): RejectedFile | null => {
  const fileName = typeof file?.fileName === "string" ? file.fileName : "";

  if (fileName === "" || typeof file.size !== "number" || !Number.isFinite(file.size)) {
    return { fileName, code: "INVALID_PARAM", message: "파일 정보가 올바르지 않습니다." };
  }

  if (file.size <= 0) {
    return { fileName, code: "INVALID_PARAM", message: "빈 파일은 올릴 수 없습니다." };
  }

  const mimeType = mimeTypeOf(fileName);

  if (mimeType === undefined) {
    return {
      fileName,
      code: "UNSUPPORTED_MEDIA_TYPE",
      message: "이미지와 영상만 업로드할 수 있습니다",
    };
  }

  if (file.size > maxBytesOf(mimeType)) {
    return {
      fileName,
      code: "FILE_TOO_LARGE",
      message: mimeType.startsWith("image/")
        ? "사진은 10MB까지 올릴 수 있어요."
        : "영상은 1GB까지 올릴 수 있어요.",
    };
  }

  return null;
};

/**
 * 목은 파일 바이트를 해석하지 않아 실제 치수를 모른다.
 * 갤러리 격자를 짜려면 값이 있어야 해서 타입별 고정 더미값을 준다.
 */
const dimensionsOf = (mimeType: string) =>
  mimeType.startsWith("image/")
    ? { width: 4032, height: 3024, duration: null }
    : { width: 1920, height: 1080, duration: 32 };

/** 공통 Media 객체. 등록 응답과 (나중에 붙을) 목록 조회가 같은 모양을 쓴다. */
const mediaPayload = (media: MockMedia) => ({
  mediaId: media.mediaId,
  type: media.mimeType.startsWith("image/") ? "IMAGE" : "VIDEO",
  fileName: media.fileName,
  mimeType: media.mimeType,
  size: media.size,
  // 워커가 만드는 값이라 PROCESSING 동안은 비어 있다. 목은 READY 로 넘기지 않는다.
  thumbnailUrl: null,
  originalUrl: null,
  ...dimensionsOf(media.mimeType),
  folderIds: media.folderIds,
  uploaderId: media.uploaderId,
  uploaderName: nicknameOf(media.uploaderId) ?? `멤버 ${media.uploaderId}`,
  status: media.status,
  uploadedAt: new Date().toISOString(),
});

export const uploadHandlers = [
  /**
   * 발급: 파일마다 서명 URL 을 내주고 RESERVED 미디어를 미리 만든다.
   * 파일 단위 실패는 요청 전체를 깨지 않고 `rejected` 로 갈라 내려간다.
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/media/upload-urls`, async ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const roomId = Number(params.roomId);
    const denied = guardRoom(roomId, memberId, token);

    if (denied !== null) {
      return denied;
    }

    const body = (await request.json().catch(() => null)) as UploadUrlsRequest | null;
    const files = body?.files;

    if (!Array.isArray(files) || files.length === 0) {
      return error(400, "INVALID_PARAM", "업로드할 파일이 없습니다");
    }

    const folderIds = body?.folderIds ?? [];

    if (!Array.isArray(folderIds)) {
      return error(400, "INVALID_PARAM", "폴더 정보가 올바르지 않습니다");
    }

    const unknownFolder = folderIds.find((folderId) => !MOCK_FOLDER_IDS.includes(folderId));

    if (unknownFolder !== undefined) {
      return error(404, "FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다");
    }

    const issued: ReturnType<typeof issuedPayload>[] = [];
    const rejected: RejectedFile[] = [];

    for (const file of files) {
      const rejection = rejectionOf(file);

      if (rejection !== null) {
        rejected.push(rejection);
        continue;
      }

      const media: MockMedia = {
        mediaId: nextMediaId++,
        roomId,
        uploaderId: memberId,
        fileName: file.fileName,
        // 서버는 클라이언트가 보낸 mimeType 을 쓰지 않고 확장자로 다시 정한다.
        mimeType: mimeTypeOf(file.fileName),
        size: file.size,
        folderIds,
        storageKey: generateStorageKey(roomId, file.fileName),
        status: "RESERVED",
        retryCount: 0,
        uploadedBytes: null,
        failOnPut: file.fileName.includes(UPLOAD_MOCK_MARKERS.putFailure),
        expiredUrl: file.fileName.includes(UPLOAD_MOCK_MARKERS.expiredUrl),
      };

      mediaById.set(media.mediaId, media);
      mediaIdByStorageKey.set(media.storageKey, media.mediaId);
      issued.push(issuedPayload(media));
    }

    return HttpResponse.json({ data: { issued, rejected } });
  }),

  /**
   * 스토리지로 직접 PUT.
   * 서명에 Content-Type 이 들어 있어 값이 다르거나 헤더가 없으면 403 이다
   * (docs/backend/R2_PRESIGNED_UPLOAD.md 2장에서 실제로 확인한 동작).
   */
  http.put(`${MOCK_R2_BASE_URL}/*`, async ({ request }) => {
    const url = new URL(request.url);
    const storageKey = storageKeyOf(url);
    const mediaId = mediaIdByStorageKey.get(storageKey);
    const media = mediaId === undefined ? undefined : mediaById.get(mediaId);

    // presigned URL 은 이미 서명이 실려 있다. Authorization 을 같이 보내면 실제 R2 도 거절한다.
    if (request.headers.get("Authorization") !== null) {
      return new HttpResponse(null, { status: 403 });
    }

    if (media === undefined || isExpired(url)) {
      return new HttpResponse(null, { status: 403 });
    }

    if (request.headers.get("Content-Type") !== media.mimeType) {
      return new HttpResponse(null, { status: 403 });
    }

    if (media.failOnPut && media.retryCount === 0) {
      media.status = "FAILED";

      return new HttpResponse(null, { status: 500 });
    }

    const uploadedBytes = (await request.arrayBuffer()).byteLength;

    // 재발급으로 키가 갈린 뒤 뒤늦게 도착한 PUT 이다.
    // 스토리지는 받아주지만(고아 객체) 미디어는 새 키만 본다.
    if (media.storageKey === storageKey) {
      media.uploadedBytes = uploadedBytes;
    }

    return new HttpResponse(null, { status: 200, headers: { ETag: '"mock-etag"' } });
  }),

  /**
   * 완료 등록: PUT 이 끝난 미디어를 방 목록에 노출시킨다.
   * 미디어 단위 실패는 `failed` 로 갈라 내려간다.
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/media`, async ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const roomId = Number(params.roomId);
    const denied = guardRoom(roomId, memberId, token);

    if (denied !== null) {
      return denied;
    }

    const body = (await request.json().catch(() => null)) as RegisterMediaRequest | null;
    const mediaIds = body?.mediaIds;

    if (!Array.isArray(mediaIds) || mediaIds.length === 0) {
      return error(400, "INVALID_PARAM", "등록할 미디어가 없습니다");
    }

    // 남의 예약이 하나라도 섞이면 요청 전체를 막는다 (스펙의 전체 403).
    const stolen = mediaIds.some((mediaId) => {
      const media = mediaById.get(mediaId);

      return media !== undefined && media.uploaderId !== memberId;
    });

    if (stolen) {
      return error(403, "MEDIA_FORBIDDEN", "본인이 발급받은 업로드가 아닙니다");
    }

    const registered: ReturnType<typeof mediaPayload>[] = [];
    const failed: { mediaId: number; code: string; message: string }[] = [];

    for (const mediaId of mediaIds) {
      const media = mediaById.get(mediaId);

      if (media === undefined || media.roomId !== roomId) {
        failed.push({
          mediaId,
          code: "MEDIA_NOT_FOUND",
          message: "업로드 요청을 찾을 수 없습니다",
        });
        continue;
      }

      // 스펙에는 없지만 재시도로 두 번 불릴 수 있어 목이 먼저 막아둔다.
      if (media.status === "PROCESSING" || media.status === "READY") {
        failed.push({
          mediaId,
          code: "UPLOAD_ALREADY_COMPLETED",
          message: "이미 등록이 끝난 파일입니다",
        });
        continue;
      }

      // 스토리지에 실제 객체가 있는지 확인하는 자리다 (서버 책임 ①).
      // 0바이트 PUT 도 스토리지는 200 을 주므로 여기서 걸러야 한다.
      if (media.uploadedBytes === null || media.uploadedBytes === 0) {
        failed.push({
          mediaId,
          code: "UPLOAD_NOT_COMPLETED",
          message: "업로드가 완료되지 않았습니다. 다시 시도해 주세요",
        });
        continue;
      }

      // 실제 크기 재확인 (서버 책임 ②). 신고값이 아니라 올라온 바이트로 본다.
      if (media.uploadedBytes > maxBytesOf(media.mimeType)) {
        failed.push({
          mediaId,
          code: "FILE_TOO_LARGE",
          message: "파일 용량이 허용 크기를 초과했습니다",
        });
        continue;
      }

      media.status = "PROCESSING";
      registered.push(mediaPayload(media));
    }

    return HttpResponse.json({ data: { registered, failed } }, { status: 201 });
  }),

  /**
   * 재발급: 만료되거나 전송이 깨진 업로드에 새 URL 을 준다.
   * mediaId 는 유지하고 storageKey 만 갈아끼워, 뒤늦게 도착한 옛 PUT 이 새 파일을 덮지 못하게 한다.
   */
  http.post(
    `${API_BASE_URL}/rooms/:roomId/media/:mediaId/upload-url`,
    async ({ request, params }) => {
      const token = request.headers.get("Authorization");
      const memberId = memberIdOf(token);

      if (token === null || memberId === null) {
        return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
      }

      const roomId = Number(params.roomId);
      // 최초 발급 뒤에 방장이 권한을 바꿨을 수 있어 여기서 다시 검사한다 (서버 책임 ③).
      const denied = guardRoom(roomId, memberId, token);

      if (denied !== null) {
        return denied;
      }

      const media = mediaById.get(Number(params.mediaId));

      if (media === undefined || media.roomId !== roomId) {
        return error(404, "MEDIA_NOT_FOUND", "업로드 요청을 찾을 수 없습니다");
      }

      // 방장도 남의 예약은 못 만진다 (서버 책임 ②).
      if (media.uploaderId !== memberId) {
        return error(403, "MEDIA_FORBIDDEN", "본인이 요청한 업로드가 아닙니다");
      }

      if (media.status === "PROCESSING" || media.status === "READY") {
        return error(409, "UPLOAD_ALREADY_COMPLETED", "이미 업로드가 완료된 파일입니다");
      }

      if (media.retryCount >= MAX_RETRY_COUNT) {
        return error(429, "UPLOAD_RETRY_EXCEEDED", "재시도 횟수를 초과했습니다");
      }

      const body = (await request.json().catch(() => null)) as ReissueRequest | null;
      const size = body?.size;

      if (size !== undefined) {
        if (typeof size !== "number" || !Number.isFinite(size) || size <= 0) {
          return error(400, "INVALID_PARAM", "파일 크기가 올바르지 않습니다");
        }

        if (size > maxBytesOf(media.mimeType)) {
          return error(413, "FILE_TOO_LARGE", "파일 용량 제한을 초과했습니다");
        }

        media.size = size;
      }

      media.retryCount += 1;
      media.status = "RESERVED";
      media.uploadedBytes = null;
      media.storageKey = generateStorageKey(roomId, media.fileName);
      mediaIdByStorageKey.set(media.storageKey, media.mediaId);

      return HttpResponse.json({
        data: {
          ...issuedPayload(media),
          retryCount: media.retryCount,
          maxRetryCount: MAX_RETRY_COUNT,
        },
      });
    },
  ),
];
