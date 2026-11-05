import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import type { OrganizeCurrent, OrganizeJob } from "../api/types";
import { useOrganizeJob } from "./useOrganizeJob";

const ROOM_ID = 1;
const BASE = `${API_BASE_URL}/rooms/${ROOM_ID}/organize-jobs`;
const JOB_ID = "7b44258a-81c7-4e75-9a85-16e5df65ad61";

const jobOf = (overrides: Partial<OrganizeJob> = {}): OrganizeJob => ({
  jobId: JOB_ID,
  status: "QUEUED",
  kind: "PERSON",
  photoCount: 40,
  excludedCount: 0,
  attempt: 1,
  jobVersion: 1,
  retryable: false,
  retryAvailableAt: null,
  reviewExpiresAt: null,
  resultExpiresAt: null,
  createdAt: "2026-11-04T03:00:00Z",
  updatedAt: "2026-11-04T03:00:00Z",
  ...overrides,
});

const serve = ({
  current = { roomBusy: false, roomVersion: 1, myJob: null },
  groupCount = 6,
}: { current?: OrganizeCurrent; groupCount?: number } = {}) => {
  const requests: { path: string; body: unknown; key: string | null }[] = [];
  const record = async (request: Request) => {
    requests.push({
      path: new URL(request.url).pathname.replace(/^.*organize-jobs/, ""),
      body: await request.json().catch(() => null),
      key: request.headers.get("Idempotency-Key"),
    });
  };

  server.use(
    http.get(`${BASE}/current`, () => HttpResponse.json({ data: current })),
    http.post(BASE, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ data: jobOf() }, { status: 202 });
    }),
    http.post(`${BASE}/by-text`, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ data: jobOf() }, { status: 202 });
    }),
    http.get(`${BASE}/:jobId/result`, () =>
      HttpResponse.json({
        data: {
          jobId: JOB_ID,
          kind: "PERSON",
          resultVersion: 1,
          photoCount: 40,
          removedCount: 0,
          groups: Array.from({ length: groupCount }, (_, index) => ({
            groupId: `person_${index}`,
            photoCount: 1,
            photos: [],
          })),
          unassigned: { groupId: null, photoCount: 0, photos: [] },
          errors: [],
        },
      }),
    ),
    http.post(`${BASE}/:jobId/cancel`, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ data: jobOf({ status: "CANCELED", jobVersion: 9 }) });
    }),
    http.post(`${BASE}/:jobId/retry`, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ data: jobOf({ attempt: 2, jobVersion: 20 }) }, { status: 202 });
    }),
  );
  return { requests };
};

const render = () => renderHook(() => useOrganizeJob({ roomId: ROOM_ID, token: "mock-token-1" }));

const settle = () => act(async () => {});

beforeEach(() => sessionStorage.clear());

