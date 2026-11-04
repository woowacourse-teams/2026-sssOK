import type { MediaItem } from "@/entities/media";

/**
 * 사진 정리 작업 계약 (2026-11-04 API 명세서).
 *
 * 접수(POST) → 방 SSE `organize.job.updated` 로 진행·완료 → 결과 조회 → 반영 또는 취소.
 * 정기 폴링은 하지 않는다. 화면에 다시 들어오거나 SSE 가 다시 붙으면 `/current` 를 한 번 읽는다.
 */

export type OrganizeKind = "PERSON" | "SIMILAR";

export type OrganizeJobStatus =
  "QUEUED" | "RUNNING" | "READY" | "FAILED" | "CANCELED" | "APPLIED" | "EXPIRED";

/**
 * 단계별 진행. `processed`·`total` 은 **그 단계 기준** 처리 수라 전체 완료율이 아니다.
 * `stage` 는 분석 서버가 주는 단계 이름이다. 전체 진행률은 `overallPercentOf` 가 단계 순서로 계산한다.
 */
export interface OrganizeProgress {
  stage: string | null;
  processed: number | null;
  total: number | null;
}

/** 작업 상태가 바뀔 때마다 오는 필드. 접수·조회 응답과 SSE 가 함께 쓴다. */
export interface OrganizeJobState {
  jobId: string;
  status: OrganizeJobStatus;
  /** 작업이 갱신될 때마다 오른다. 이보다 낮은 이벤트·스냅샷은 버린다. */
  jobVersion: number;
  attempt: number;
  progress?: OrganizeProgress;
  failureCode?: string;
  /** 지금 다시 시도할 의미가 있는지. 서버가 호출 때 다시 판단한다. */
  retryable: boolean;
  retryAvailableAt: string | null;
  /** 분석 완료 +30분. 이때까지 반영하거나 포기하지 않으면 EXPIRED 가 된다. */
  reviewExpiresAt: string | null;
  resultExpiresAt: string | null;
}

/** 공통 Job 응답. */
export interface OrganizeJob extends OrganizeJobState {
  kind: OrganizeKind;
  /** 확정된 분석 대상 수. */
  photoCount: number;
  excludedCount: number;
  createdAt: string;
  updatedAt: string;
}

/** `GET /current`. 다른 사람의 작업은 공개하지 않아 `roomBusy` 로만 알 수 있다. */
export interface OrganizeCurrent {
  roomBusy: boolean;
  roomVersion: number;
  myJob: OrganizeJob | null;
}

/** 방 SSE `organize.room.changed`. 방 멤버 모두가 받는다. */
export interface OrganizeRoomChangedEvent {
  roomId: number;
  roomVersion: number;
  busy: boolean;
}

/** 방 SSE `organize.job.updated`. 작업을 시작한 사람만 받는다. */
export type OrganizeJobUpdatedEvent = OrganizeJobState;

export interface OrganizeGroup {
  groupId: string | null;
  photoCount: number;
  photos: MediaItem[];
}

/** `GET /{jobId}/result`. 지금은 찾은 사람 수(그룹 수)만 쓴다. */
export interface OrganizeResult {
  jobId: string;
  kind: OrganizeKind;
  resultVersion: number;
  photoCount: number;
  removedCount: number;
  groups: OrganizeGroup[];
  unassigned: OrganizeGroup;
  errors: { mediaId: number; code: string }[];
  smallGroups?: OrganizeGroup[];
}

/** 반영 요청의 한 그룹. 폴더 하나가 된다. */
export interface OrganizeApplyGroup {
  groupId: string;
  folderName: string;
  mediaIds: number[];
}

/** `POST /{jobId}/apply`. 만든 폴더를 요청한 그룹 순서대로 돌려준다. */
export interface OrganizeApplied {
  jobId: string;
  status: OrganizeJobStatus;
  folders: { folderId: number; folderName: string; mediaIds: number[] }[];
}

export const isActiveStatus = (status: OrganizeJobStatus) =>
  status === "QUEUED" || status === "RUNNING";
