import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { routes } from "@/app/routes";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";

const renderRoomList = () => {
  const router = createMemoryRouter(routes, { initialEntries: [ROUTES.rooms] });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
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

    expect(await screen.findByText("‘동아리 MT’에서 나갔어요.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /동아리 MT/ })).not.toBeInTheDocument();
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
    expect(screen.getByRole("button", { name: "초대 링크로 들어가기" })).toBeInTheDocument();
  });

  it("붙여넣은 초대 링크로 들어가면 링크 입장 흐름으로 간다", async () => {
    const user = userEvent.setup();
    const router = renderRoomList();

    await user.click(await screen.findByRole("button", { name: /방 추가/ }));
    await user.click(screen.getByRole("button", { name: /초대 링크 붙여넣기/ }));

    const input = screen.getByLabelText("초대 링크");
    await user.type(input, "https://example.com/nope");
    await user.click(screen.getByRole("button", { name: "들어가기" }));
    expect(screen.getByText(/쏙 초대 링크가 아니에요/)).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "https://ssok.app/join/7K93QX2S");
    await user.click(screen.getByRole("button", { name: "들어가기" }));

    expect(router.state.location.pathname).toBe(ROUTES.roomEntry("7K93QX2S"));
  });

  it("목록을 불러오지 못하면 다시 불러오기 버튼을 보여준다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/members/me/rooms`, () =>
        HttpResponse.json({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }, { status: 401 }),
      ),
    );
    renderRoomList();

    expect(await screen.findByText("방 목록을 불러오지 못했어요.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 불러오기" })).toBeInTheDocument();
  });
});
