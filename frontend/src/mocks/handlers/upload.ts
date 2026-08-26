import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import { MOCK_ROOM_ID } from "./room";

/**
 * backend `MediaType` enum 과 같은 목록이다.
 * 목이 여기서만 관대해지면 프론트에서 통과한 파일이 실제 서버에서 거절된다.
 */
const CONTENT_TYPES: Record<string, string> = {
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
 */
export const UPLOAD_MOCK_MARKERS = {
  /** PUT 이 500 으로 깨진다 — 업로드 실패 모달 확인용 */
  putFailure: "__fail__",
  /** 이미 만료된 URL 을 발급한다 — 만료 403 흐름 확인용 */
  expiredUrl: "__expired__",
} as const;

interface UploadUrlRequestFile {
  fileName: string;
  /** 서버는 확장자로 타입을 정하므로 이 값은 쓰지 않는다. 형식을 맞추려고 받기만 한다. */
  contentType: string;
  size: number;
}

interface UploadUrlsRequest {
  folderId?: number | null;
  files: UploadUrlRequestFile[];
}

interface CompleteUploadRequest {
  storageKeys: string[];
}

/** 발급한 URL 한 건. PUT 과 완료 확정이 같은 기록을 본다. */
interface IssuedUpload {
  fileName: string;
  contentType: string;
  size: number;
  folderId: number | null;
  failOnPut: boolean;
  uploaded: boolean;
}

const issuedUploads = new Map<string, IssuedUpload>();

let nextKeySequence = 1;
let nextFileId = 9001;

/** 테스트끼리 발급 기록과 번호가 이어지지 않도록 되돌린다. */
export const resetUploads = () => {
  issuedUploads.clear();
  nextKeySequence = 1;
  nextFileId = 9001;
};

const extensionOf = (fileName: string) => {
  const lastDot = fileName.lastIndexOf(".");

  if (lastDot === -1) {
    return "";
  }

  const extension = fileName.slice(lastDot + 1);

  return extension.trim().toLowerCase();
};

const maxBytesOf = (contentType: string) =>
  contentType.startsWith("image/") ? IMAGE_MAX_BYTES : VIDEO_MAX_BYTES;

/** backend `StorageKey.generate` 와 같은 모양이다. 목은 UUID 대신 순번을 써서 값이 예측된다. */
const generateStorageKey = (extension: string) =>
  `rooms/${MOCK_ROOM_ID}/mock-upload-${nextKeySequence++}.${extension}`;

/** `X-Amz-Date` 는 `20260826T053000Z` 형태의 ISO8601 basic 표기다. */
const formatAmzDate = (date: Date) => {
  const isoWithoutMillis = date.toISOString().replace(/\.\d+Z$/, "Z");

  return isoWithoutMillis.replace(/[-:]/g, "");
};

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

const unauthorized = () =>
  HttpResponse.json({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }, { status: 401 });

const roomNotFound = () =>
  HttpResponse.json(
    { code: "ROOM_NOT_FOUND", message: "존재하지 않는 방입니다." },
    { status: 404 },
  );

const invalidBody = (message: string) =>
  HttpResponse.json({ code: "INVALID_REQUEST_BODY", message }, { status: 400 });

export const uploadHandlers = [
  /**
   * 1단계: presigned URL 발급.
   * 문서의 요청 형식은 배열이지만 `folderId` 를 담을 자리가 없어 객체로 감쌌다 (이슈 #71).
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/media/upload-urls`, async ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== MOCK_ROOM_ID) {
      return roomNotFound();
    }

    const { folderId = null, files } = (await request.json()) as UploadUrlsRequest;

    if (!Array.isArray(files) || files.length === 0) {
      return invalidBody("업로드할 파일이 없습니다.");
    }

    // 한 건이라도 걸리면 아무것도 발급하지 않는다. 서버도 한 트랜잭션으로 처리한다.
    for (const file of files) {
      const contentType = CONTENT_TYPES[extensionOf(file.fileName)];

      if (contentType === undefined) {
        return HttpResponse.json(
          {
            code: "UNSUPPORTED_MEDIA_TYPE",
            message: `올릴 수 없는 형식이에요: ${file.fileName}`,
          },
          { status: 400 },
        );
      }

      if (file.size > maxBytesOf(contentType)) {
        return HttpResponse.json(
          {
            code: "FILE_TOO_LARGE",
            message: contentType.startsWith("image/")
              ? "사진은 10MB까지 올릴 수 있어요."
              : "영상은 1GB까지 올릴 수 있어요.",
          },
          { status: 413 },
        );
      }
    }

    const issuedAt = new Date();

    const issued = files.map((file) => {
      // 서버는 클라이언트가 보낸 contentType 을 쓰지 않고 확장자로 다시 정한다.
      const contentType = CONTENT_TYPES[extensionOf(file.fileName)];
      const storageKey = generateStorageKey(extensionOf(file.fileName));

      issuedUploads.set(storageKey, {
        fileName: file.fileName,
        contentType,
        size: file.size,
        folderId,
        failOnPut: file.fileName.includes(UPLOAD_MOCK_MARKERS.putFailure),
        uploaded: false,
      });

      const expiredIssuedAt = new Date(issuedAt.getTime() - PRESIGNED_TTL_SECONDS * 2 * 1000);
      const signedAt = file.fileName.includes(UPLOAD_MOCK_MARKERS.expiredUrl)
        ? expiredIssuedAt
        : issuedAt;

      return {
        uploadUrl: buildUploadUrl(storageKey, signedAt),
        storageKey,
        contentType,
      };
    });

    // PENDING 상태의 StoredFile 이 이 시점에 만들어진다.
    return HttpResponse.json({ data: issued }, { status: 201 });
  }),

  /**
   * 2단계: R2 로 직접 PUT.
   * 서명에 Content-Type 이 들어 있어 값이 다르거나 헤더가 없으면 403 이다
   * (docs/backend/R2_PRESIGNED_UPLOAD.md 2장에서 실제로 확인한 동작).
   */
  http.put(`${MOCK_R2_BASE_URL}/*`, ({ request }) => {
    const url = new URL(request.url);
    const upload = issuedUploads.get(storageKeyOf(url));

    // presigned URL 은 이미 서명이 실려 있다. Authorization 을 같이 보내면 실제 R2 도 거절한다.
    if (request.headers.get("Authorization") !== null) {
      return new HttpResponse(null, { status: 403 });
    }

    if (upload === undefined || isExpired(url)) {
      return new HttpResponse(null, { status: 403 });
    }

    if (request.headers.get("Content-Type") !== upload.contentType) {
      return new HttpResponse(null, { status: 403 });
    }

    if (upload.failOnPut) {
      return new HttpResponse(null, { status: 500 });
    }

    upload.uploaded = true;

    return new HttpResponse(null, { status: 200, headers: { ETag: '"mock-etag"' } });
  }),

  /** 3단계: 완료 확정. PUT 이 끝난 파일만 갤러리에 올린다. */
  http.post(`${API_BASE_URL}/rooms/:roomId/media/complete`, async ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== MOCK_ROOM_ID) {
      return roomNotFound();
    }

    const { storageKeys } = (await request.json()) as CompleteUploadRequest;

    if (!Array.isArray(storageKeys) || storageKeys.length === 0) {
      return invalidBody("확정할 파일이 없습니다.");
    }

    for (const storageKey of storageKeys) {
      const upload = issuedUploads.get(storageKey);

      if (upload === undefined) {
        return HttpResponse.json(
          { code: "STORED_FILE_NOT_FOUND", message: "발급한 적 없는 파일입니다." },
          { status: 404 },
        );
      }

      if (!upload.uploaded) {
        return HttpResponse.json(
          { code: "UPLOAD_NOT_COMPLETED", message: "아직 업로드가 끝나지 않은 파일이 있어요." },
          { status: 400 },
        );
      }
    }

    const completed = storageKeys.map((storageKey) => {
      const upload = issuedUploads.get(storageKey)!;

      return {
        fileId: nextFileId++,
        storageKey,
        fileName: upload.fileName,
        contentType: upload.contentType,
        size: upload.size,
        folderId: upload.folderId,
        status: "COMPLETED",
        createdAt: "2026-08-18T05:32:00Z",
      };
    });

    return HttpResponse.json({ data: completed }, { status: 201 });
  }),
];
