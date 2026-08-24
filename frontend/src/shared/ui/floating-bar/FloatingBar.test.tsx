import { render, screen } from "@testing-library/react";
import { FloatingProgressBar } from "./FloatingProgressBar";

test("전달한 children을 그대로 렌더링한다", () => {
  render(
    <FloatingProgressBar>
      <span>20 / 24</span>
      <span>업로드 중... 62%</span>
      <span>취소</span>
    </FloatingProgressBar>,
  );

  expect(screen.getByText("20 / 24")).toBeInTheDocument();
  expect(screen.getByText("업로드 중... 62%")).toBeInTheDocument();
  expect(screen.getByText("취소")).toBeInTheDocument();
});
