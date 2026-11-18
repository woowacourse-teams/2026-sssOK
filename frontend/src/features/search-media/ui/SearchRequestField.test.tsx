import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { ComponentProps } from "react";

import { SearchRequestField } from "./SearchRequestField";

const renderField = (
  props: Partial<ComponentProps<typeof SearchRequestField>> = {},
): {
  onSearchKeyword: ReturnType<typeof jest.fn>;
  onSearchUploader: ReturnType<typeof jest.fn>;
  onClear: ReturnType<typeof jest.fn>;
} => {
  const handlers = {
    onSearchKeyword: jest.fn(),
    onSearchUploader: jest.fn(),
    onClear: jest.fn(),
  };
  render(
    <SearchRequestField
      appliedText={null}
      uploaders={[
        { id: 2, name: "소이", count: 21, isMe: false },
        { id: 1, name: "해니", count: 9, isMe: true },
      ]}
      {...handlers}
      {...props}
    />,
  );
  return handlers;
};

const input = (): HTMLElement => screen.getByRole("searchbox", { name: "사진 검색" });
const panel = (): HTMLElement | null => screen.queryByRole("region", { name: "검색 제안" });
const suggestions = (): string[] =>
  screen
    .getAllByRole("button")
    .map((button) => button.querySelector("strong")?.textContent)
    .filter((label): label is string => Boolean(label));

describe("SearchRequestField", () => {
  it("사진 내용과 이름으로 찾을 수 있다고 안내한다", () => {
    renderField();

    expect(input()).toHaveAttribute("placeholder", "사진 내용이나 이름으로 검색해 보세요");
  });

  it("입력창을 눌러도 글을 입력하기 전에는 패널을 열지 않는다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(input());

    expect(panel()).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "검색 닫기·검색어 지우기" })).toBeInTheDocument();
  });

  it("올린 사람 이름을 입력하면 그 사람이 올린 사진을 먼저, 내용 검색을 다음에 제안한다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.type(input(), "소이");

    expect(suggestions()).toEqual(["소이가 올린 사진 보기", "‘소이’로 검색하기"]);
    expect(screen.getByText("21장을 올렸어요")).toBeInTheDocument();
  });

  it("이름 일부만 입력해도 맞는 사람을 제안한다", async () => {
    const user = userEvent.setup();
    const { onSearchUploader } = renderField();

    await user.type(input(), "소");
    await user.click(screen.getByRole("button", { name: /소이가 올린 사진/ }));

    expect(onSearchUploader).toHaveBeenCalledWith(expect.objectContaining({ id: 2, name: "소이" }));
    expect(panel()).not.toBeInTheDocument();
  });

  it("'나' 를 입력하면 내가 올린 사진을 제안한다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.type(input(), "나");

    expect(suggestions()[0]).toBe("내가 올린 사진 보기");
  });

  it("이름과 맞지 않는 글은 내용 검색 한 줄만 보여 주고, Enter 로 찾는다", async () => {
    const user = userEvent.setup();
    const { onSearchKeyword } = renderField();

    await user.type(input(), "  바다에서   찍은 사진");
    expect(suggestions()).toEqual(["‘바다에서 찍은 사진’으로 검색하기"]);
    expect(screen.getByText("사진 내용으로 찾아요")).toBeInTheDocument();

    await user.keyboard("{Enter}");

    expect(onSearchKeyword).toHaveBeenCalledWith("바다에서 찍은 사진");
    expect(panel()).not.toBeInTheDocument();
  });

  it("Enter 는 첫 줄을 실행한다", async () => {
    const user = userEvent.setup();
    const { onSearchUploader, onSearchKeyword } = renderField();

    await user.type(input(), "소이{Enter}");

    expect(onSearchUploader).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
    expect(onSearchKeyword).not.toHaveBeenCalled();
  });

  it("정리나 아직 못 하는 요청처럼 보이는 글도 안내 없이 내용 검색으로 제안한다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.type(input(), "사람별로 나눠 줘");

    expect(suggestions()).toEqual(["‘사람별로 나눠 줘’로 검색하기"]);
    expect(screen.queryByText(/✨/)).not.toBeInTheDocument();
    expect(screen.queryByText(/아직 못 해요/)).not.toBeInTheDocument();
  });

  it("결과를 보는 동안 입력창에 적용한 글을 남기고, X 로 지운다", async () => {
    const user = userEvent.setup();
    const { onClear } = renderField({ appliedText: "소이가 올린 사진" });

    expect(input()).toHaveValue("소이가 올린 사진");

    await user.click(screen.getByRole("button", { name: "검색 닫기·검색어 지우기" }));

    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it("Escape 로 패널을 닫는다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.type(input(), "소이");
    await user.keyboard("{Escape}");

    expect(panel()).not.toBeInTheDocument();
  });
});
