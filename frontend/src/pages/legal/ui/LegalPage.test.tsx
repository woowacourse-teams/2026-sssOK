import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";

import { routes } from "@/app/routes";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";

const renderPage = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
};

it.each([
  [ROUTES.privacy, "개인정보처리방침"],
  [ROUTES.terms, "이용약관"],
])("%s는 로그인 없이 직접 열리고 세션 복원 요청을 보내지 않는다", (path, title) => {
  const refresh = jest.fn();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => {
      refresh();
      return new HttpResponse(null, { status: 401 });
    }),
  );
  const router = renderPage(path);
  expect(router.state.location.pathname).toBe(path);
  expect(screen.getByRole("heading", { level: 1, name: title })).toBeInTheDocument();

  expect(screen.getAllByRole("link", { name: "yundoll777@gmail.com" })[0]).toHaveAttribute(
    "href",
    "mailto:yundoll777@gmail.com",
  );
  expect(refresh).not.toHaveBeenCalled();
});

it("목차를 펼쳐 읽고 정책 사이를 이동해도 동의 완료로 처리하지 않는다", async () => {
  const user = userEvent.setup();
  const router = renderPage(ROUTES.privacy);
  await user.click(screen.getByText("목차 펼치기"));
  expect(screen.getByText("목차 펼치기").parentElement).toHaveAttribute("open");
  await user.click(screen.getByRole("link", { name: "이용약관" }));
  expect(router.state.location.pathname).toBe(ROUTES.terms);
  expect(screen.getByRole("heading", { level: 1, name: "이용약관" })).toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /동의/ })).not.toBeInTheDocument();
});
