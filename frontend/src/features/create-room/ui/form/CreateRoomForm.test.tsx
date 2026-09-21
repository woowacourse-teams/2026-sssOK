import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import type { ComponentProps, ReactNode } from "react";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { track } from "@/shared/lib/analytics";
import { CreateRoomForm } from "./CreateRoomForm";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const renderForm = (props: ComponentProps<typeof CreateRoomForm> = {}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
    },
  });

  const QueryClientTestWrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return render(<CreateRoomForm {...props} />, { wrapper: QueryClientTestWrapper });
};

const fillRequiredFields = async () => {
  const user = userEvent.setup();

  await user.type(screen.getByRole("textbox", { name: "내 이름" }), "민수");
  await user.type(screen.getByRole("textbox", { name: "방 이름" }), "제주 여행");

  return user;
};

describe("CreateRoomForm", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("입력값으로 방을 생성하고 성공 결과를 전달한다", async () => {
    const handleSuccess = jest.fn();
    renderForm({ onSuccess: handleSuccess });

    const submitButton = screen.getByRole("button", { name: "방 만들기" });
    expect(submitButton).toBeDisabled();

    const user = await fillRequiredFields();
    await user.click(screen.getByRole("radio", { name: "방장만" }));
    await user.click(screen.getByRole("radio", { name: "3일" }));
    await user.click(submitButton);

    await waitFor(() => {
      expect(handleSuccess).toHaveBeenCalled();
      expect(handleSuccess.mock.calls[0][0]).toEqual(
        expect.objectContaining({
          code: "7K93QX2S",
          name: "제주 여행",
          uploadPolicy: "host",
        }),
      );
    });
  });

  it("요청 중에는 중복 제출을 막는다", async () => {
    server.use(
      http.post(`${API_BASE_URL}/auth/anonymous`, async ({ request }) => {
        const body = (await request.json()) as { nickname: string };
        await delay(50);

        return HttpResponse.json({
          accessToken: "mock-access-token",
          userId: 10234,
          nickname: body.nickname,
          expiresAt: "9999-12-31T23:59:59Z",
        });
      }),
    );
    renderForm();

    const user = await fillRequiredFields();
    await user.click(screen.getByRole("button", { name: "방 만들기" }));

    expect(screen.getByRole("button", { name: "방 만드는 중..." })).toBeDisabled();
    expect(await screen.findByRole("button", { name: "방 만들기" })).toBeEnabled();
  });

  it("방 생성 실패 메시지를 표시한다", async () => {
    server.use(
      http.post(`${API_BASE_URL}/auth/anonymous`, () => {
        return HttpResponse.json(
          { code: "INVALID_NICKNAME", message: "닉네임을 입력해주세요" },
          { status: 400 },
        );
      }),
    );
    renderForm();

    const user = await fillRequiredFields();
    await user.click(screen.getByRole("button", { name: "방 만들기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("닉네임을 입력해주세요");
  });

  describe("방 만들기 퍼널 이벤트", () => {
    it("폼이 뜨면 방 만들기 시작을 남긴다", () => {
      renderForm();

      expect(track).toHaveBeenCalledWith("Room Create Started", {});
    });

    it("방을 만들면 방 코드·만료 시간·업로드 권한을 남긴다", async () => {
      const handleSuccess = jest.fn();
      renderForm({ onSuccess: handleSuccess });
      const user = await fillRequiredFields();

      await user.click(screen.getByRole("button", { name: "방 만들기" }));
      await waitFor(() => expect(handleSuccess).toHaveBeenCalled());

      const [room] = handleSuccess.mock.calls[0];
      expect(track).toHaveBeenCalledWith("Room Created", {
        room_code: room.code,
        expiry_hours: 24,
        upload_policy: "everyone",
      });
    });
  });
});
