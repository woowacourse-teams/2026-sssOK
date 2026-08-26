import { act, render, screen } from "@testing-library/react";

import { RoomRemainingTime, formatRemainingTime } from "./RoomRemainingTime";

describe("RoomRemainingTime", () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it("만료 시각까지 남은 시간을 시·분으로 표시한다", () => {
    expect(
      formatRemainingTime("2026-08-19T05:30:00Z", new Date("2026-08-18T06:20:00Z").getTime()),
    ).toBe("23시간 10분");
  });

  it("남은 시간이 1분 미만이면 초로 표시한다", () => {
    expect(
      formatRemainingTime("2026-08-18T06:20:42Z", new Date("2026-08-18T06:20:00Z").getTime()),
    ).toBe("42초");
  });

  it("시간이 지나면 표시를 갱신한다", () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-08-18T06:20:00Z"));

    render(<RoomRemainingTime expiresAt="2026-08-18T06:22:00Z" />);
    expect(screen.getByText("2분")).toBeInTheDocument();

    act(() => {
      jest.advanceTimersByTime(60 * 1000);
    });

    expect(screen.getByText("1분")).toBeInTheDocument();
  });

  it("만료된 시각은 만료됨으로 표시한다", () => {
    expect(
      formatRemainingTime("2026-08-18T06:19:00Z", new Date("2026-08-18T06:20:00Z").getTime()),
    ).toBe("만료됨");
  });
});
