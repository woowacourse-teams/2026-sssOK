import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { colors } from "@/shared/styles/tokens";
import { IconButton } from "./IconButton";

const CloseIcon = () => <svg data-testid="close-icon" aria-hidden="true" />;

describe("IconButton", () => {
  it("아이콘과 기본 속성을 렌더링한다", () => {
    render(
      <IconButton aria-label="닫기">
        <CloseIcon />
      </IconButton>,
    );

    expect(screen.getByRole("button", { name: "닫기" })).toHaveAttribute("type", "button");
    expect(screen.getByTestId("close-icon")).toBeInTheDocument();
  });

  it("클릭 이벤트를 실행한다", async () => {
    const user = userEvent.setup();
    const handleClick = jest.fn();

    render(
      <IconButton aria-label="닫기" onClick={handleClick}>
        <CloseIcon />
      </IconButton>,
    );

    await user.click(screen.getByRole("button", { name: "닫기" }));

    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it("danger variant 스타일을 적용한다", () => {
    render(
      <IconButton aria-label="삭제" variant="danger">
        <CloseIcon />
      </IconButton>,
    );

    expect(screen.getByRole("button", { name: "삭제" })).toHaveStyle({
      color: colors.danger,
    });
  });
});
