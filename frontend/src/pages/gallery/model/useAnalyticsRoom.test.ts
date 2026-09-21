import { renderHook } from "@testing-library/react";

import { setAnalyticsRoom } from "@/shared/lib/analytics";
import { useAnalyticsRoom } from "./useAnalyticsRoom";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  setAnalyticsRoom: jest.fn(),
}));

describe("useAnalyticsRoom", () => {
  it("방장이면 host 로, 아니면 guest 로 방 정보를 건다", () => {
    const { rerender } = renderHook(({ isHost }) => useAnalyticsRoom("7K93QX2S", isHost), {
      initialProps: { isHost: true },
    });
    expect(setAnalyticsRoom).toHaveBeenLastCalledWith({ room_code: "7K93QX2S", role: "host" });

    rerender({ isHost: false });
    expect(setAnalyticsRoom).toHaveBeenLastCalledWith({ room_code: "7K93QX2S", role: "guest" });
  });

  it("갤러리를 떠나면 방 정보를 지운다", () => {
    const { unmount } = renderHook(() => useAnalyticsRoom("7K93QX2S", false));

    unmount();

    expect(setAnalyticsRoom).toHaveBeenLastCalledWith(null);
  });
});
