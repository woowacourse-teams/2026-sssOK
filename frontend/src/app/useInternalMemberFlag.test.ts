import { QueryClient } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { markInternalMember } from "@/shared/lib/analytics";
import { useInternalMemberFlag } from "./useInternalMemberFlag";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  markInternalMember: jest.fn(),
}));

const session = (userId: number): Record<string, unknown> => ({
  accessToken: "token",
  userId,
  nickname: "하루",
  expiresAt: "2026-11-25T00:00:00Z",
  refreshExpiresAt: "2026-12-25T00:00:00Z",
  memberStatus: "REGISTERED",
});

describe("useInternalMemberFlag", () => {
  it("이미 받아 둔 회원 세션이 있으면 바로 확인한다", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, session(12));

    renderHook(() => useInternalMemberFlag(queryClient));

    expect(markInternalMember).toHaveBeenCalledWith(12);
  });

  it("로그인해서 회원 세션이 들어오면 확인한다", () => {
    const queryClient = new QueryClient();
    renderHook(() => useInternalMemberFlag(queryClient));
    expect(markInternalMember).not.toHaveBeenCalled();

    act(() => {
      queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, session(34));
    });

    expect(markInternalMember).toHaveBeenCalledWith(34);
  });

  it("회원이 아니거나 다른 쿼리가 바뀌면 확인하지 않는다", () => {
    const queryClient = new QueryClient();
    renderHook(() => useInternalMemberFlag(queryClient));

    act(() => {
      queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, null);
      queryClient.setQueryData(["room", "ABC123"], { userId: 12 });
    });

    expect(markInternalMember).not.toHaveBeenCalled();
  });
});
