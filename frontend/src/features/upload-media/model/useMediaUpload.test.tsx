import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { MOCK_R2_BASE_URL } from "@/mocks/handlers/upload";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useMediaUpload } from "./useMediaUpload";

const TOKEN = "mock-token-10234";

const fileOf = (fileName: string, byteLength: number, type = "image/jpeg") =>
  new File(["x".repeat(byteLength)], fileName, { type });

const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

/**
 * PUT 을 붙잡아 둔다. 목은 즉시 응답해서 그냥 두면 시작하자마자 끝나 버려,
 * "올라가는 중" 의 화면을 볼 수가 없다.
 *
 * 가로챈 PUT 은 목이 업로드 바이트를 기록하지 못해 완료 등록이 실패로 떨어진다.
 * 그래서 이 헬퍼를 쓰는 검사는 등록 결과가 아니라 **진행 상태**만 본다.
 */
const holdPuts = (shouldHold: (key: string) => boolean = () => true) => {
  let open = () => {};
  const opened = new Promise<void>((resolve) => {
    open = resolve;
  });

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, async ({ request }) => {
      if (shouldHold(new URL(request.url).pathname)) {
        await opened;
      }

      return new HttpResponse(null, { status: 200 });
    }),
  );

  return { open: () => open() };
};

const renderUpload = (options: Partial<Parameters<typeof useMediaUpload>[0]> = {}) =>
  renderHook(() => useMediaUpload({ roomId: MOCK_ROOM_ID, token: TOKEN, ...options }));

/** `start` 는 판이 끝나야 풀린다. 도는 중을 보려면 기다리지 않고 손잡이만 받아둬야 한다. */
const startUpload = (result: { current: ReturnType<typeof useMediaUpload> }, files: File[]) => {
  let running: Promise<void>;

  act(() => {
    running = result.current.start(files);
  });

  return running!;
};

