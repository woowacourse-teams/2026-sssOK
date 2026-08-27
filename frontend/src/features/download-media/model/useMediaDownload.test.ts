import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useMediaDownload } from "./useMediaDownload";
import type { DownloadTarget } from "./types";

jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  INDIVIDUAL_SAVE_GAP_MS: 0,
}));

const TOKEN = "mock-token-10234";

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  size: 100,
  mimeType: "image/jpeg",
});

const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const serveBytes = () =>
  server.use(
    http.get(
      `${API_BASE_URL}/rooms/:roomId/media/:mediaId/download`,
      () => new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": "image/jpeg" } }),
    ),
  );

/** 응답을 붙잡아 뒀다가 원할 때 내보낸다. 받는 중 상태를 손으로 붙잡기 위해서다. */
const serveHeld = () => {
  let release = () => {};
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });

  server.use(
    http.get(`${API_BASE_URL}/rooms/:roomId/media/:mediaId/download`, async () => {
      await held;

      return new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": "image/jpeg" } });
    }),
  );

  return { release: () => release() };
};

const setup = (options: Parameters<typeof useMediaDownload>[0]) =>
  renderHook(() => useMediaDownload(options));

beforeEach(enterRoom);

describe("useMediaDownload", () => {
  it("받는 중이 아니면 progress 가 null 이다 — 바를 띄울지가 이 값 하나로 정해진다", () => {
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN });

    expect(result.current.progress).toBeNull();
  });

  it("한 판이 끝나면 결말을 알리고 바를 치운다", async () => {
    serveBytes();

    const onSettled = jest.fn();
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN, onSettled });

    await act(() => result.current.start([targetOf(5000)], "individual"));

    expect(onSettled).toHaveBeenCalledWith({ type: "saved", savedCount: 1, failed: [] });
    expect(result.current.progress).toBeNull();
  });

  it("고른 것이 없으면 시작하지 않는다", async () => {
    const onSettled = jest.fn();
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN, onSettled });

    await act(() => result.current.start([], "individual"));

    expect(onSettled).not.toHaveBeenCalled();
    expect(result.current.progress).toBeNull();
  });

  it("이미 한 판이 돌고 있으면 두 번째 판을 시작하지 않는다", async () => {
    const { release } = serveHeld();
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN });

    let first!: Promise<void>;

    act(() => {
      first = result.current.start([targetOf(5000)], "individual");
    });

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    // 두 판이 겹치면 진행 바가 어느 쪽을 세는지 알 수 없다.
    await act(() => result.current.start([targetOf(5001), targetOf(5002)], "individual"));

    expect(result.current.progress?.totalCount).toBe(1);

    release();
    await act(async () => {
      await first;
    });
  });

  it("취소하면 바를 곧바로 치운다", async () => {
    const { release } = serveHeld();
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN });

    let running!: Promise<void>;

    act(() => {
      running = result.current.start([targetOf(5000)], "individual");
    });

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    act(() => result.current.cancel());

    expect(result.current.progress).toBeNull();

    release();
    await act(async () => {
      await running;
    });
  });

  /**
   * 취소한 판이 뒤늦게 끝나면서 화면을 건드리면, 사용자가 치운 바가 되살아나거나
   * "1장 저장됨" 같은 안내가 뒤늦게 뜬다. `runIdRef` 가 막는 부분이다.
   */
  it("취소한 판의 결말은 화면에도 콜백에도 닿지 않는다", async () => {
    const { release } = serveHeld();
    const onSettled = jest.fn();
    const { result } = setup({ roomId: MOCK_ROOM_ID, token: TOKEN, onSettled });

    let running!: Promise<void>;

    act(() => {
      running = result.current.start([targetOf(5000)], "individual");
    });

    await waitFor(() => expect(result.current.progress).not.toBeNull());

    act(() => result.current.cancel());
    release();

    await act(async () => {
      await running;
    });

    expect(onSettled).not.toHaveBeenCalled();
    expect(result.current.progress).toBeNull();
  });
});