describe("useOrganizeJob", () => {
  it("사람별로 시작하면 중복 방지 키와 함께 접수하고 분석 대기 진행 바를 띄운다", async () => {
    const { requests } = serve();
    const { result } = render();
    await settle();

    await act(() => result.current.start({ kind: "PERSON" }));

    expect(requests[0]).toMatchObject({ path: "", body: { kind: "PERSON" } });
    expect(requests[0].key).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
    expect(result.current.notice).toMatchObject({ status: "QUEUED", photoCount: 40 });
    expect(result.current.blockedReason).toBe("지금 나누고 있어요");
  });

  it("문장으로 시작하면 by-text 로 보낸다", async () => {
    const { requests } = serve();
    const { result } = render();
    await settle();

    await act(() => result.current.start({ text: "사람별로 나눠 줘" }));

    expect(requests[0]).toMatchObject({ path: "/by-text", body: { text: "사람별로 나눠 줘" } });
  });

  it("방 SSE 진행 이벤트를 반영하고, 버전이 낮은 이벤트는 버린다", async () => {
    serve();
    const { result } = render();
    await settle();
    await act(() => result.current.start({ kind: "PERSON" }));

    act(() =>
      result.current.applyJobUpdate({
        ...jobOf({ status: "RUNNING", jobVersion: 3 }),
        progress: { stage: "detecting_faces", processed: 18, total: 40 },
      }),
    );
    act(() => result.current.applyJobUpdate({ ...jobOf({ status: "QUEUED", jobVersion: 2 }) }));

    expect(result.current.notice).toMatchObject({ status: "RUNNING" });
  });

  it("완료되면 결과의 그룹 수로 찾은 사람 수를 채운다", async () => {
    serve({ groupCount: 6 });
    const { result } = render();
    await settle();
    await act(() => result.current.start({ kind: "PERSON" }));

    act(() => result.current.applyJobUpdate(jobOf({ status: "READY", jobVersion: 5 })));

    await waitFor(() => expect(result.current.notice?.peopleCount).toBe(6));
    expect(result.current.isRunning).toBe(false);
  });

  it("사람이 없으면 찾은 사람 수가 0이다", async () => {
    serve({ groupCount: 0 });
    const { result } = render();
    await settle();
    await act(() => result.current.start({ kind: "PERSON" }));

    act(() => result.current.applyJobUpdate(jobOf({ status: "READY", jobVersion: 5 })));

    await waitFor(() => expect(result.current.notice?.peopleCount).toBe(0));
  });

  it("다른 사람이 정리하는 중이면 이름 없이 막는다", async () => {
    serve({ current: { roomBusy: true, roomVersion: 4, myJob: null } });
    const { result } = render();

    await waitFor(() => expect(result.current.blockedReason).toBe("다른 사람이 정리하고 있어요"));
    expect(result.current.notice).toBeNull();
  });

  it("방 점유 이벤트는 버전이 더 높을 때만 반영한다", async () => {
    serve({ current: { roomBusy: true, roomVersion: 4, myJob: null } });
    const { result } = render();
    await settle();

    act(() => result.current.applyRoomChange({ roomId: ROOM_ID, roomVersion: 3, busy: false }));
    expect(result.current.blockedReason).toBe("다른 사람이 정리하고 있어요");

    act(() => result.current.applyRoomChange({ roomId: ROOM_ID, roomVersion: 5, busy: false }));
    expect(result.current.blockedReason).toBeNull();
  });

  it("다시 들어오면 내 작업을 이어서 보여 준다", async () => {
    serve({
      current: {
        roomBusy: true,
        roomVersion: 2,
        myJob: jobOf({
          status: "RUNNING",
          jobVersion: 4,
          progress: { stage: "detecting_faces", processed: 10, total: 40 },
        }),
      },
    });
    const { result } = render();

    await waitFor(() => expect(result.current.notice?.status).toBe("RUNNING"));
    expect(result.current.blockedReason).toBe("지금 나누고 있어요");
  });

  it("닫은 결과 알림은 다시 띄우지 않지만, 검토 기한 동안 새 정리는 막는다", async () => {
    sessionStorage.setItem(`organize-dismissed:${ROOM_ID}`, JOB_ID);
    serve({
      current: { roomBusy: true, roomVersion: 2, myJob: jobOf({ status: "READY", jobVersion: 5 }) },
    });
    const { result } = render();

    await waitFor(() =>
      expect(result.current.blockedReason).toBe("확인하지 않은 정리 결과가 있어요"),
    );
    expect(result.current.notice).toBeNull();
  });

  it("접수 응답은 방 점유를 바꾸지 않아, 작업이 바로 실패해도 막히지 않는다", async () => {
    serve();
    const { result } = render();
    await settle();

    await act(() => result.current.start({ kind: "PERSON" }));
    act(() =>
      result.current.applyJobUpdate(
        jobOf({
          status: "FAILED",
          jobVersion: 2,
          retryable: true,
        }),
      ),
    );

    expect(result.current.blockedReason).toBeNull();
  });

  it.each([
    { roomBusy: true, expected: "다른 사람이 정리하고 있어요" },
    { roomBusy: false, expected: null },
  ])(
    "이미 진행 중이라고 거절되면 현재 상태를 다시 읽어 방 점유를 맞춘다 (점유: $roomBusy)",
    async ({ roomBusy, expected }) => {
      serve();
      server.use(
        http.post(BASE, () =>
          HttpResponse.json({ code: "ORGANIZE_JOB_IN_PROGRESS", message: "" }, { status: 409 }),
        ),
      );
      const { result } = render();
      await settle();
      server.use(
        http.get(`${BASE}/current`, () =>
          HttpResponse.json({ data: { roomBusy, roomVersion: 2, myJob: null } }),
        ),
      );

      await act(() => result.current.start({ kind: "PERSON" }));

      await waitFor(() => expect(result.current.blockedReason).toBe(expected));
      await settle();
      expect(result.current.blockedReason).toBe(expected);
    },
  );

  it("체험을 다 쓴 익명 회원에게 웹에서는 앱에서 로그인하라고 안내한다", async () => {
    serve();
    server.use(
      http.post(BASE, () =>
        HttpResponse.json({ code: "ORGANIZE_TRIAL_EXHAUSTED", message: "" }, { status: 403 }),
      ),
    );
    const { result } = render();
    await settle();

    await act(() => result.current.start({ kind: "PERSON" }));

    expect(result.current.notice).toBeNull();
    expect(result.current.error).toBe(
      "이제 AI 정리는 앱에서 이어서 쓸 수 있어요.\n앱을 설치하고 로그인해 주세요.",
    );
  });

  it("실패한 작업은 retry API 로 같은 작업을 다시 돌린다", async () => {
    const { requests } = serve({
      current: {
        roomBusy: false,
        roomVersion: 2,
        myJob: jobOf({ status: "FAILED", jobVersion: 8, retryable: true }),
      },
    });
    const { result } = render();
    await waitFor(() => expect(result.current.notice?.status).toBe("FAILED"));

    await act(() => result.current.retry());

    expect(requests[0].path).toBe(`/${JOB_ID}/retry`);
    expect(requests[0].key).not.toBeNull();
    expect(result.current.notice?.status).toBe("QUEUED");
  });

  it("재시도 가능 시각 전이면 보내지 않고 남은 시간을 알린다", async () => {
    const { requests } = serve({
      current: {
        roomBusy: false,
        roomVersion: 2,
        myJob: jobOf({
          status: "FAILED",
          jobVersion: 8,
          retryable: true,
          retryAvailableAt: new Date(Date.now() + 15_000).toISOString(),
        }),
      },
    });
    const { result } = render();
    await waitFor(() => expect(result.current.notice?.status).toBe("FAILED"));

    await act(() => result.current.retry());

    expect(requests).toHaveLength(0);
    expect(result.current.error).toMatch(/초 뒤에 다시 시도할 수 있어요/);
  });

  it("멈추면 진행 바를 닫고 cancel 을 보낸다", async () => {
    const { requests } = serve();
    const { result } = render();
    await settle();
    await act(() => result.current.start({ kind: "PERSON" }));

    await act(() => result.current.cancel());

    expect(requests.map((request) => request.path)).toContain(`/${JOB_ID}/cancel`);
    expect(result.current.notice).toBeNull();
  });
  it("완료 알림을 닫으면 검토 포기로 cancel 을 보내고 방을 풀어 준다", async () => {
    const { requests } = serve({
      current: { roomBusy: true, roomVersion: 2, myJob: jobOf({ status: "READY", jobVersion: 5 }) },
    });
    const { result } = render();
    await waitFor(() => expect(result.current.notice?.status).toBe("READY"));

    await act(async () => result.current.dismiss());

    await waitFor(() =>
      expect(requests.map((request) => request.path)).toEqual([`/${JOB_ID}/cancel`]),
    );
    expect(result.current.notice).toBeNull();
    expect(result.current.blockedReason).toBeNull();
    expect(sessionStorage.getItem(`organize-dismissed:${ROOM_ID}`)).toBe(JOB_ID);
  });

  it("실패 알림을 닫으면 cancel 없이 숨기기만 한다", async () => {
    const { requests } = serve({
      current: {
        roomBusy: false,
        roomVersion: 2,
        myJob: jobOf({ status: "FAILED", jobVersion: 8, retryable: true }),
      },
    });
    const { result } = render();
    await waitFor(() => expect(result.current.notice?.status).toBe("FAILED"));

    act(() => result.current.dismiss());

    expect(requests).toHaveLength(0);
    expect(result.current.notice).toBeNull();
  });

  it("완료된 결과를 검토용으로 들고 있고, 못 읽었으면 다시 읽을 수 있다", async () => {
    serve({
      current: { roomBusy: true, roomVersion: 2, myJob: jobOf({ status: "READY", jobVersion: 5 }) },
    });
    let failsLeft = 1;
    server.use(
      http.get(`${BASE}/:jobId/result`, () => {
        if (failsLeft === 0) return undefined;
        failsLeft -= 1;
        return HttpResponse.json({ code: "INTERNAL", message: "x" }, { status: 500 });
      }),
    );
    const { result } = render();

    await waitFor(() => expect(result.current.review).toEqual({ state: "failed" }));

    act(() => result.current.reloadResult());

    await waitFor(() => expect(result.current.review?.state).toBe("ready"));
    expect(result.current.notice?.peopleCount).toBe(6);
    expect(result.current.blockedReason).toBe("확인하지 않은 정리 결과가 있어요");
  });

  it("검토 화면을 열 때 결과를 새로 받는다", async () => {
    serve({
      current: { roomBusy: true, roomVersion: 2, myJob: jobOf({ status: "READY", jobVersion: 5 }) },
    });
    let resultCalls = 0;
    server.use(
      http.get(`${BASE}/:jobId/result`, () => {
        resultCalls += 1;
        return undefined;
      }),
    );
    const { result } = render();
    await waitFor(() => expect(result.current.review?.state).toBe("ready"));
    expect(resultCalls).toBe(1);

    act(() => result.current.reloadResult());

    expect(result.current.review).toEqual({ state: "loading" });
    await waitFor(() => expect(result.current.review?.state).toBe("ready"));
    expect(resultCalls).toBe(2);
  });

  it("서버가 200장 초과로 거절하면 토스트 대신 모달용 까닭을 남긴다", async () => {
    serve();
    server.use(
      http.post(BASE, () =>
        HttpResponse.json(
          {
            code: "ORGANIZE_TOO_MANY_PHOTOS",
            message: "정리할 사진이 너무 많습니다. 현재 230장, 최대 200장까지 정리할 수 있습니다",
          },
          { status: 422 },
        ),
      ),
    );
    const { result } = render();
    await settle();

    await act(() => result.current.start({ kind: "PERSON" }));

    expect(result.current.limit).toEqual({ reason: "tooMany", count: 230 });
    expect(result.current.error).toBeNull();
  });
  it("SSE 가 접수 응답보다 먼저 와도 분석 대상 장수를 잃지 않는다", async () => {
    serve();
    let release: () => void = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post(BASE, async () => {
        await held;
        return HttpResponse.json(
          { data: jobOf({ status: "RUNNING", jobVersion: 2 }) },
          { status: 202 },
        );
      }),
    );
    const { result } = render();
    await settle();

    let started: Promise<void> = Promise.resolve();
    act(() => {
      started = result.current.start({ kind: "PERSON" });
    });
    // SSE 이벤트에는 분석 대상 장수(photoCount)가 없다.
    const { jobId, status, jobVersion, attempt, retryable, retryAvailableAt } = jobOf({
      status: "RUNNING",
      jobVersion: 3,
    });
    act(() =>
      result.current.applyJobUpdate({
        jobId,
        status,
        jobVersion,
        attempt,
        retryable,
        retryAvailableAt,
        reviewExpiresAt: null,
        resultExpiresAt: null,
      }),
    );
    expect(result.current.notice?.photoCount).toBeNull();

    await act(async () => {
      release();
      await started;
    });

    expect(result.current.notice).toMatchObject({ status: "RUNNING", photoCount: 40 });
  });
  it("단계가 바뀌어 서버 숫자가 0으로 돌아가도 진행률은 앞으로만 간다", async () => {
    serve();
    const { result } = render();
    await settle();
    await act(() => result.current.start({ kind: "PERSON" }));

    act(() =>
      result.current.applyJobUpdate({
        ...jobOf({ status: "RUNNING", jobVersion: 3 }),
        progress: { stage: "detecting_faces", processed: 40, total: 40 },
      }),
    );
    const afterFaces = result.current.notice?.percent;
    act(() =>
      result.current.applyJobUpdate({
        ...jobOf({ status: "RUNNING", jobVersion: 4 }),
        progress: { stage: "grouping_faces", processed: null, total: null },
      }),
    );

    expect(afterFaces).toBe(37);
    expect(result.current.notice?.percent).toBe(37);
  });

  it("다시 시도해 새 회차가 되면 진행률을 처음부터 센다", async () => {
    serve({
      current: {
        roomBusy: true,
        roomVersion: 2,
        myJob: jobOf({
          status: "RUNNING",
          jobVersion: 5,
          progress: { stage: "matching_bodies", processed: null, total: null },
        }),
      },
    });
    const { result } = render();
    await waitFor(() => expect(result.current.notice?.percent).toBe(75));

    act(() =>
      result.current.applyJobUpdate({
        ...jobOf({ status: "RUNNING", jobVersion: 20, attempt: 2 }),
        progress: { stage: "preparing", processed: 0, total: 40 },
      }),
    );

    expect(result.current.notice?.percent).toBe(12);
  });
});
