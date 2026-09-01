import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import type { DownloadJobStatus } from "../api/types";
import { pollDownloadJob } from "./pollDownloadJob";

// 되묻는 간격을 실제로 기다리면 검사 하나마다 1.5초씩 잡아먹는다. 간격 자체는 상수로 고정돼 있다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  POLL_INTERVAL_MS: 0,
}));

const TOKEN = "mock-token-10234";
const JOB_ID = "dl_000001";

const progressOf = (status: DownloadJobStatus, progress: number) => ({
  jobId: JOB_ID,
  status,
  progress,
  mediaCount: 3,
  fileName: "ShareDrop_7K93QX2S.zip",
  downloadUrl: status === "READY" ? "https://storage.example.com/zips/dl_000001.zip" : null,
  expiresAt: null,
  failureReason: status === "FAILED" ? "압축에 실패했습니다" : null,
});

/**
 * 되물을 때마다 다음 상태를 하나씩 내준다. 마지막에 닿으면 그 자리에 머문다 —
 * 폴링이 멈추지 않으면 그 사실이 `calls` 로 드러난다.
 */
const serveSequence = (sequence: ReturnType<typeof progressOf>[]) => {
  const calls: number[] = [];

  server.use(
    http.get(`${API_BASE_URL}/rooms/:roomId/downloads/zip/:jobId`, () => {
      const index = Math.min(calls.length, sequence.length - 1);

      calls.push(index);

      return HttpResponse.json({ data: sequence[index] });
    }),
  );

  return calls;
};

const run = (signal?: AbortSignal, onProgress?: (p: { progress: number }) => void) =>
  pollDownloadJob({ roomId: MOCK_ROOM_ID, jobId: JOB_ID, token: TOKEN, signal, onProgress });

describe("pollDownloadJob", () => {
  it("READY 가 되면 멈추고 그 응답을 돌려준다", async () => {
    const calls = serveSequence([
      progressOf("QUEUED", 0),
      progressOf("RUNNING", 60),
      progressOf("READY", 100),
    ]);

    const settled = await run();

    expect(settled?.status).toBe("READY");
    expect(calls).toHaveLength(3);
  });

  // 더 물어봐도 소용없는 상태들이다. 여기서 안 멈추면 영원히 돈다.
  it.each<DownloadJobStatus>(["FAILED", "EXPIRED"])("%s 에서도 멈춘다", async (status) => {
    const calls = serveSequence([progressOf("RUNNING", 10), progressOf(status, 10)]);

    const settled = await run();

    expect(settled?.status).toBe(status);
    expect(calls).toHaveLength(2);
  });

  it("되물을 때마다 진행률을 넘긴다", async () => {
    serveSequence([progressOf("RUNNING", 30), progressOf("READY", 100)]);

    const seen: number[] = [];

    await run(undefined, ({ progress }) => seen.push(progress));

    expect(seen).toEqual([30, 100]);
  });

  it("이미 중단된 신호면 한 번도 묻지 않는다", async () => {
    const calls = serveSequence([progressOf("QUEUED", 0)]);

    expect(await run(AbortSignal.abort())).toBeNull();
    expect(calls).toHaveLength(0);
  });

  /**
   * 취소는 실패가 아니라서 `null` 이다. 여기서 결말을 돌려주면 부르는 쪽이
   * 취소한 판에 실패 안내를 띄운다.
   */
  it("기다리는 중에 취소되면 더 묻지 않고 null 을 준다", async () => {
    const calls = serveSequence([progressOf("QUEUED", 0)]);
    const controller = new AbortController();
    const settled = run(controller.signal);

    // 첫 조회가 끝나고 다음 차례를 기다리는 사이에 끊는다.
    await Promise.resolve();
    controller.abort();

    expect(await settled).toBeNull();
    expect(calls).toHaveLength(1);
  });
});
