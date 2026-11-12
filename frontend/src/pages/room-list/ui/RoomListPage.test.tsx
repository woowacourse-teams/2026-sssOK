import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import { render } from "../../../../test/renderWithToast";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { routes } from "@/app/routes";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";

const renderRoomList = () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () =>
      HttpResponse.json({
        data: {
          accessToken: "mock-token-10234",
          userId: 10234,
          nickname: "회원",
          memberStatus: "REGISTERED",
          expiresAt: "2099-01-01T00:00:00Z",
          refreshExpiresAt: "2099-02-01T00:00:00Z",
        },
      }),
    ),
  );
  const router = createMemoryRouter(routes, { initialEntries: [ROUTES.rooms] });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, {
    accessToken: "mock-token-10234",
    userId: 10234,
    nickname: "회원",
    memberStatus: "REGISTERED",
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    refreshExpiresAt: "2099-01-01T00:00:00Z",
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
};

const cardOf = async (name: string) =>
  (await screen.findByRole("link", { name: new RegExp(name) })).closest("li") as HTMLElement;

describe("방 목록 화면", () => {
  it("초대 링크를 복사하면 메뉴가 닫히고 전역 완료 알림이 표시된다", async () => {
    const user = userEvent.setup();
    const copy = jest.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    renderRoomList();

    await user.click(await screen.findByRole("button", { name: "동아리 MT 메뉴" }));
    await user.click(screen.getByRole("button", { name: "초대 링크 복사" }));

    expect(await screen.findByRole("status")).toHaveTextContent("초대 링크를 복사했어요.");
    expect(copy).toHaveBeenCalledWith(expect.stringContaining("/join/"));
    expect(screen.queryByTestId("dropdown-menu")).not.toBeInTheDocument();
    copy.mockRestore();
  });

  it("초대 링크 복사가 실패하면 전역 실패 알림이 표시된다", async () => {
    const user = userEvent.setup();
    const copy = jest
      .spyOn(navigator.clipboard, "writeText")
      .mockRejectedValue(new Error("denied"));
    renderRoomList();

    await user.click(await screen.findByRole("button", { name: "동아리 MT 메뉴" }));
    await user.click(screen.getByRole("button", { name: "초대 링크 복사" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("링크를 복사하지 못했어요.");
    expect(screen.queryByTestId("dropdown-menu")).not.toBeInTheDocument();
    copy.mockRestore();
  });

  it("들어간 방을 사진 수·참여 인원과 함께 보여주고, 내가 만든 방에만 방장 표시를 붙인다", async () => {
    renderRoomList();

    const hostCard = await cardOf("제주 졸업여행");
    expect(within(hostCard).getByText("방장")).toBeInTheDocument();
    expect(within(hostCard).getByText("사진 128 · 14명")).toBeInTheDocument();

    const memberCard = await cardOf("동아리 MT");
    expect(within(memberCard).queryByText("방장")).not.toBeInTheDocument();
  });

  it("필터를 고르면 그 종류의 방만 보이고 칩에 개수가 붙는다", async () => {
    const user = userEvent.setup();
    renderRoomList();

    await user.click(await screen.findByRole("button", { name: /내가 만든 방 1/ }));

    expect(screen.getByRole("link", { name: /제주 졸업여행/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /동아리 MT/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /참여한 방 2/ }));

    expect(screen.queryByRole("link", { name: /제주 졸업여행/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /동아리 MT/ })).toBeInTheDocument();
  });

  it("카드를 누르면 그 방 입장 화면으로 간다", async () => {
    const user = userEvent.setup();
    const router = renderRoomList();

    await user.click(await screen.findByRole("link", { name: /동아리 MT/ }));

    expect(router.state.location.pathname).toBe(ROUTES.roomEntry("QRST6789"));
  });

  it("참여한 방에서 나가면 확인 후 목록에서 빠진다", async () => {
    const user = userEvent.setup();
    renderRoomList();

    await user.click(await screen.findByRole("button", { name: "동아리 MT 메뉴" }));
    await user.click(screen.getByRole("button", { name: "방 나가기" }));

    const dialog = screen.getByRole("dialog");
    expect(
      within(dialog).getByRole("heading", { name: /‘동아리 MT’에서\s*나갈까요\?/ }),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "나가기" }));

    await waitFor(() => {
      expect(screen.queryByRole("link", { name: /동아리 MT/ })).not.toBeInTheDocument();
    });
  });

  it("내가 만든 방의 메뉴에는 나가기 대신 방 설정이 있다", async () => {
    const user = userEvent.setup();
    renderRoomList();

    await user.click(await screen.findByRole("button", { name: "제주 졸업여행 메뉴" }));

    expect(screen.getByRole("button", { name: "방 설정" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "방 나가기" })).not.toBeInTheDocument();
  });

  it("들어간 방이 없으면 빈 화면과 방 만들기·초대 링크 버튼을 보여준다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/members/me/rooms`, () =>
        HttpResponse.json({ data: { rooms: [] } }),
      ),
    );
    renderRoomList();

    expect(
      await screen.findByRole("heading", { name: "아직 들어간 방이 없어요" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "방 만들기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "링크로 참여" })).toBeInTheDocument();
  });

  it("붙여넣은 초대 링크의 안내 화면에서 웹 참여로 방에 입장한다", async () => {
    const user = userEvent.setup();
    const router = renderRoomList();

    await user.click(await screen.findByRole("button", { name: "링크로 참여" }));

    const input = screen.getByLabelText("초대 링크");
    await user.type(input, "https://example.com/nope");
    await user.click(screen.getByRole("button", { name: "들어가기" }));
    expect(screen.getByText(/쏙 초대 링크가 아니에요/)).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "https://ssok.app/join/7K93QX2S");
    await user.click(screen.getByRole("button", { name: "들어가기" }));

    expect(router.state.location.pathname).toBe(ROUTES.roomInvite("7K93QX2S"));
    expect(screen.getByRole("heading", { name: "쏙 방에 초대됐어요" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "웹에서 참여하기" }));

    expect(router.state.location.pathname).toBe(ROUTES.roomEntry("7K93QX2S"));
  });

  it("목록 조회 실패 후 다시 불러오면 방 목록을 보여준다", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(
        `${API_BASE_URL}/members/me/rooms`,
        () => HttpResponse.json({ code: "INTERNAL_SERVER_ERROR" }, { status: 500 }),
        { once: true },
      ),
    );
    renderRoomList();

    expect(await screen.findByText("방 목록을 불러오지 못했어요.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "다시 불러오기" }));
    expect(await screen.findByRole("link", { name: /제주 졸업여행/ })).toBeInTheDocument();
    expect(screen.queryByText("방 목록을 불러오지 못했어요.")).not.toBeInTheDocument();
  });
});
