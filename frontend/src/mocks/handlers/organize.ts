import { http, HttpResponse, sse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import { createFolderWithMedia, hasFolderNamed, hasJoinedRoom, mediaOfRoom } from "./room";

/**
 * 사진 정리 작업 목 (2026-11-04 API 명세서).
 *
 * - POST /organize-jobs, /organize-jobs/by-text → 202 QUEUED Job
 * - GET  /organize-jobs/current → { roomBusy, roomVersion, myJob }
 * - GET  /organize-jobs/:jobId, /:jobId/result
 * - POST /organize-jobs/:jobId/cancel, /:jobId/retry, /:jobId/apply
 * - GET  /rooms/:roomId/events → organize.room.changed · organize.job.updated (브라우저만)
 *
 * 결과는 `localStorage["mock-organize-scenario"]` 로 바꿔 본다: ok(기본)·fail·empty.
 * 진행은 시간으로 계산한다. 이벤트를 놓쳐도 GET 이 같은 상태를 준다.
 */

const LIMIT = 200;
const QUEUE_MS = 1200;
const PER_PHOTO_MS = 120;
const PEOPLE_LIMIT = 6;
const MAX_RETRIES = 3;

type Status = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "CANCELED" | "APPLIED" | "EXPIRED";
type Scenario = "ok" | "fail" | "empty";

interface MockJob {
  jobId: string;
  roomId: number;
  requesterId: number;
  kind: "PERSON" | "SIMILAR";
  photoIds: number[];
  startedAt: number;
  createdAt: string;
  attempt: number;
  scenario: Scenario;
  canceledVersion: number | null;
  appliedVersion: number | null;
}

const jobs = new Map<string, MockJob>();
let sequence = 0;

const TOKEN_PATTERN = /^(?:Bearer )?mock-token-(\d+)$/;
const memberIdOf = (token: string | null) => {
  const matched = token === null ? null : TOKEN_PATTERN.exec(token);
  return matched === null ? null : Number(matched[1]);
};

const error = (status: number, code: string, message: string) =>
  HttpResponse.json({ code, message }, { status });

const scenarioOf = (): Scenario => {
  try {
    const value = localStorage.getItem("mock-organize-scenario");
    return value === "fail" || value === "empty" ? value : "ok";
  } catch {
    return "ok";
  }
};

/**
 * 실제 분석 서버처럼 단계마다 0부터 다시 센다. 앞 절반은 얼굴 찾기, 뒤 절반은 사람(몸) 찾기다.
 * 진행 바가 이 숫자를 그대로 쓰지 않고 전체 진행률로 바꾸는지 손으로 확인할 수 있다.
 */
const progressOf = (processed: number, total: number) => {
  const half = Math.ceil(total / 2);
  return processed < half
    ? { stage: "detecting_faces", processed: processed * 2, total }
    : { stage: "detecting_bodies", processed: (processed - half) * 2, total };
};

/** 지금 시각의 작업 상태. 버전은 상태·처리 수가 바뀔 때마다 커지도록 계산한다. */
const snapshot = (job: MockJob, now = Date.now()) => {
  const total = job.photoIds.length;
  const elapsed = now - job.startedAt;
  const stopAt = job.scenario === "fail" ? Math.round(total * 0.55) : total;
  const processed = Math.max(0, Math.min(stopAt, Math.floor((elapsed - QUEUE_MS) / PER_PHOTO_MS)));
  const base = (job.attempt - 1) * 10_000;

  let status: Status;
  let jobVersion: number;
  if (job.canceledVersion !== null) {
    status = "CANCELED";
    jobVersion = job.canceledVersion;
  } else if (job.appliedVersion !== null) {
    status = "APPLIED";
    jobVersion = job.appliedVersion;
  } else if (elapsed < QUEUE_MS) {
    status = "QUEUED";
    jobVersion = base + 1;
  } else if (processed < stopAt) {
    status = "RUNNING";
    jobVersion = base + 2 + processed;
  } else {
    status = job.scenario === "fail" ? "FAILED" : "READY";
    jobVersion = base + 3 + total;
  }

  const finishedAt = job.startedAt + QUEUE_MS + stopAt * PER_PHOTO_MS;
  const isReady = status === "READY";

  return {
    jobId: job.jobId,
    status,
    kind: job.kind,
    photoCount: total,
    excludedCount: 0,
    attempt: job.attempt,
    jobVersion,
    ...(status === "RUNNING" ? { progress: progressOf(processed, total) } : {}),
    ...(status === "FAILED" ? { failureCode: "ANALYSIS_FAILED" } : {}),
    retryable: status === "FAILED" && job.attempt <= MAX_RETRIES,
    retryAvailableAt: null,
    reviewExpiresAt: isReady ? new Date(finishedAt + 30 * 60_000).toISOString() : null,
    resultExpiresAt: isReady ? new Date(finishedAt + 3 * 24 * 60 * 60_000).toISOString() : null,
    createdAt: job.createdAt,
    updatedAt: new Date(Math.min(now, finishedAt)).toISOString(),
  };
};

/** READY 는 검토 기한 동안 방을 점유한다. */
const holdsRoom = (job: MockJob) => {
  const { status } = snapshot(job);
  return status === "QUEUED" || status === "RUNNING" || status === "READY";
};

const roomJobs = (roomId: number) => [...jobs.values()].filter((job) => job.roomId === roomId);
const roomBusyOf = (roomId: number) => roomJobs(roomId).some(holdsRoom);
/** 방 점유가 바뀔 때마다 오르는 버전. 목은 작업마다 점유 시작·해제를 한 번씩 센다. */
const roomVersionOf = (roomId: number) =>
  roomJobs(roomId).reduce((sum, job) => sum + (holdsRoom(job) ? 1 : 2), 0);

const guard = (request: Request, roomId: number) => {
  const token = request.headers.get("Authorization");
  const memberId = memberIdOf(token);
  if (token === null || memberId === null) {
    return { denied: error(401, "UNAUTHORIZED", "인증이 필요합니다.") };
  }
  if (!hasJoinedRoom(token, roomId)) {
    return { denied: error(403, "NOT_ROOM_MEMBER", "입장한 방에서만 이용할 수 있습니다") };
  }
  return { memberId };
};

/** 없는 작업·다른 방·다른 사람의 작업은 모두 404 다. */
const myJobOf = (request: Request, roomId: number, jobId: string) => {
  const checked = guard(request, roomId);
  if ("denied" in checked) return { denied: checked.denied };
  const job = jobs.get(jobId);
  if (!job || job.roomId !== roomId || job.requesterId !== checked.memberId) {
    return { denied: error(404, "ORGANIZE_JOB_NOT_FOUND", "사진 정리 작업을 찾을 수 없습니다") };
  }
  return { job };
};

const startJob = (request: Request, roomId: number, kind: MockJob["kind"]) => {
  const checked = guard(request, roomId);
  if ("denied" in checked) return checked.denied;
  if (!request.headers.get("Idempotency-Key")) {
    return error(400, "INVALID_PARAM", "요청 값이 올바르지 않습니다");
  }
  if (roomBusyOf(roomId)) {
    return error(409, "ORGANIZE_JOB_IN_PROGRESS", "이 방에서 이미 사진 정리가 진행 중입니다");
  }

  const photoIds = mediaOfRoom(roomId)
    .filter((media) => media.type === "IMAGE")
    .map((media) => media.mediaId);
  if (photoIds.length === 0) {
    return error(422, "ORGANIZE_NO_ANALYZABLE_PHOTOS", "정리할 수 있는 사진이 없습니다");
  }
  if (photoIds.length > LIMIT) {
    return error(
      422,
      "ORGANIZE_TOO_MANY_PHOTOS",
      `정리할 사진이 너무 많습니다. 현재 ${photoIds.length}장, 최대 200장까지 정리할 수 있습니다`,
    );
  }

  sequence += 1;
  const job: MockJob = {
    jobId: `00000000-0000-4000-8000-${sequence.toString().padStart(12, "0")}`,
    roomId,
    requesterId: checked.memberId,
    kind,
    photoIds,
    startedAt: Date.now(),
    createdAt: new Date().toISOString(),
    attempt: 1,
    scenario: scenarioOf(),
    canceledVersion: null,
    appliedVersion: null,
  };
  jobs.set(job.jobId, job);

  return HttpResponse.json({ data: snapshot(job) }, { status: 202 });
};

const BASE = `${API_BASE_URL}/rooms/:roomId/organize-jobs`;

export const resetOrganizeJobs = () => {
  jobs.clear();
  sequence = 0;
};

export const organizeHandlers = [
  http.post(BASE, async ({ request, params }) => {
    const body = (await request.json().catch(() => ({}))) as { kind?: string };
    if (body.kind !== "PERSON" && body.kind !== "SIMILAR") {
      return error(400, "INVALID_PARAM", "요청 값이 올바르지 않습니다");
    }
    return startJob(request, Number(params.roomId), body.kind);
  }),

  http.post(`${BASE}/by-text`, async ({ request, params }) => {
    const body = (await request.json().catch(() => ({}))) as { text?: string };
    const text = body.text?.trim() ?? "";
    if (text.length === 0 || text.length > 1000) {
      return error(400, "INVALID_PARAM", "요청 값이 올바르지 않습니다");
    }
    if (!/사람|인물|얼굴|친구/.test(text)) {
      return error(
        422,
        "ORGANIZE_NEEDS_CLARIFICATION",
        "어떤 정리를 원하는지 더 구체적으로 알려주세요",
      );
    }
    return startJob(request, Number(params.roomId), "PERSON");
  }),

  http.get(`${BASE}/current`, ({ request, params }) => {
    const roomId = Number(params.roomId);
    const checked = guard(request, roomId);
    if ("denied" in checked) return checked.denied;

    const mine = roomJobs(roomId).filter((job) => job.requesterId === checked.memberId);
    const myJob = mine.find(holdsRoom) ?? mine[mine.length - 1];
    return HttpResponse.json({
      data: {
        roomBusy: roomBusyOf(roomId),
        roomVersion: roomVersionOf(roomId),
        myJob: myJob ? snapshot(myJob) : null,
      },
    });
  }),

  http.get(`${BASE}/:jobId/result`, ({ request, params }) => {
    const found = myJobOf(request, Number(params.roomId), String(params.jobId));
    if ("denied" in found) return found.denied;

    const { job } = found;
    if (snapshot(job).status !== "READY") {
      return error(409, "ORGANIZE_JOB_NOT_READY", "아직 검토할 수 있는 결과가 없습니다");
    }

    const media = mediaOfRoom(job.roomId).filter((item) => job.photoIds.includes(item.mediaId));
    const groupCount = job.scenario === "empty" ? 0 : Math.min(PEOPLE_LIMIT, media.length);
    const groups = Array.from({ length: groupCount }, (_, index) => {
      const photos = media.filter((__, photoIndex) => photoIndex % groupCount === index);
      return { groupId: `person_${index + 1}`, photoCount: photos.length, photos };
    });

    return HttpResponse.json({
      data: {
        jobId: job.jobId,
        kind: job.kind,
        resultVersion: 1,
        photoCount: job.photoIds.length,
        removedCount: 0,
        groups,
        unassigned: {
          groupId: null,
          photoCount: groupCount ? 0 : media.length,
          photos: groupCount ? [] : media,
        },
        errors: [],
      },
    });
  }),

  http.get(`${BASE}/:jobId`, ({ request, params }) => {
    const found = myJobOf(request, Number(params.roomId), String(params.jobId));
    return "denied" in found ? found.denied : HttpResponse.json({ data: snapshot(found.job) });
  }),

  http.post(`${BASE}/:jobId/cancel`, ({ request, params }) => {
    const found = myJobOf(request, Number(params.roomId), String(params.jobId));
    if ("denied" in found) return found.denied;

    const { job } = found;
    const current = snapshot(job);
    if (current.status === "QUEUED" || current.status === "RUNNING" || current.status === "READY") {
      job.canceledVersion = current.jobVersion + 1;
    }
    return HttpResponse.json({ data: snapshot(job) });
  }),

  /** 전부 만들거나 하나도 만들지 않는다. 같은 이름 폴더가 이미 있으면 409 다. */
  http.post(`${BASE}/:jobId/apply`, async ({ request, params }) => {
    const found = myJobOf(request, Number(params.roomId), String(params.jobId));
    if ("denied" in found) return found.denied;
    if (!request.headers.get("Idempotency-Key")) {
      return error(400, "INVALID_PARAM", "요청 값이 올바르지 않습니다");
    }

    const { job } = found;
    const current = snapshot(job);
    if (current.status === "APPLIED") {
      return error(409, "ORGANIZE_ALREADY_APPLIED", "이미 폴더에 반영된 작업입니다");
    }
    if (current.status !== "READY") {
      return error(409, "ORGANIZE_JOB_NOT_READY", "아직 검토할 수 있는 결과가 없습니다");
    }

    const { groups } = (await request.json().catch(() => ({}))) as {
      groups?: { groupId: string; folderName: string; mediaIds: number[] }[];
    };
    if (
      !groups?.length ||
      groups.some((group) => !group.folderName?.trim() || !group.mediaIds?.length)
    ) {
      return error(400, "INVALID_PARAM", "그룹 ID, 폴더 이름, 사진을 모두 입력해주세요");
    }
    const names = groups.map((group) => group.folderName.trim());
    if (
      new Set(names).size !== names.length ||
      names.some((name) => hasFolderNamed(job.roomId, name))
    ) {
      return error(
        409,
        "ORGANIZE_FOLDER_NAME_CONFLICT",
        "같은 이름의 폴더가 있습니다. 이름을 변경해주세요",
      );
    }

    const folders = groups.map((group) => {
      const folder = createFolderWithMedia(job.roomId, group.folderName.trim(), group.mediaIds);
      return { folderId: folder!.id, folderName: folder!.name, mediaIds: group.mediaIds };
    });
    job.appliedVersion = current.jobVersion + 1;
    return HttpResponse.json({ data: { jobId: job.jobId, status: "APPLIED", folders } });
  }),

  http.post(`${BASE}/:jobId/retry`, ({ request, params }) => {
    const found = myJobOf(request, Number(params.roomId), String(params.jobId));
    if ("denied" in found) return found.denied;
    if (!request.headers.get("Idempotency-Key")) {
      return error(400, "INVALID_PARAM", "요청 값이 올바르지 않습니다");
    }

    const { job } = found;
    const current = snapshot(job);
    if (current.status === "QUEUED" || current.status === "RUNNING" || current.status === "READY") {
      return HttpResponse.json({ data: current });
    }
    if (!current.retryable) {
      return error(409, "ORGANIZE_RETRY_NOT_ALLOWED", "다시 시도할 수 없는 작업입니다");
    }

    job.attempt += 1;
    job.startedAt = Date.now();
    job.scenario = "ok";
    return HttpResponse.json({ data: snapshot(job) }, { status: 202 });
  }),
];

/**
 * 방 SSE. 지금까지 목은 방 SSE 를 흉내내지 않았다. 정리 이벤트 두 가지만 보낸다.
 * EventSource 가 없는 jest 에서는 msw 가 핸들러를 만들지 못해 브라우저 워커에만 붙인다.
 */
export const createRoomEventHandler = () =>
  sse<{ "organize.room.changed": string; "organize.job.updated": string }>(
    `${API_BASE_URL}/rooms/:roomId/events`,
    ({ client, request, params }) => {
      const roomId = Number(params.roomId);
      const memberId = memberIdOf(new URL(request.url).searchParams.get("token"));
      let eventId = 0;
      let lastRoomVersion = -1;
      const lastJobVersions = new Map<string, number>();

      const tick = () => {
        const roomVersion = roomVersionOf(roomId);
        if (roomVersion !== lastRoomVersion) {
          lastRoomVersion = roomVersion;
          eventId += 1;
          client.send({
            id: String(eventId),
            event: "organize.room.changed",
            data: JSON.stringify({ roomId, roomVersion, busy: roomBusyOf(roomId) }),
          });
        }

        // 작업 이벤트는 시작한 사람에게만 보낸다. 결과 목록·사진 수는 싣지 않는다.
        roomJobs(roomId)
          .filter((job) => job.requesterId === memberId)
          .forEach((job) => {
            const current = snapshot(job);
            if ((lastJobVersions.get(job.jobId) ?? -1) >= current.jobVersion) return;
            lastJobVersions.set(job.jobId, current.jobVersion);
            eventId += 1;
            client.send({
              id: String(eventId),
              event: "organize.job.updated",
              data: JSON.stringify({
                jobId: current.jobId,
                jobVersion: current.jobVersion,
                attempt: current.attempt,
                status: current.status,
                progress: current.progress,
                failureCode: current.failureCode,
                retryable: current.retryable,
                retryAvailableAt: current.retryAvailableAt,
                reviewExpiresAt: current.reviewExpiresAt,
                resultExpiresAt: current.resultExpiresAt,
              }),
            });
          });
      };

      // 실제 서버는 약 15초마다 진행을 보내지만, 목은 흐름을 빨리 보려고 0.5초마다 확인한다.
      const timer = setInterval(tick, 500);
      request.signal.addEventListener("abort", () => clearInterval(timer));
    },
  );
