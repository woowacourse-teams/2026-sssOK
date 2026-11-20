import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { RoomClosedPage } from "./RoomClosedPage";

const renderPage = (): ReturnType<typeof createMemoryRouter> => {
  const router = createMemoryRouter(
    [
      { path: ROUTES.closedRoom, element: <RoomClosedPage /> },
      { path: ROUTES.rooms, element: <p>방 목록</p> },
    ],
    { initialEntries: [ROUTES.closedRoom] },
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
};

afterEach(() => {
  delete window.ReactNativeWebView;
});

it("브라우저 게스트에게는 닫힌 방 안내와 랜딩으로 가는 링크를 보여준다", () => {
  renderPage();
  expect(screen.getByText("이 방은 사라졌어요")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "홈으로 가기" })).toHaveAttribute("href", ROUTES.landing);
});

it("앱에서는 닫힌 방 안내 대신 방 목록으로 이동하며 현재 기록을 교체한다", async () => {
  window.ReactNativeWebView = { postMessage: jest.fn() };
  const router = renderPage();
  await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.rooms));
  expect(router.state.historyAction).toBe("REPLACE");
  expect(screen.queryByText("이 방은 사라졌어요")).not.toBeInTheDocument();
});
