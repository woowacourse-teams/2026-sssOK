import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { colors } from "@/shared/styles/tokens";
import { Input } from "./Input";

describe("Input", () => {
  it("입력값과 글자 수를 렌더링한다", () => {
    render(<Input label="이름" value="윤돌" maxLength={10} readOnly />);

    expect(screen.getByRole("textbox", { name: "이름" })).toHaveValue("윤돌");
    expect(screen.getByText("2/10")).toBeInTheDocument();
  });

  it("에러 메시지와 에러 상태를 표시한다", () => {
    render(
      <Input
        label="이름"
        value=""
        maxLength={10}
        errorMessage="이름을 입력해주세요."
        readOnly
      />,
    );

    const input = screen.getByRole("textbox", { name: "이름" });

    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveStyle({ borderColor: colors.borderDanger });
    expect(screen.getByText("이름을 입력해주세요.")).toBeInTheDocument();
  });

  it("에러가 없어도 글자 수 영역을 표시한다", () => {
    render(<Input label="이름" value="" maxLength={10} readOnly />);

    expect(screen.getByRole("textbox", { name: "이름" })).toHaveAttribute(
      "aria-invalid",
      "false",
    );
    expect(screen.getByText("0/10")).toBeInTheDocument();
  });

  it("input의 기본 props와 입력 이벤트를 전달한다", async () => {
    const user = userEvent.setup();
    const handleChange = jest.fn();

    render(
      <Input
        label="이름"
        defaultValue=""
        maxLength={10}
        placeholder="이름을 입력해주세요."
        onChange={handleChange}
      />,
    );

    const input = screen.getByPlaceholderText("이름을 입력해주세요.");
    await user.type(input, "윤돌");

    expect(handleChange).toHaveBeenCalled();
    expect(input).toHaveValue("윤돌");
  });
});
