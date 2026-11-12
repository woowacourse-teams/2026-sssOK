import { z } from "zod";
import { MediaItemSchema } from "../../../entities/media/model/types";
export const OrganizeKindSchema = z.union([z.literal("PERSON"), z.literal("SIMILAR")]);
export const OrganizeJobStatusSchema = z.union([
  z.literal("QUEUED"),
  z.literal("RUNNING"),
  z.literal("READY"),
  z.literal("FAILED"),
  z.literal("CANCELED"),
  z.literal("APPLIED"),
  z.literal("EXPIRED"),
]);
export const OrganizeProgressSchema = z.object({
  stage: z.union([z.string(), z.null()]),
  processed: z.union([z.number(), z.null()]),
  total: z.union([z.number(), z.null()]),
  /** 센 대상(계약 #704). 사진이면 PHOTO, 검출된 사람(몸)이면 BODY. 진행률 계산에는 쓰지 않는다. */
  unit: z.union([z.literal("PHOTO"), z.literal("BODY"), z.null()]).optional(),
});
export const OrganizeJobStateSchema = z.object({
  jobId: z.string(),
  status: OrganizeJobStatusSchema,
  /** 작업이 갱신될 때마다 오른다. 이보다 낮은 이벤트·스냅샷은 버린다. */
  jobVersion: z.number(),
  attempt: z.number(),
  progress: OrganizeProgressSchema.optional(),
  failureCode: z.string().optional(),
  /** 지금 다시 시도할 의미가 있는지. 서버가 호출 때 다시 판단한다. */
  retryable: z.boolean(),
  retryAvailableAt: z.union([z.string(), z.null()]),
  /** 분석 완료 +30분. 이때까지 반영하거나 포기하지 않으면 EXPIRED 가 된다. */
  reviewExpiresAt: z.union([z.string(), z.null()]),
  resultExpiresAt: z.union([z.string(), z.null()]),
});
export const OrganizeJobSchema = OrganizeJobStateSchema.extend(
  z.object({
    kind: OrganizeKindSchema,
    /** 확정된 분석 대상 수. */
    photoCount: z.number(),
    excludedCount: z.number(),
    createdAt: z.string(),
    updatedAt: z.string(),
  }).shape,
);
export const OrganizeCurrentSchema = z.object({
  roomBusy: z.boolean(),
  roomVersion: z.number(),
  myJob: z.union([OrganizeJobSchema, z.null()]),
});
export const OrganizeGroupSchema = z.object({
  groupId: z.union([z.string(), z.null()]),
  photoCount: z.number(),
  photos: z.array(MediaItemSchema),
});
export const OrganizeResultSchema = z.object({
  jobId: z.string(),
  kind: OrganizeKindSchema,
  resultVersion: z.number(),
  photoCount: z.number(),
  removedCount: z.number(),
  groups: z.array(OrganizeGroupSchema),
  unassigned: OrganizeGroupSchema,
  errors: z.array(z.object({ mediaId: z.number(), code: z.string() })),
  smallGroups: z.array(OrganizeGroupSchema).optional(),
});
export const OrganizeAppliedSchema = z.object({
  jobId: z.string(),
  status: OrganizeJobStatusSchema,
  folders: z.array(
    z.object({ folderId: z.number(), folderName: z.string(), mediaIds: z.array(z.number()) }),
  ),
});

/**
 * 사진 정리 작업 계약 (2026-11-04 API 명세서).
 *
 * 접수(POST) → 방 SSE `organize.job.updated` 로 진행·완료 → 결과 조회 → 반영 또는 취소.
 * 정기 폴링은 하지 않는다. 화면에 다시 들어오거나 SSE 가 다시 붙으면 `/current` 를 한 번 읽는다.
 */

export type OrganizeKind = z.infer<typeof OrganizeKindSchema>;

export type OrganizeJobStatus = z.infer<typeof OrganizeJobStatusSchema>;

/**
 * 단계별 진행. `processed`·`total` 은 **그 단계 기준** 처리 수라 전체 완료율이 아니다.
 * `stage` 는 분석 서버가 주는 단계 이름이다. 전체 진행률은 `overallPercentOf` 가 단계 순서로 계산한다.
 */
export type OrganizeProgress = z.infer<typeof OrganizeProgressSchema>;

/** 작업 상태가 바뀔 때마다 오는 필드. 접수·조회 응답과 SSE 가 함께 쓴다. */
export type OrganizeJobState = z.infer<typeof OrganizeJobStateSchema>;

/** 공통 Job 응답. */
export type OrganizeJob = z.infer<typeof OrganizeJobSchema>;

/** `GET /current`. 다른 사람의 작업은 공개하지 않아 `roomBusy` 로만 알 수 있다. */
export type OrganizeCurrent = z.infer<typeof OrganizeCurrentSchema>;

/** 방 SSE `organize.room.changed`. 방 멤버 모두가 받는다. */
export interface OrganizeRoomChangedEvent {
  roomId: number;
  roomVersion: number;
  busy: boolean;
}

/** 방 SSE `organize.job.updated`. 작업을 시작한 사람만 받는다. */
export type OrganizeJobUpdatedEvent = OrganizeJobState;

export type OrganizeGroup = z.infer<typeof OrganizeGroupSchema>;

/** `GET /{jobId}/result`. 지금은 찾은 사람 수(그룹 수)만 쓴다. */
export type OrganizeResult = z.infer<typeof OrganizeResultSchema>;

/** 반영 요청의 한 그룹. 폴더 하나가 된다. */
export interface OrganizeApplyGroup {
  groupId: string;
  folderName: string;
  mediaIds: number[];
}

/** `POST /{jobId}/apply`. 만든 폴더를 요청한 그룹 순서대로 돌려준다. */
export type OrganizeApplied = z.infer<typeof OrganizeAppliedSchema>;

export const isActiveStatus = (status: OrganizeJobStatus): boolean =>
  status === "QUEUED" || status === "RUNNING";
