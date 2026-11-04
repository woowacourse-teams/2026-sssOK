import { apiClient } from "@/shared/api";
import type { OrganizeCurrent, OrganizeJob, OrganizeKind, OrganizeResult } from "./types";

const ROUTE = "/rooms/:roomId/organize-jobs";
const basePath = (roomId: number) => `/rooms/${roomId}/organize-jobs`;

interface RoomParams {
  roomId: number;
  token: string;
}

interface JobParams extends RoomParams {
  jobId: string;
}

/**
 * 접수·재시도에 붙이는 중복 방지 키. 영숫자·`-`·`_` 16~64자.
 * 같은 요청을 다시 보낼 때는 같은 키를, 내용이 바뀌면 새 키를 쓴다.
 */
export const createIdempotencyKey = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;

const jsonHeaders = (idempotencyKey: string) => ({
  "Content-Type": "application/json",
  "Idempotency-Key": idempotencyKey,
});

/** 정리 종류를 직접 골라 접수한다. 202 + QUEUED Job. */
export const startOrganizeJob = ({
  roomId,
  token,
  kind,
  idempotencyKey,
}: RoomParams & { kind: OrganizeKind; idempotencyKey: string }) =>
  apiClient<OrganizeJob>(basePath(roomId), {
    method: "POST",
    token,
    headers: jsonHeaders(idempotencyKey),
    body: JSON.stringify({ kind }),
    errorTracking: { level: "error", operation: "organize.start", route: ROUTE },
  });

/** 입력한 문장을 서버가 해석해 접수한다. 해석에 실패하면 작업 없이 422 다. */
export const startOrganizeJobByText = ({
  roomId,
  token,
  text,
  idempotencyKey,
}: RoomParams & { text: string; idempotencyKey: string }) =>
  apiClient<OrganizeJob>(`${basePath(roomId)}/by-text`, {
    method: "POST",
    token,
    headers: jsonHeaders(idempotencyKey),
    body: JSON.stringify({ text }),
    errorTracking: { level: "error", operation: "organize.start_text", route: `${ROUTE}/by-text` },
  });

/** 방이 사용 중인지와 내 작업을 함께 읽는다. 작업이 없어도 200 이다. */
export const getCurrentOrganizeJob = ({ roomId, token }: RoomParams) =>
  apiClient<OrganizeCurrent>(`${basePath(roomId)}/current`, {
    token,
    errorTracking: { level: "warning", operation: "organize.current", route: `${ROUTE}/current` },
  });

export const getOrganizeJob = ({ roomId, token, jobId }: JobParams) =>
  apiClient<OrganizeJob>(`${basePath(roomId)}/${jobId}`, {
    token,
    errorTracking: { level: "warning", operation: "organize.get", route: `${ROUTE}/:jobId` },
  });

export const getOrganizeResult = ({ roomId, token, jobId }: JobParams) =>
  apiClient<OrganizeResult>(`${basePath(roomId)}/${jobId}/result`, {
    token,
    errorTracking: {
      level: "warning",
      operation: "organize.result",
      route: `${ROUTE}/:jobId/result`,
    },
  });

/** 분석을 멈추거나 완료된 결과의 검토를 포기한다. 이미 끝난 작업이면 그 상태를 그대로 돌려준다. */
export const cancelOrganizeJob = ({ roomId, token, jobId }: JobParams) =>
  apiClient<OrganizeJob>(`${basePath(roomId)}/${jobId}/cancel`, {
    method: "POST",
    token,
    errorTracking: {
      level: "warning",
      operation: "organize.cancel",
      route: `${ROUTE}/:jobId/cancel`,
    },
  });

/** 실패한 작업을 같은 jobId 로 이어서 다시 돌린다. 작업당 최대 3회. */
export const retryOrganizeJob = ({
  roomId,
  token,
  jobId,
  idempotencyKey,
}: JobParams & { idempotencyKey: string }) =>
  apiClient<OrganizeJob>(`${basePath(roomId)}/${jobId}/retry`, {
    method: "POST",
    token,
    headers: { "Idempotency-Key": idempotencyKey },
    errorTracking: { level: "error", operation: "organize.retry", route: `${ROUTE}/:jobId/retry` },
  });
