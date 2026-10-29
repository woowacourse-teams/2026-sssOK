import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { MAX_NAME_LENGTH, NameEntryForm } from "./NameEntryForm";

const getNameInput = () => screen.getByRole("textbox", { name: "표시할 이름" });
const getSubmitButton = () => screen.getByRole("button", { name: /^입장하기/ });

describe("NameEntryForm", () => {
  it("이름 입력 자리표시자와 글자 수를 보여준다", () => {
    render(<NameEntryForm onSubmit={jest.fn()} />);

    expect(screen.getByPlaceholderText("이름을 입력하세요")).toBeInTheDocument();
    expect(screen.getByText(`0/${MAX_NAME_LENGTH}`)).toBeInTheDocument();
  });

  it("이름이 비어 있는 동안 입장하기 버튼은 비활성화된다", () => {
    render(<NameEntryForm onSubmit={jest.fn()} />);

    expect(getSubmitButton()).toBeDisabled();
  });

  it("이름을 입력하면 입장하기 버튼이 활성화되고 글자 수가 갱신된다", async () => {
    const user = userEvent.setup();

    render(<NameEntryForm onSubmit={jest.fn()} />);
    await user.type(getNameInput(), "해니");

    expect(getSubmitButton()).toBeEnabled();
    expect(screen.getByText(`2/${MAX_NAME_LENGTH}`)).toBeInTheDocument();
  });

  it("공백만 입력하면 입장하기 버튼은 비활성화 상태를 유지한다", async () => {
    const user = userEvent.setup();

    render(<NameEntryForm onSubmit={jest.fn()} />);
    await user.type(getNameInput(), "   ");

    expect(getSubmitButton()).toBeDisabled();
  });

  it("입장하기를 누르면 앞뒤 공백을 지운 이름으로 onSubmit을 호출한다", async () => {
    const user = userEvent.setup();
    const handleSubmit = jest.fn();

    render(<NameEntryForm onSubmit={handleSubmit} />);
    await user.type(getNameInput(), "  윤돌  ");
    await user.click(getSubmitButton());

    expect(handleSubmit).toHaveBeenCalledWith("윤돌");
  });

  it(`이름은 ${MAX_NAME_LENGTH}자를 넘겨 입력할 수 없다`, async () => {
    const user = userEvent.setup();

    render(<NameEntryForm onSubmit={jest.fn()} />);
    await user.type(getNameInput(), "가".repeat(MAX_NAME_LENGTH + 3));

    expect(getNameInput()).toHaveValue("가".repeat(MAX_NAME_LENGTH));
    expect(screen.getByText(`${MAX_NAME_LENGTH}/${MAX_NAME_LENGTH}`)).toBeInTheDocument();
  });

  it("각 요소에 입력 조건과 글자 수, 비활성화 이유를 제공한다", async () => {
    const user = userEvent.setup();
    render(<NameEntryForm onSubmit={jest.fn()} />);
    expect(getNameInput()).toBeRequired();
    expect(getNameInput()).toHaveAccessibleDescription(
      "최대 12자까지 입력할 수 있어요. 입력한 이름은 다른 사람에게 보여요.",
    );
    expect(getSubmitButton()).toHaveAccessibleName("입장하기. 이름을 입력해야 입장할 수 있어요.");
    expect(screen.getByText("최대 12자 중 0자 입력")).toBeInTheDocument();
    expect(screen.getByText("0/12").closest("[aria-hidden]")).not.toBeNull();
    await user.type(getNameInput(), " ");
    expect(getNameInput()).toHaveAttribute("aria-invalid", "true");
    expect(getSubmitButton()).toHaveAccessibleName(
      "입장하기. 공백을 제외한 이름을 입력해야 입장할 수 있어요.",
    );
    await user.type(getNameInput(), "윤돌");
    expect(getNameInput()).toHaveAttribute("aria-invalid", "false");
    expect(getSubmitButton()).toHaveAccessibleName("입장하기");
    await user.tab();
    expect(getSubmitButton()).toHaveFocus();
  });

  it("인증 요청 중에는 입력과 입장하기를 막는다", async () => {
    const user = userEvent.setup();
    const handleSubmit = jest.fn();

    render(<NameEntryForm isPending onSubmit={handleSubmit} />);

    expect(getNameInput()).toBeDisabled();
    expect(getSubmitButton()).toBeDisabled();

    await user.click(getSubmitButton());
    expect(handleSubmit).not.toHaveBeenCalled();
  });
});