describe("useMediaUpload", () => {
  it("업로드 전에는 진행 상태가 없다 — 바를 띄울 이유가 없다", () => {
    const { result } = renderUpload();

    expect(result.current.progress).toBeNull();
  });

  it("파일을 넘기면 발급을 기다리지 않고 곧바로 진행 상태가 생긴다", async () => {
    await enterRoom();
    const hold = holdPuts();
    const { result } = renderUpload();

    const running = startUpload(result, [fileOf("첫째.jpg", 3), fileOf("둘째.jpg", 7)]);

    // 발급 왕복 전이라 아직 잠정값이다. 그래도 바는 이미 떠 있어야 한다.
    expect(result.current.progress).toEqual({ completedCount: 0, totalCount: 2, percent: 0 });

    hold.open();
    await act(async () => {
      await running;
    });
  });

  it("발급이 거절한 파일은 전체 장수에서 빠진다", async () => {
    await enterRoom();
    const hold = holdPuts();
    const { result } = renderUpload();

    const running = startUpload(result, [
      fileOf("첫째.jpg", 3),
      fileOf("메모.txt", 5, "text/plain"),
    ]);

    await waitFor(() => expect(result.current.progress?.totalCount).toBe(1));

    hold.open();
    await act(async () => {
      await running;
    });
  });

  it("PUT 이 끝날 때마다 완료 장수가 오르고, 퍼센트는 바이트로 센다", async () => {
    await enterRoom();
    // 영상만 붙잡아 둔다. 사진은 그대로 끝난다.
    const hold = holdPuts((key) => key.endsWith(".mp4"));
    const { result } = renderUpload();

    const running = startUpload(result, [
      fileOf("사진.jpg", 3),
      fileOf("영상.mp4", 97, "video/mp4"),
    ]);

    // 2장 중 1장이 끝났지만 회선으로는 3%밖에 안 나갔다. 50% 가 아니다.
    await waitFor(() =>
      expect(result.current.progress).toEqual({ completedCount: 1, totalCount: 2, percent: 3 }),
    );

    hold.open();
    await act(async () => {
      await running;
    });
  });

  it("업로드가 끝나면 진행 상태가 사라진다 — 바가 저절로 사라진다", async () => {
    await enterRoom();
    const onSettled = jest.fn();
    const { result } = renderUpload({ onSettled });

    await act(async () => {
      await result.current.start([fileOf("첫째.jpg", 3)]);
    });

    expect(result.current.progress).toBeNull();
    expect(onSettled).toHaveBeenCalledWith(
      expect.objectContaining({ registered: [expect.objectContaining({ fileName: "첫째.jpg" })] }),
    );
  });

  it("취소하면 등록 왕복을 기다리지 않고 곧바로 사라진다", async () => {
    await enterRoom();
    const hold = holdPuts();
    const { result } = renderUpload();

    const running = startUpload(result, [fileOf("첫째.jpg", 3), fileOf("둘째.jpg", 3)]);

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    act(() => {
      result.current.cancel();
    });

    expect(result.current.progress).toBeNull();

    hold.open();
    await act(async () => {
      await running;
    });
  });

  it("취소한 판도 결과는 그대로 알린다 — 이미 올라간 파일이 등록돼야 한다", async () => {
    await enterRoom();
    const hold = holdPuts();
    const onSettled = jest.fn();
    const { result } = renderUpload({ onSettled });

    const running = startUpload(result, [fileOf("첫째.jpg", 3)]);

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    act(() => {
      result.current.cancel();
    });

    hold.open();
    await act(async () => {
      await running;
    });

    // 무엇이 등록되고 무엇이 실패했는지는 uploadFiles 가 가른다. 여기서는 결과가 온다는 것만 본다.
    expect(onSettled).toHaveBeenCalledTimes(1);
  });

  it("취소한 판이 뒤늦게 끝나도 사라진 바를 되살리지 않는다", async () => {
    await enterRoom();
    const hold = holdPuts();
    const { result } = renderUpload();

    const running = startUpload(result, [fileOf("첫째.jpg", 3)]);

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    act(() => {
      result.current.cancel();
    });

    hold.open();
    await act(async () => {
      await running;
    });

    expect(result.current.progress).toBeNull();
  });

  it("도는 중에 또 시작해도 판이 겹치지 않는다", async () => {
    await enterRoom();
    const hold = holdPuts();
    let issueCount = 0;

    server.events.on("request:start", ({ request }) => {
      if (new URL(request.url).pathname.endsWith("/media/upload-urls")) {
        issueCount += 1;
      }
    });

    const { result } = renderUpload();
    const running = startUpload(result, [fileOf("첫째.jpg", 3)]);

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    const ignored = startUpload(result, [fileOf("둘째.jpg", 3), fileOf("셋째.jpg", 3)]);

    await act(async () => {
      await ignored;
    });

    // 두 번째 호출은 아무 일도 하지 않는다. 첫 판의 장수가 그대로여야 한다.
    expect(result.current.progress?.totalCount).toBe(1);

    hold.open();
    await act(async () => {
      await running;
    });

    expect(issueCount).toBe(1);
  });

  it("빈 목록으로 시작하면 바를 띄우지 않는다", async () => {
    await enterRoom();
    const { result } = renderUpload();

    await act(async () => {
      await result.current.start([]);
    });

    expect(result.current.progress).toBeNull();
  });

  it("방·권한 문제로 배치 전체가 막히면 바를 치우고 알린다", async () => {
    const onError = jest.fn();
    const { result } = renderUpload({ onError });

    // 입장하지 않은 방이라 발급부터 403 이다.
    await act(async () => {
      await result.current.start([fileOf("첫째.jpg", 3)]);
    });

    expect(result.current.progress).toBeNull();
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ code: "NOT_ROOM_MEMBER" }));
  });
});

afterEach(() => server.events.removeAllListeners());
