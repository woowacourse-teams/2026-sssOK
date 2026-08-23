import { render, screen } from "@testing-library/react";

import { colors } from "@/shared/styles/tokens";
import { Badge } from "./Badge";

describe("Badge", () => {
  it("배지 내용을 렌더링한다", () => {
    render(<Badge>나</Badge>);

    expect(screen.getByText("나")).toBeInTheDocument();
  });

  it("primary variant 스타일을 적용한다", () => {
    render(<Badge variant="primary">윤돌</Badge>);

    expect(screen.getByText("윤돌")).toHaveStyle({
      backgroundColor: colors.primary,
      color: colors.textInverse,
    });
  });
});
