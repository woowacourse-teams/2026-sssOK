import { runWithLimit } from "./runWithLimit";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** 마이크로태스크와 타이머가 한 바퀴 돌 틈을 준다. */
const tick = () => sleep(0);

const deferred = () => {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => {
    release = resolve;
  });

  return { promise, release };
};

describe("runWithLimit", () => {
  it("완료 순서가 뒤섞여도 결과는 입력 순서로 돌아온다", async () => {
    const results = await runWithLimit([30, 0, 15], 3, async (ms) => {
      await sleep(ms);
      return `${ms}ms`;
    });

    // 실제로 끝난 순서는 0 → 15 → 30 이지만 자리는 입력 그대로다.
    expect(results).toEqual(["30ms", "0ms", "15ms"]);
  });

  it("동시에 도는 개수가 limit 을 넘지 않는다", async () => {
    let running = 0;
    let peak = 0;

    await runWithLimit(
      Array.from({ length: 9 }, (_, index) => index),
      3,
      async () => {
        running += 1;
        peak = Math.max(peak, running);
        await sleep(1);
        running -= 1;
      },
    );

    expect(peak).toBe(3);
  });

  it("앞선 작업이 끝나야 대기 중인 다음 작업이 출발한다", async () => {
    const started: string[] = [];
    const gates = { 첫째: deferred(), 둘째: deferred(), 셋째: deferred() };

    const pending = runWithLimit(["첫째", "둘째", "셋째"] as const, 2, async (name) => {
      started.push(name);
      await gates[name].promise;
    });

    await tick();
    // 셋째는 자리가 없어 아직 출발하지 못했다.
    expect(started).toEqual(["첫째", "둘째"]);

    gates.첫째.release();
    await tick();
    expect(started).toEqual(["첫째", "둘째", "셋째"]);

    gates.둘째.release();
    gates.셋째.release();
    await pending;
  });

  it("작업이 limit 보다 적으면 그만큼만 돈다", async () => {
    const results = await runWithLimit([1, 2], 3, async (value) => value * 10);

    expect(results).toEqual([10, 20]);
  });

  it("빈 목록이면 아무것도 돌리지 않는다", async () => {
    const run = jest.fn();

    expect(await runWithLimit([], 3, run)).toEqual([]);
    expect(run).not.toHaveBeenCalled();
  });

  it("limit 이 0 이하로 들어와도 멈추지 않고 하나씩 돈다", async () => {
    const results = await runWithLimit([1, 2, 3], 0, async (value) => value * 10);

    expect(results).toEqual([10, 20, 30]);
  });

  it("run 이 던지면 전체가 거절된다 — 실패는 값으로 돌려줘야 한다", async () => {
    await expect(
      runWithLimit([1, 2], 2, async (value) => {
        if (value === 2) throw new Error("깨졌다");
        return value;
      }),
    ).rejects.toThrow("깨졌다");
  });
});
