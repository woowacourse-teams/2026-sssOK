import { useCallback, useEffect, useRef, useState } from "react";

import { isApiError } from "@/shared/api";
import {
  applyOrganizeJob,
  cancelOrganizeJob,
  createIdempotencyKey,
  getCurrentOrganizeJob,
  getOrganizeResult,
  retryOrganizeJob,
  startOrganizeJob,
  startOrganizeJobByText,
} from "../api/organizeJobApi";
import {
  isActiveStatus,
  type OrganizeApplied,
  type OrganizeApplyGroup,
  type OrganizeJob,
  type OrganizeJobState,
  type OrganizeKind,
  type OrganizeResult,
  type OrganizeRoomChangedEvent,
} from "../api/types";
import type { OrganizeLimit } from "./analyzablePhotos";
import { overallPercentOf } from "./overallProgress";
import {
  applyErrorMessage,
  cancelErrorMessage,
  isApplyClosed,
  ORGANIZE_ERROR_CODES,
  organizeErrorMessage,
  waitText,
} from "./organizeErrorMessage";

/** 버튼으로 고른 방식이거나, 입력창에 쓴 문장이다. */
export type OrganizeRequest = { kind: OrganizeKind } | { text: string };

/** 진행 바와 결과 알림이 그리는 내 작업. 취소·반영·만료된 작업은 여기 오지 않는다. */
export interface MyOrganizeJob {
  jobId: string;
  status: "QUEUED" | "RUNNING" | "READY" | "FAILED";
  /** 단계 순서로 만든 전체 진행률(0~99). 진행 정보를 아직 못 받았으면 null. 앞으로만 간다. */
  percent: number | null;
  /** 분석 대상 장수. 접수 응답을 받기 전이면 null 이다. */
  photoCount: number | null;
  retryable: boolean;
  /** READY 일 때 찾은 사람 수(결과 그룹 수). 결과를 읽기 전이나 읽지 못했으면 null. */
  peopleCount: number | null;
}

type TrackedJob = OrganizeJobState & { photoCount: number | null; percent: number | null };

interface UseOrganizeJobParams {
  roomId: number;
  token: string;
}

/**
 * 서버가 장수 때문에 거절했으면 모달로 알릴 까닭을 돌려준다.
 * 200장 초과 메시지("현재 N장, 최대 200장…")에서 장수를 읽고, 못 읽으면 장수 없이 알린다.
 */
const limitOf = (cause: unknown): OrganizeLimit | null => {
  if (!isApiError(cause)) return null;
  if (cause.code === ORGANIZE_ERROR_CODES.noAnalyzablePhotos) return { reason: "empty" };
  if (cause.code !== ORGANIZE_ERROR_CODES.tooManyPhotos) return null;
  const matched = /현재\s*(\d+)\s*장/.exec(cause.message);
  return { reason: "tooMany", count: matched ? Number(matched[1]) : null };
};

const VISIBLE_STATUSES: readonly string[] = ["QUEUED", "RUNNING", "READY", "FAILED"];

const dismissedKey = (roomId: number) => `organize-dismissed:${roomId}`;

const readDismissed = (roomId: number) => {
  try {
    return sessionStorage.getItem(dismissedKey(roomId));
  } catch {
    return null;
  }
};

const writeDismissed = (roomId: number, jobId: string) => {
  try {
    sessionStorage.setItem(dismissedKey(roomId), jobId);
  } catch {
    // 저장하지 못하면 다시 들어왔을 때 알림이 한 번 더 뜰 뿐이다.
  }
};

/**
 * 오래된 이벤트·스냅샷을 걸러 내 작업에 합친다.
 * 같은 작업이면 `jobVersion` 이 더 높을 때만 받는다. 다른 작업이면 새 작업으로 갈아탄다
 * (같은 사람이 다른 기기에서 새로 시작한 경우다).
 */
const mergeJob = (
  current: TrackedJob | null,
  next: OrganizeJobState & Partial<Pick<OrganizeJob, "photoCount">>,
): TrackedJob | null => {
  const isSameJob = current?.jobId === next.jobId;
  // 분석 대상 장수는 SSE 에 없고 접수·조회 응답에만 있다. SSE 가 접수 응답보다 먼저 와서
  // 응답이 "오래된 버전" 으로 버려져도, 장수만은 그 응답에서 채운다.
  const photoCount = next.photoCount ?? (isSameJob ? current.photoCount : null);

  if (current && isSameJob && next.jobVersion <= current.jobVersion) {
    return photoCount === current.photoCount ? current : { ...current, photoCount };
  }
  // 같은 실행 회차 안에서는 진행률이 줄지 않는다. 다시 시도해 새 회차가 되면 처음부터 센다.
  const previous =
    current && isSameJob && current.attempt === next.attempt ? current.percent : null;
  return { ...next, photoCount, percent: overallPercentOf(next.progress, previous) };
};

