import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { manageAccount } from "@/features/manage-account";
import { AccountSettingsPage } from "./AccountSettingsPage";

jest.mock("@/features/manage-account", () => ({ manageAccount: jest.fn() }));
const request = jest.mocked(manageAccount);

const setup = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(MEMBER_SESSION_QUERY_KEY, {
    accessToken: "member-token",
    userId: 1,
    nickname: "회원",
    memberStatus: "REGISTERED",
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  });
  client.setQueryData(["private-data"], "secret");
  localStorage.setItem("sssok.auth:test-room", "stored-session");
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/settings"]}>
        <Routes>
          <Route path="/settings" element={<AccountSettingsPage />} />
          <Route path="/app/login" element={<p>로그인 화면</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { client, user: userEvent.setup() };
};

it.each([
  ["로그아웃", "logout", "로그아웃"],
  ["회원탈퇴", "withdraw", "탈퇴하기"],
] as const)("%s 확인 후 API를 호출하고 인증과 캐시를 정리한다", async (label, action, confirm) => {
  request.mockResolvedValueOnce(undefined);
  const { client, user } = setup();
  expect(screen.queryByText("로그인 수단")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: /이용약관/ })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: label }));
  expect(request).not.toHaveBeenCalled();
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: confirm }));
  await screen.findByText("로그인 화면");
  expect(request).toHaveBeenCalledWith(action, "member-token");
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toBeNull();
  expect(client.getQueryData(["private-data"])).toBeUndefined();
  expect(localStorage.length).toBe(0);
});

it("탈퇴 실패 시 오류를 안내하고 로그인 상태를 유지한다", async () => {
  request.mockRejectedValueOnce(new Error("network"));
  const { client, user } = setup();
  await user.click(screen.getByRole("button", { name: "회원탈퇴" }));
  await user.click(screen.getByRole("button", { name: "탈퇴하기" }));
  await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  expect(localStorage.getItem("sssok.auth:test-room")).toBe("stored-session");
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toMatchObject({
    accessToken: "member-token",
  });
});
