import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { routes } from "./routes";

const ROOM_CODE = "7K93QX2S";

const renderAt = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
};

describe("라우트", () => {
  it("/ 는 홈 화면을 보여준다", () => {
    renderAt(ROUTES.home);

    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });

  // 방 조회 결과에 따른 화면 분기는 RoomEntryPage.test.tsx 가 검증한다.
  // 여기서는 경로가 그 화면으로 연결되는지만 본다.
  it("/rooms/:code 는 방 입장 화면을 보여준다", () => {
    renderAt(ROUTES.roomEntry(ROOM_CODE));

    expect(screen.getByText(/불러오는 중/)).toBeInTheDocument();
  });

  it("/rooms/:code/gallery 는 갤러리 화면을 보여준다", () => {
    renderAt(ROUTES.gallery(ROOM_CODE));

    expect(screen.getByText(/갤러리 화면/)).toBeInTheDocument();
  });

  it("방 생성에 성공하면 생성된 방의 갤러리로 이동한다", async () => {
    const user = userEvent.setup();
    const router = renderAt(ROUTES.createRoom);

    await user.type(screen.getByRole("textbox", { name: "내 이름" }), "민수");
    await user.type(screen.getByRole("textbox", { name: "방 이름" }), "제주 여행");
    await user.click(screen.getByRole("button", { name: "방 만들기" }));

    expect(await screen.findByText(/갤러리 화면/)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.gallery("7K93QX2S"));
  });

  it("알 수 없는 주소는 홈으로 보낸다", () => {
    const router = renderAt("/이런-주소는-없다");

    expect(router.state.location.pathname).toBe(ROUTES.home);
    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });
});
