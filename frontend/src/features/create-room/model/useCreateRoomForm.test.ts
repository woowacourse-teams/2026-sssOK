import { act, renderHook } from "@testing-library/react";

import { INITIAL_CREATE_ROOM_FORM } from "./createRoomForm";
import { useCreateRoomForm } from "./useCreateRoomForm";

describe("useCreateRoomForm", () => {
  it("초기 폼 상태를 반환한다", () => {
    const { result } = renderHook(() => useCreateRoomForm());

    expect(result.current.formValues).toEqual(INITIAL_CREATE_ROOM_FORM);
    expect(result.current.isValid).toBe(false);
  });

  it("필드 값을 변경하고 필수 입력 여부를 검증한다", () => {
    const { result } = renderHook(() => useCreateRoomForm());

    act(() => {
      result.current.updateField("nickname")("민수");
      result.current.updateField("name")("제주 여행");
    });

    expect(result.current.formValues).toEqual(
      expect.objectContaining({
        nickname: "민수",
        name: "제주 여행",
      }),
    );
    expect(result.current.isValid).toBe(true);
  });
});
