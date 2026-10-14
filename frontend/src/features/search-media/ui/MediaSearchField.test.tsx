import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { ComponentProps } from "react";

import { MediaSearchField } from "./MediaSearchField";

const renderField = (props: Partial<ComponentProps<typeof MediaSearchField>> = {}) =>
  render(
    <MediaSearchField
      searchType="keyword"
      onSearchTypeChange={jest.fn()}
      onSearch={jest.fn()}
      onClose={jest.fn()}
      {...props}
    />,
  );

describe("MediaSearchField", () => {
  it("열리면 검색창에 바로 입력할 수 있다", () => {
    renderField();

    expect(screen.getByRole("searchbox", { name: "사진 검색" })).toHaveFocus();
    expect(screen.getByPlaceholderText("찾고 싶은 사진을 검색해주세요!")).toBeInTheDocument();
  });

  it("제출하면 공백을 정리한 검색어로 검색한다", async () => {
    const user = userEvent.setup();
    const onSearch = jest.fn();
    renderField({ onSearch });

    await user.type(screen.getByRole("searchbox"), "  바다에서   찍은 사진 {Enter}");

    expect(onSearch).toHaveBeenCalledWith("바다에서 찍은 사진");
  });

  it("입력하는 동안에는 검색하지 않고, 빈 검색어는 제출하지 않는다", async () => {
    const user = userEvent.setup();
    const onSearch = jest.fn();
    renderField({ onSearch });

    await user.type(screen.getByRole("searchbox"), "   ");
    expect(onSearch).not.toHaveBeenCalled();

    await user.keyboard("{Enter}");
    expect(onSearch).not.toHaveBeenCalled();
  });

  it("닫기 버튼을 누르면 접히는 애니메이션이 끝난 뒤 한 번만 닫는다", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    renderField({ onClose });

    await user.click(screen.getByRole("button", { name: "검색 닫기" }));
    await user.click(screen.getByRole("button", { name: "검색 닫기" }));

    expect(onClose).not.toHaveBeenCalled();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("Escape 로도 검색을 닫는다", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    renderField({ onClose });

    await user.type(screen.getByRole("searchbox"), "{Escape}");

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("기본 검색 기준은 검색어다", () => {
    renderField();

    expect(screen.getByRole("button", { name: "검색 기준: 검색어" })).toBeInTheDocument();
  });

  it("닉네임 기준이면 닉네임 안내 문구를 보여준다", () => {
    renderField({ searchType: "nickname" });

    expect(screen.getByPlaceholderText("닉네임을 입력해주세요!")).toBeInTheDocument();
  });

  it("검색 기준 메뉴는 지금 기준에 체크하고 각 기준의 설명을 보여준다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(screen.getByRole("button", { name: "검색 기준: 검색어" }));

    expect(screen.getByRole("menuitemradio", { name: /검색어/ })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByRole("menuitemradio", { name: /닉네임/ })).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByText("올린 사람으로 찾기")).toBeInTheDocument();
  });

  it("검색 기준을 바꾸면 알리고, 쓰던 검색어를 비운 뒤 다시 입력할 수 있게 한다", async () => {
    const user = userEvent.setup();
    const onSearchTypeChange = jest.fn();
    renderField({ onSearchTypeChange });

    await user.type(screen.getByRole("searchbox"), "바다");
    await user.click(screen.getByRole("button", { name: "검색 기준: 검색어" }));
    await user.click(screen.getByRole("menuitemradio", { name: /닉네임/ }));

    expect(onSearchTypeChange).toHaveBeenCalledWith("nickname");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox")).toHaveValue("");
    expect(screen.getByRole("searchbox")).toHaveFocus();
  });

  it("지금 기준을 다시 고르면 검색어를 그대로 둔다", async () => {
    const user = userEvent.setup();
    const onSearchTypeChange = jest.fn();
    renderField({ onSearchTypeChange });

    await user.type(screen.getByRole("searchbox"), "바다");
    await user.click(screen.getByRole("button", { name: "검색 기준: 검색어" }));
    await user.click(screen.getByRole("menuitemradio", { name: /검색어/ }));

    expect(onSearchTypeChange).not.toHaveBeenCalled();
    expect(screen.getByRole("searchbox")).toHaveValue("바다");
  });

  it("메뉴가 열린 채 Escape 를 누르면 메뉴만 닫고 검색은 닫지 않는다", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    renderField({ onClose });

    await user.click(screen.getByRole("button", { name: "검색 기준: 검색어" }));
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 500));
    expect(onClose).not.toHaveBeenCalled();
  });
});
