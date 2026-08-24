import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Button } from "./Button";
import { colors } from "@/shared/styles/tokens";

describe("Button", () => {
  it("버튼 내용을 렌더링한다", () => {
    render(<Button>확인</Button>);

    expect(screen.getByRole("button", { name: "확인" })).toBeInTheDocument();
  });

  it("기본 스타일을 적용한다", () => {
    render(<Button>확인</Button>);

    expect(screen.getByRole("button", { name: "확인" })).toHaveStyle({
      backgroundColor: colors.primary,
    });
  });

  it("default variant에 테두리를 적용한다", () => {
    render(<Button variant="default">취소</Button>);

    expect(screen.getByRole("button", { name: "취소" })).toHaveStyle({
      border: `1.25px solid ${colors.borderDefault}`,
    });
  });

  it("danger variant 스타일을 적용한다", () => {
    render(<Button variant="danger">삭제</Button>);

    expect(screen.getByRole("button", { name: "삭제" })).toHaveStyle({
      backgroundColor: colors.danger,
      color: colors.textInverse,
    });
  });

  it("클릭 이벤트를 실행한다", async () => {
    const user = userEvent.setup();
    const handleClick = jest.fn();

    render(<Button onClick={handleClick}>확인</Button>);

    await user.click(screen.getByRole("button", { name: "확인" }));

    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it("disabled 상태에서는 클릭할 수 없다", async () => {
    const user = userEvent.setup();
    const handleClick = jest.fn();

    render(
      <Button disabled onClick={handleClick}>
        확인
      </Button>,
    );

    const button = screen.getByRole("button", { name: "확인" });

    expect(button).toBeDisabled();

    await user.click(button);

    expect(handleClick).not.toHaveBeenCalled();
  });
});
