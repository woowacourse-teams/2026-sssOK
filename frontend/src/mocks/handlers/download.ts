import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import { buildStoreZip } from "../zip/storeZip";
import { zipFileNames } from "../zip/zipFileNames";
import { hasFolder, hasJoinedRoom, mediaOfRoom, roomCodeOfId, roomStatusOfId } from "./room";

/**
 * 다운로드 목 (B-6 · B-7-1 · B-7-2).
 *
 * **목이 서버 워커 역할을 그대로 한다.** 잡을 만들면 원본을 실제로 받아 zip 을 조립하고,
 * 그동안 진행률이 오른다. 가짜 진행률을 흘리면 "다 됐다는데 파일이 없다" 같은 어긋남을
 * 프론트가 붙일 때까지 못 잡는다.
 *
 * 실제 서버가 아직 `main` 에 없어서, 지금은 이 목이 유일하게 동작하는 계약이다.
 */

const error = (status: number, code: string, message: string) =>
  HttpResponse.json({ code, message }, { status });

const TOKEN_PATTERN = /^Bearer mock-token-(\d+)$/;

const memberIdOf = (authorization: string | null) => {
  const matched = authorization === null ? null : TOKEN_PATTERN.exec(authorization);

  return matched === null ? null : Number(matched[1]);
};