/**
 * 방의 사진 정리 작업을 따라간다 (2026-11-04 API 명세서).
 *
 * - 진행·완료는 방 SSE 이벤트로만 받는다. 부르는 쪽이 `applyJobUpdate`·`applyRoomChange` 에 넘긴다.
 * - 정기 폴링은 하지 않는다. 들어올 때와 SSE 가 다시 붙었을 때(`resync`)만 `/current` 를 읽는다.
 * - 진행 바와 결과 알림은 시작한 사람에게만 보인다. 다른 사람의 작업은 `roomBusy` 로만 알 수 있어서
 *   이름 없이 "다른 사람이 정리하고 있어요" 로 막는다.
 */
export const useOrganizeJob = ({ roomId, token }: UseOrganizeJobParams) => {
  const [job, setJob] = useState<TrackedJob | null>(null);
  const [room, setRoom] = useState({ busy: false, version: -1 });
  /** READY 작업의 검토용 결과. 읽지 못했으면 `result` 가 null 이다. */
  const [loaded, setLoaded] = useState<{ jobId: string; result: OrganizeResult | null } | null>(
    null,
  );
  const [resultAttempt, setResultAttempt] = useState(0);
  const [dismissedJobId, setDismissedJobId] = useState(() => readDismissed(roomId));
  const [error, setError] = useState<string | null>(null);
  const [limit, setLimit] = useState<OrganizeLimit | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const isStartingRef = useRef(false);
  const [isApplying, setIsApplying] = useState(false);
  const isApplyingRef = useRef(false);
  const [applyError, setApplyError] = useState<string | null>(null);
  /** 같은 내용을 다시 보낼 때는 같은 키를 써서, 응답만 놓친 반영이 두 번 일어나지 않게 한다. */
  const applyKeyRef = useRef<{ body: string; key: string } | null>(null);

  const applyJob = useCallback(
    (next: OrganizeJobState & Partial<Pick<OrganizeJob, "photoCount">>) =>
      setJob((current) => mergeJob(current, next)),
    [],
  );

  const applyRoomChange = useCallback(({ roomVersion, busy }: OrganizeRoomChangedEvent) => {
    setRoom((current) =>
      roomVersion >= current.version ? { busy, version: roomVersion } : current,
    );
  }, []);

  /**
   * 방 SSE `organize.job.updated`. 필드 해석은 이 기능이 맡는다.
   * 방 SSE 쪽은 이벤트 모양을 모르므로 받은 그대로 넘겨받는다.
   */
  const applyJobUpdate = useCallback(
    (event: OrganizeJobState | { jobId: string; jobVersion: number; status: string }) =>
      applyJob(event as OrganizeJobState),
    [applyJob],
  );

  const readCurrent = useCallback(
    () =>
      getCurrentOrganizeJob({ roomId, token }).then(
        (current) => () => {
          applyRoomChange({ roomId, roomVersion: current.roomVersion, busy: current.roomBusy });
          if (current.myJob) applyJob(current.myJob);
        },
        // 다시 읽지 못해도 새로 시작하는 데는 지장이 없다. 다음 이벤트로 따라잡는다.
        () => () => undefined,
      ),
    [applyJob, applyRoomChange, roomId, token],
  );

  /** SSE 가 다시 붙었을 때 그사이 놓친 상태를 한 번 읽는다. 정기 폴링은 하지 않는다. */
  const resync = useCallback(async () => (await readCurrent())(), [readCurrent]);

  // 들어올 때 현재 작업을 읽어 진행 바·알림을 이어서 보여 준다.
  useEffect(() => {
    let isCancelled = false;
    void readCurrent().then((apply) => {
      if (!isCancelled) apply();
    });
    return () => {
      isCancelled = true;
    };
  }, [readCurrent]);

  // 분석이 끝나면 결과를 읽는다. 찾은 사람 수(그룹 수)가 0이면 "사람 없음" 알림이고,
  // 아니면 같은 결과로 검토 화면을 그린다.
  const readyJobId = job?.status === "READY" ? job.jobId : null;
  useEffect(() => {
    if (!readyJobId) return;
    let isCancelled = false;
    getOrganizeResult({ roomId, token, jobId: readyJobId })
      .then((result) => {
        if (!isCancelled) setLoaded({ jobId: readyJobId, result });
      })
      .catch(() => {
        if (!isCancelled) setLoaded({ jobId: readyJobId, result: null });
      });
    return () => {
      isCancelled = true;
    };
  }, [readyJobId, resultAttempt, roomId, token]);

  const start = useCallback(
    async (request: OrganizeRequest) => {
      if (isStartingRef.current) return;
      isStartingRef.current = true;
      setIsStarting(true);
      setError(null);
      const idempotencyKey = createIdempotencyKey();

      try {
        const accepted =
          "kind" in request
            ? await startOrganizeJob({ roomId, token, kind: request.kind, idempotencyKey })
            : await startOrganizeJobByText({ roomId, token, text: request.text, idempotencyKey });
        // 방 점유는 건드리지 않는다. 내 작업이 도는 동안은 isRunning 으로 막히고,
        // 버전 없이 바꾸면 응답보다 먼저 온 점유 해제 이벤트를 덮어쓴다.
        applyJob(accepted);
      } catch (cause) {
        const rejectedBy = limitOf(cause);
        if (rejectedBy) setLimit(rejectedBy);
        else setError(organizeErrorMessage(cause));
        if (isApiError(cause) && cause.code === ORGANIZE_ERROR_CODES.inProgress) {
          void resync();
        }
      } finally {
        isStartingRef.current = false;
        setIsStarting(false);
      }
    },
    [applyJob, resync, roomId, token],
  );

  /** 실패한 작업을 같은 jobId 로 다시 돌린다. 기다려야 하면 남은 시간을 알린다. */
  const retry = useCallback(async () => {
    if (!job || job.status !== "FAILED") return;
    const waitMs = job.retryAvailableAt ? Date.parse(job.retryAvailableAt) - Date.now() : 0;
    if (waitMs > 0) {
      setError(`${waitText(waitMs / 1000)} 뒤에 다시 시도할 수 있어요.`);
      return;
    }

    setError(null);
    try {
      applyJob(
        await retryOrganizeJob({
          roomId,
          token,
          jobId: job.jobId,
          idempotencyKey: createIdempotencyKey(),
        }),
      );
    } catch (cause) {
      setError(organizeErrorMessage(cause));
    }
  }, [applyJob, job, roomId, token]);

  const hideNotice = useCallback(
    (jobId: string) => {
      writeDismissed(roomId, jobId);
      setDismissedJobId(jobId);
    },
    [roomId],
  );

  /**
   * 내 작업을 `cancel` 로 끝내 방을 풀어 준다. 화면은 먼저 닫고, 서버 응답으로 상태를 맞춘다.
   * 방 점유 이벤트가 오기 전에도 '자동으로 정리하기' 가 막혀 보이지 않게 점유를 먼저 푼다.
   */
  const release = useCallback(
    async (failureMessage: string) => {
      if (!job) return;
      const { jobId } = job;
      setJob(null);
      setRoom((current) => ({ ...current, busy: false }));
      setError(null);

      try {
        applyJob(await cancelOrganizeJob({ roomId, token, jobId }));
      } catch (cause) {
        setError(cancelErrorMessage(cause, failureMessage));
        void resync();
      }
    },
    [applyJob, job, resync, roomId, token],
  );

  /** 분석을 멈춘다. */
  const cancel = useCallback(
    () => release("나누기를 멈추지 못했어요. 잠시 뒤 다시 시도해 주세요."),
    [release],
  );

  /**
   * 결과 알림을 닫는다.
   *
   * 완료(READY)된 결과는 검토 기한(30분) 동안 방을 점유한다. 닫기만 하면 그동안 나도 다른 멤버도
   * 새로 정리할 수 없어서, 닫기를 **검토 포기**로 보고 방을 풀어 준다.
   * 찾은 사람이 없는 결과도 반영할 그룹이 없으니 같은 방법으로 닫는다.
   * 취소에 실패하면 알림이 돌아와 다시 누를 수 있도록, 숨김 기록은 실패 알림에만 남긴다.
   */
  const dismiss = useCallback(() => {
    if (!job) return;
    if (job.status === "READY") {
      void release("결과를 취소하지 못했어요. 다시 시도해 주세요.");
      return;
    }
    hideNotice(job.jobId);
  }, [hideNotice, job, release]);

  /**
   * 결과를 새로 읽는다. 검토 화면을 열 때('확인하기')와, 읽지 못해 다시 시도할 때 쓴다.
   * 완료 때 읽어 둔 결과는 알림의 사람 수용이다. 그 뒤 시간이 지나면 사진 주소(서명 URL)가 만료되거나
   * 그사이 지워진 사진이 남아 있을 수 있어, 화면을 열 때 한 번 더 받는다.
   */
  const reloadResult = useCallback(() => {
    setLoaded(null);
    setResultAttempt((attempt) => attempt + 1);
  }, []);

  /**
   * 검토한 그룹을 폴더로 만든다. 성공하면 만든 폴더를, 실패하면 null 을 돌려준다.
   *
   * 성공하면 방을 바로 풀어 준다. 서버는 반영과 함께 방 점유를 풀지만 이벤트는 조금 늦게 올 수 있다.
   * 실패해도 서버는 일부만 반영하지 않으니 검토 화면에 그대로 남아 다시 누르면 된다.
   * 이미 반영됐거나 기한이 지났으면 다시 눌러도 소용없어 현재 상태를 다시 읽는다.
   */
  const apply = useCallback(
    async (groups: OrganizeApplyGroup[]): Promise<OrganizeApplied | null> => {
      if (!job || job.status !== "READY" || groups.length === 0 || isApplyingRef.current) {
        return null;
      }
      isApplyingRef.current = true;
      setIsApplying(true);
      setApplyError(null);

      const body = JSON.stringify(groups);
      if (applyKeyRef.current?.body !== body) {
        applyKeyRef.current = { body, key: createIdempotencyKey() };
      }

      try {
        const applied = await applyOrganizeJob({
          roomId,
          token,
          jobId: job.jobId,
          groups,
          idempotencyKey: applyKeyRef.current.key,
        });
        applyKeyRef.current = null;
        // 같은 작업의 늦게 온 READY 이벤트가 알림을 되살리지 않게 버전을 하나 올려 둔다.
        setJob((current) =>
          current?.jobId === job.jobId
            ? { ...current, status: "APPLIED", jobVersion: current.jobVersion + 1 }
            : current,
        );
        setRoom((current) => ({ ...current, busy: false }));
        return applied;
      } catch (cause) {
        setApplyError(applyErrorMessage(cause));
        if (isApplyClosed(cause)) void resync();
        return null;
      } finally {
        isApplyingRef.current = false;
        setIsApplying(false);
      }
    },
    [job, resync, roomId, token],
  );

  const isMine = job !== null && VISIBLE_STATUSES.includes(job.status);
  const isRunning = job !== null && isActiveStatus(job.status);
  const isAwaitingReview = job?.status === "READY";

  const readyResult = loaded !== null && loaded.jobId === readyJobId ? loaded : null;

  // READY 는 방을 점유하므로 숨기지 않는다. 숨기면 방을 풀어 줄 X 까지 사라진다.
  const notice: MyOrganizeJob | null =
    job && isMine && (isRunning || isAwaitingReview || job.jobId !== dismissedJobId)
      ? {
          jobId: job.jobId,
          status: job.status as MyOrganizeJob["status"],
          percent: job.percent,
          photoCount: job.photoCount,
          retryable: job.retryable,
          peopleCount: readyResult?.result ? readyResult.result.groups.length : null,
        }
      : null;

  // 완료된 내 결과도 검토 기한(30분) 동안 방을 점유한다. 다른 사람 작업과 구분해 알린다.
  const blockedReason = isRunning
    ? "지금 나누고 있어요"
    : isAwaitingReview
      ? "확인하지 않은 정리 결과가 있어요"
      : room.busy
        ? "다른 사람이 정리하고 있어요"
        : null;

  return {
    notice,
    isRunning,
    isStarting,
    blockedReason,
    isPeopleCountLoading: notice?.status === "READY" && readyResult === null,
    /**
     * 검토할 결과. 아직 읽는 중이면 `loading`, 읽지 못했으면 `failed` 다.
     * 완료(READY)된 내 작업이 없으면 null 이다.
     */
    review: !readyJobId
      ? null
      : readyResult === null
        ? ({ state: "loading" } as const)
        : readyResult.result === null
          ? ({ state: "failed" } as const)
          : ({ state: "ready", result: readyResult.result } as const),
    reloadResult,
    apply,
    isApplying,
    /** 폴더 반영이 거절된 까닭. 검토 화면 안에서 알린다. */
    applyError,
    clearApplyError: () => setApplyError(null),
    error,
    clearError: () => setError(null),
    /** 서버가 장수 때문에 거절한 까닭. 모달로 알린다. */
    limit,
    clearLimit: () => setLimit(null),
    start,
    retry,
    cancel,
    dismiss,
    resync,
    applyJobUpdate,
    applyRoomChange,
  };
};
