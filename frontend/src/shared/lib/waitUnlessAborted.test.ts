import { waitUnlessAborted } from "./waitUnlessAborted";

describe("waitUnlessAborted", () => {
  it("중단되지 않으면 끝까지 기다리고 true 를 준다", async () => {
    expect(await waitUnlessAborted(5)).toBe(true);
  });

  it("기다리는 중에 중단되면 곧바로 깨어난다", async () => {
    const controller = new AbortController();
    const startedAt = Date.now();

    const pending = waitUnlessAborted(5_000, controller.signal);
    controller.abort();

    expect(await pending).toBe(false);
    // 5초를 다 기다렸다면 이 검사를 통과하지 못한다.
    expect(Date.now() - startedAt).toBeLessThan(1_000);
  });

  it("이미 중단된 signal 이면 기다리지 않는다", async () => {
    expect(await waitUnlessAborted(5_000, AbortSignal.abort())).toBe(false);
  });
});
