import { act, renderHook } from "@testing-library/react";

import { useRetryCountdown } from "./useRetryCountdown";

describe("useRetryCountdown", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it("시작하지 않으면 세지 않는다", () => {
    const { result } = renderHook(() => useRetryCountdown());

    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.isCountingDown).toBe(false);
  });

  it("매초 1씩 줄이다 0 에서 멈춘다", () => {
    const { result } = renderHook(() => useRetryCountdown());

    act(() => {
      result.current.start(3);
    });
    expect(result.current.remainingSeconds).toBe(3);

    act(() => jest.advanceTimersByTime(1000));
    expect(result.current.remainingSeconds).toBe(2);

    act(() => jest.advanceTimersByTime(2000));
    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.isCountingDown).toBe(false);

    // 0 에 닿으면 타이머를 더 걸지 않는다 — 음수로 내려가지 않는지 본다.
    act(() => jest.advanceTimersByTime(5000));
    expect(result.current.remainingSeconds).toBe(0);
  });

  it("초를 못 읽으면 카운트다운을 걸지 않는다", () => {
    const { result } = renderHook(() => useRetryCountdown());
    let started = true;

    act(() => {
      started = result.current.start(undefined);
    });

    expect(started).toBe(false);
    expect(result.current.isCountingDown).toBe(false);
  });

  it("0 이하도 카운트다운으로 보지 않는다", () => {
    const { result } = renderHook(() => useRetryCountdown());

    act(() => {
      result.current.start(0);
    });

    expect(result.current.isCountingDown).toBe(false);
  });

  it("소수로 와도 올림해서 센다 — 0.4초를 0초로 깎으면 잠금이 사라진다", () => {
    const { result } = renderHook(() => useRetryCountdown());

    act(() => {
      result.current.start(2.4);
    });

    expect(result.current.remainingSeconds).toBe(3);
  });

  it("clear 로 즉시 멈춘다", () => {
    const { result } = renderHook(() => useRetryCountdown());

    act(() => {
      result.current.start(10);
    });
    act(() => result.current.clear());

    expect(result.current.isCountingDown).toBe(false);
  });
});
