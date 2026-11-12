import { screen } from "@testing-library/react";
import { ROUTES } from "@/shared/config";
import { installApi, renderApp, resetLoginTestState } from "../../../../test/loginIntegration";

beforeEach(() => {
  installApi();
});
afterEach(resetLoginTestState);

// QA: HM-01
it("홈에서 링크 만들기 → 방 생성 화면", async () => {
  const app = renderApp(ROUTES.home);
  await app.user.click(screen.getByRole("button", { name: "링크 만들기" }));
  expect(app.router.state.location.pathname).toBe(ROUTES.createRoom);
  expect(screen.getByRole("textbox", { name: "방 제목" })).toBeInTheDocument();
  expect(screen.getByRole("textbox", { name: "내 이름" })).toBeInTheDocument();
  app.dispose();
});