/** 업로드 목의 `guardRoom` 과 같은 검사다. 방·입장 여부를 먼저 거른다. */
const guardRoom = (roomId: number, token: string) => {
  const status = roomStatusOfId(roomId);

  if (status === null) {
    return error(404, "ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
  }

  if (status !== "ACTIVE") {
    return error(410, "ROOM_ALREADY_DELETED", "이미 삭제되었거나 만료된 방입니다");
  }

  if (!hasJoinedRoom(token, roomId)) {
    return error(403, "NOT_ROOM_MEMBER", "입장한 방에서만 이용할 수 있습니다");
  }

  return null;
};

/** backend `DownloadFileNames.zipNameOf` 와 같은 형식이다. */
const zipNameOf = (roomCode: string) => `ShareDrop_${roomCode}.zip`;

/** application.yml 의 `download.max-concurrent-jobs-per-requester: 3` 과 같은 값이다. */
const MAX_CONCURRENT_JOBS = 3;

/** `mediaIds` 상한. backend `DownloadTargetResolver.MAX_MEDIA_IDS` 와 같다. */
const MAX_MEDIA_IDS = 1000;

/** `download.retention: 1h`. READY 시점부터 센다. */
const RETENTION_MS = 60 * 60 * 1000;

/** 목이 zip 을 내주는 주소. 실제로는 R2 서명 URL 이다. */
const ZIP_HOST = "https://storage.example.com/zips";

type JobStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "EXPIRED";

interface DownloadJob {
  jobId: string;
  requesterId: number;
  roomId: number;
  status: JobStatus;
  /** 0~100. 원본을 하나 받을 때마다 오른다. */
  progress: number;
  mediaCount: number;
  totalSize: number;
  fileName: string;
  /** 조립이 끝난 zip. READY 전에는 null 이다. */
  blob: Blob | null;
  readyAt: number | null;
  failureReason: string | null;
}

const jobs = new Map<string, DownloadJob>();
let jobSequence = 0;

/** 스펙의 `dl_7d1e93` 형태를 흉내낸다. 실제 서버는 Long 을 쓴다 — 프론트는 그대로 URL 에만 싣는다. */
const nextJobId = () => {
  jobSequence += 1;

  return `dl_${jobSequence.toString(16).padStart(6, "0")}`;
};

/**
 * 원본을 하나씩 받아 zip 으로 묶는다. **잡을 만든 뒤 뒤에서 도는 부분이다.**
 *
 * 받다가 실패한 것은 건너뛴다. 원본 하나가 깨졌다고 잡 전체를 무너뜨리면 zip 흐름을
 * 확인할 수가 없다 — 실제 워커도 받은 것까지는 묶는다.
 */
const runCompression = async (job: DownloadJob, sources: { name: string; url: string }[]) => {
  job.status = "RUNNING";

  const entries: { name: string; blob: Blob }[] = [];

  for (const [index, source] of sources.entries()) {
    try {
      const response = await fetch(source.url);

      if (response.ok) {
        entries.push({ name: source.name, blob: await response.blob() });
      }
    } catch {
      // 못 받은 원본은 빼고 계속한다.
    }

    // 마지막 한 칸은 조립이 끝나야 채운다. 100% 인데 파일이 없는 순간을 만들지 않는다.
    job.progress = Math.floor(((index + 1) / sources.length) * 95);
  }

  try {
    job.blob = await buildStoreZip(entries);
    job.progress = 100;
    job.status = "READY";
    job.readyAt = Date.now();
  } catch (cause) {
    job.status = "FAILED";
    job.failureReason = cause instanceof Error ? cause.message : "압축에 실패했습니다";
  }
};

const isActive = (job: DownloadJob) => job.status === "QUEUED" || job.status === "RUNNING";

/** READY 로부터 보관 기간이 지났는지. 지나면 410 이고 실물도 놓아준다. */
const expireIfStale = (job: DownloadJob) => {
  if (job.status === "READY" && job.readyAt !== null && Date.now() - job.readyAt > RETENTION_MS) {
    job.status = "EXPIRED";
    job.blob = null;
  }
};

export const downloadHandlers = [
  /**
   * B-6 단건 다운로드. 바이트를 프록시하지 않고 **302 로 스토리지를 가리킨다.**
   * 목에서는 목록이 쓰는 원본 URL 이 그 자리다.
   */
  http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const roomId = Number(params.roomId);
    const denied = guardRoom(roomId, token);

    if (denied !== null) {
      return denied;
    }

    const mediaId = Number(params.mediaId);
    const media = mediaOfRoom(roomId).find((item) => item.mediaId === mediaId);

    if (media === undefined) {
      return error(404, "MEDIA_NOT_FOUND", "미디어를 찾을 수 없습니다");
    }

    if (media.status !== "READY") {
      return error(409, "MEDIA_NOT_READY", "아직 처리 중인 미디어입니다");
    }

    // 실제 서명 URL 은 Content-Disposition 을 쿼리로 싣는다. 목은 그 모양만 흉내낸다.
    const disposition = `attachment; filename="${media.fileName}"; filename*=UTF-8''${encodeURIComponent(media.fileName)}`;
    const location = `${media.originalUrl}${media.originalUrl.includes("?") ? "&" : "?"}response-content-disposition=${encodeURIComponent(disposition)}`;

    return new HttpResponse(null, { status: 302, headers: { Location: location } });
  }),

  /**
   * B-6 다건 다운로드 URL 발급. 압축하지 않고 파일마다 서명 URL 을 즉시 돌려준다.
   * 처리 중인 미디어는 대상에서 빠지므로 `files` 가 요청한 장수보다 적을 수 있다.
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const roomId = Number(params.roomId);
    const denied = guardRoom(roomId, token);

    if (denied !== null) {
      return denied;
    }

    const { mediaIds, folderId } = (await request.json()) as {
      mediaIds?: number[];
      folderId?: number;
    };

    if (mediaIds !== undefined && folderId !== undefined) {
      return error(400, "INVALID_PARAM", "mediaIds 와 folderId 는 함께 보낼 수 없습니다");
    }

    const all = mediaOfRoom(roomId);
    const chosen =
      mediaIds === undefined ? all : all.filter((media) => mediaIds.includes(media.mediaId));
    const ready = chosen.filter((media) => media.status === "READY");

    if (ready.length === 0) {
      return error(404, "MEDIA_NOT_FOUND", "받을 수 있는 미디어가 없습니다");
    }

    const files = ready.map((media) => {
      const disposition = `attachment; filename="${media.fileName}"; filename*=UTF-8''${encodeURIComponent(media.fileName)}`;

      return {
        mediaId: media.mediaId,
        fileName: media.fileName,
        // 실서버는 스토리지 서명 URL 이다. 목은 목록이 쓰는 원본 URL 로 그 자리를 대신한다.
        downloadUrl: `${media.originalUrl}${media.originalUrl.includes("?") ? "&" : "?"}response-content-disposition=${encodeURIComponent(disposition)}`,
        expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
      };
    });

    return HttpResponse.json({ data: { files } });
  }),

  /** B-7-1 zip 다운로드 요청. 압축을 기다리지 않고 잡 번호만 돌려준다. */
  http.post(`${API_BASE_URL}/rooms/:roomId/downloads/zip`, async ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const roomId = Number(params.roomId);
    const denied = guardRoom(roomId, token);

    if (denied !== null) {
      return denied;
    }

    const body = (await request.json().catch(() => ({}))) as {
      mediaIds?: number[];
      folderId?: number;
    };

    if (body.mediaIds !== undefined && body.folderId !== undefined) {
      return error(400, "INVALID_PARAM", "다운로드 조건이 올바르지 않습니다");
    }

    // 중복은 세기 전에 걷어낸다 — backend `DownloadTargetResolver` 도 distinct 뒤에 상한을 본다.
    const requestedIds = body.mediaIds === undefined ? null : [...new Set(body.mediaIds)];

    if (requestedIds !== null && requestedIds.length > MAX_MEDIA_IDS) {
      return error(
        400,
        "TOO_MANY_FILES",
        `한 번에 최대 ${MAX_MEDIA_IDS}개까지 다운로드할 수 있습니다`,
      );
    }

    if (body.folderId !== undefined && !hasFolder(roomId, body.folderId)) {
      return error(404, "FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다");
    }

    const active = [...jobs.values()].filter(
      (job) => job.requesterId === memberId && isActive(job),
    );

    if (active.length >= MAX_CONCURRENT_JOBS) {
      return error(429, "RATE_LIMITED", "요청이 너무 많습니다");
    }

    const all = mediaOfRoom(roomId);
    const scoped =
      requestedIds !== null
        ? all.filter((media) => requestedIds.includes(media.mediaId))
        : body.folderId !== undefined
          ? all.filter((media) => media.folderIds.includes(body.folderId as number))
          : all;

    // 처리 중인 미디어는 대상에서 빼고 mediaCount 에도 안 센다.
    const targets = scoped.filter((media) => media.status === "READY");

    if (targets.length === 0) {
      return error(404, "MEDIA_NOT_FOUND", "선택한 미디어를 찾을 수 없습니다");
    }

    const names = zipFileNames(targets.map((media) => media.fileName));
    const job: DownloadJob = {
      jobId: nextJobId(),
      requesterId: memberId,
      roomId,
      status: "QUEUED",
      progress: 0,
      mediaCount: targets.length,
      totalSize: targets.reduce((sum, media) => sum + media.size, 0),
      fileName: zipNameOf(roomCodeOfId(roomId) ?? String(roomId)),
      blob: null,
      readyAt: null,
      failureReason: null,
    };

    jobs.set(job.jobId, job);

    // 응답을 막지 않는다. 실제 서버도 이벤트만 던지고 202 를 먼저 돌려준다.
    void runCompression(
      job,
      targets.map((media, index) => ({ name: names[index], url: media.originalUrl })),
    );

    return HttpResponse.json(
      {
        data: {
          jobId: job.jobId,
          status: job.status,
          mediaCount: job.mediaCount,
          totalSize: job.totalSize,
          fileName: job.fileName,
        },
      },
      { status: 202 },
    );
  }),

  /** B-7-2 상태 조회. READY 일 때만 `downloadUrl` 과 `expiresAt` 이 채워진다. */
  http.get(`${API_BASE_URL}/rooms/:roomId/downloads/zip/:jobId`, ({ request, params }) => {
    const token = request.headers.get("Authorization");
    const memberId = memberIdOf(token);

    if (token === null || memberId === null) {
      return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    const job = jobs.get(String(params.jobId));

    if (job === undefined) {
      return error(404, "DOWNLOAD_NOT_FOUND", "다운로드 요청을 찾을 수 없습니다");
    }

    if (job.requesterId !== memberId) {
      return error(403, "FORBIDDEN", "본인이 요청한 다운로드가 아닙니다");
    }

    expireIfStale(job);

    if (job.status === "EXPIRED") {
      return error(410, "DOWNLOAD_EXPIRED", "다운로드 기한이 지났습니다");
    }

    const ready = job.status === "READY";

    return HttpResponse.json({
      data: {
        jobId: job.jobId,
        status: job.status,
        progress: job.progress,
        mediaCount: job.mediaCount,
        fileName: job.fileName,
        // 조회할 때마다 새로 서명한다 — 값이 매번 달라지는 것까지 흉내낸다.
        downloadUrl: ready ? `${ZIP_HOST}/${job.jobId}.zip?sig=${Date.now().toString(36)}` : null,
        expiresAt: ready ? new Date((job.readyAt ?? 0) + RETENTION_MS).toISOString() : null,
        failureReason: job.failureReason,
      },
    });
  }),

  /**
   * 영상 원본 자리 (`originalUrlOf` 가 가리키는 `cdn.example.com`).
   *
   * **가로채지 않으면 영상 받기가 항상 실패한다.** 존재하지 않는 호스트라 요청이
   * 통째로 막히고, 프론트에는 `status: 0` 으로만 보여서 "네트워크를 확인하세요" 가 뜬다.
   * 목이 서버 역할을 하는 동안은 여기도 바이트를 내줘야 흐름이 이어진다.
   *
   * 사진은 `picsum.photos` 가 실제로 응답하므로 가로챌 것이 없다.
   */
  http.get("https://cdn.example.com/rooms/:roomId/:fileName", ({ params }) => {
    const fileName = String(params.fileName);

    // 진짜 영상일 필요는 없다. 받아지는지와 이름이 붙는지만 확인하면 된다.
    return new HttpResponse(new Blob([new Uint8Array(1024)], { type: "video/mp4" }), {
      headers: {
        "Content-Type": "video/mp4",
        "Content-Disposition": `attachment; filename="${fileName}"`,
      },
    });
  }),

  /** 서명 URL 자리. 조립해둔 zip 을 그대로 내준다. */
  http.get(`${ZIP_HOST}/:fileName`, ({ params }) => {
    const jobId = String(params.fileName).replace(/\.zip$/, "");
    const job = jobs.get(jobId);

    if (job?.blob == null) {
      return new HttpResponse(null, { status: 404 });
    }

    return new HttpResponse(job.blob, {
      headers: {
        "Content-Type": "application/zip",
        "Content-Disposition": `attachment; filename="${job.fileName}"`,
      },
    });
  }),
];

export const resetDownloadJobs = () => {
  jobs.clear();
  jobSequence = 0;
};
