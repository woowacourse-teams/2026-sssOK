import { act, renderHook } from "@testing-library/react";

import type { Room } from "@/entities/room";
import { useUpdateRoomForm } from "./useUpdateRoomForm";

const room: Room = {
  roomId: 5031,
  code: "7K93QX2S",
  name: "제주 여행",
  status: "ACTIVE",
  hostId: 10234,
  hostName: "민수",
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: "2026-08-19T05:30:00Z",
  uploadPolicy: "everyone",
  joined: true,
  photoCount: 0,
  folders: [],
};

describe("useUpdateRoomForm", () => {
  it("방 정보로 초기 폼 상태를 만든다", () => {
    const { result } = renderHook(() => useUpdateRoomForm(room));

    expect(result.current.formValues).toEqual({
      name: "제주 여행",
      uploadPolicy: "everyone",
      expiryHours: "",
    });
    expect(result.current.hasChanges).toBe(false);
  });

  it("폼 값을 객체로 변경하고 바뀐 값만 요청으로 만든다", () => {
    const { result } = renderHook(() => useUpdateRoomForm(room));

    act(() => {
      result.current.updateField("name")("제주 3박 4일");
      result.current.updateField("uploadPolicy")("host");
    });

    expect(result.current.formValues).toEqual({
      name: "제주 3박 4일",
      uploadPolicy: "host",
      expiryHours: "",
    });
    expect(result.current.request).toEqual({
      name: "제주 3박 4일",
      uploadPolicy: "host",
    });
    expect(result.current.hasChanges).toBe(true);
  });
});
