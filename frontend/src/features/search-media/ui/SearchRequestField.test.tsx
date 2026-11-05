import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { ComponentProps } from "react";

import { SearchRequestField } from "./SearchRequestField";

const renderField = (props: Partial<ComponentProps<typeof SearchRequestField>> = {}) => {
  const handlers = {
    onSearchKeyword: jest.fn(),
    onSearchUploader: jest.fn(),
    onOpenFolder: jest.fn(),
    onClear: jest.fn(),
  };
  render(
    <SearchRequestField
      appliedText={null}
      folders={[{ id: 7, name: "하루" }]}
      uploaders={[
        { id: 2, name: "소이", count: 21, isMe: false },
        { id: 1, name: "해니", count: 9, isMe: true },
      ]}
      myNickname="해니"
      {...handlers}
      {...props}
    />,
  );
  return handlers;
};

const input = () => screen.getByRole("searchbox", { name: "사진 찾기·정리 요청" });

describe("SearchRequestField", () => {
  it("입력창을 누르면 정리와 찾는 방법을 고르는 패널을 연다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(input());

    expect(screen.getByText("원하는 걸 눌러 주세요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "자동으로 정리하기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사진 내용으로 찾기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "올린 사람으로 찾기" })).toBeInTheDocument();
  });

  it("자동으로 정리하기를 누르면 정리를 요청하고 패널을 닫는다", async () => {
    const user = userEvent.setup();
    const onOrganize = jest.fn();
    renderField({ onOrganize });

    await user.click(input());
    await user.click(screen.getByRole("button", { name: "자동으로 정리하기" }));

    expect(onOrganize).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("region", { name: "요청 패널" })).not.toBeInTheDocument();
  });

  it("사진 내용으로 찾기를 고르면 예시 안내를 보여 주고, 입력한 글로 찾는다", async () => {
    const user = userEvent.setup();
    const { onSearchKeyword } = renderField();

    await user.click(input());
    await user.click(screen.getByRole("button", { name: "사진 내용으로 찾기" }));
    expect(input()).toHaveAttribute("placeholder", "예: 바다에서 찍은 사진");

    await user.type(input(), "  바다에서   찍은 사진{Enter}");

    expect(onSearchKeyword).toHaveBeenCalledWith("바다에서 찍은 사진");
    expect(screen.queryByRole("region", { name: "요청 패널" })).not.toBeInTheDocument();
  });

  it("올린 사람으로 찾기는 이름이 맞는 사람과 장수를 보여 준다", async () => {
    const user = userEvent.setup();
    const { onSearchUploader } = renderField();

    await user.click(input());
    await user.click(screen.getByRole("button", { name: "올린 사람으로 찾기" }));
    await user.type(input(), "소");
    await user.click(screen.getByRole("button", { name: /소이가 올린 사진 찾기/ }));

    expect(onSearchUploader).toHaveBeenCalledWith(expect.objectContaining({ id: 2, name: "소이" }));
  });

  it("올린 사람이 없으면 안내 문구를 보여 준다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(input());
    await user.click(screen.getByRole("button", { name: "올린 사람으로 찾기" }));
    await user.type(input(), "수진");

    expect(screen.getByText("‘수진’ 이름으로 올린 사진이 없어요")).toBeInTheDocument();
  });

  it("폴더가 있는 사람 이름을 입력하면 그 폴더로 보낸다", async () => {
    const user = userEvent.setup();
    const { onOpenFolder } = renderField();

    await user.click(input());
    await user.type(input(), "사진 중에 하루 나온 거");
    await user.click(screen.getByRole("button", { name: /하루 폴더 보기/ }));

    expect(onOpenFolder).toHaveBeenCalledWith(7);
  });

  it("폴더가 없는 사람 이름이면 나눈 뒤에 찾을 수 있다고 안내한다", async () => {
    const user = userEvent.setup();
    const { onSearchKeyword } = renderField();

    await user.click(input());
    await user.type(input(), "소이 나온 사진{Enter}");

    expect(screen.getByText("사람별로 나눈 뒤에 찾을 수 있어요.")).toBeInTheDocument();
    expect(onSearchKeyword).not.toHaveBeenCalled();
  });

  it("아직 못 하는 요청은 안내만 한다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(input());
    await user.type(input(), "잘 나온 사진 골라줘");

    expect(screen.getByText("이 요청은 아직 못 해요.")).toBeInTheDocument();
  });

  it("결과를 보는 동안 입력창에 검색어를 남기고, X 로 지운다", async () => {
    const user = userEvent.setup();
    const { onClear } = renderField({ appliedText: "소이" });

    expect(input()).toHaveValue("소이");

    await user.click(screen.getByRole("button", { name: "요청 닫기·검색어 지우기" }));

    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it("Escape 로 패널을 닫는다", async () => {
    const user = userEvent.setup();
    renderField();

    await user.click(input());
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("region", { name: "요청 패널" })).not.toBeInTheDocument();
  });
  it("정리 중이면 자동으로 정리하기를 막고 까닭을 보여 준다", async () => {
    const user = userEvent.setup();
    const onOrganize = jest.fn();
    renderField({ onOrganize, organizeBlockedReason: "하루 님이 정리하고 있어요" });

    await user.click(input());
    const organize = screen.getByRole("button", { name: /자동으로 정리하기/ });

    expect(organize).toBeDisabled();
    expect(screen.getByText("하루 님이 정리하고 있어요")).toBeInTheDocument();
    await user.click(organize);
    expect(onOrganize).not.toHaveBeenCalled();
  });

  it("정리를 바라는 문장은 시트를 거치지 않고 그 문장으로 시작한다", async () => {
    const user = userEvent.setup();
    const onOrganize = jest.fn();
    const onOrganizeText = jest.fn();
    renderField({ onOrganize, onOrganizeText });

    await user.type(input(), "사람별로 나눠 줘{Enter}");

    expect(onOrganizeText).toHaveBeenCalledWith("사람별로 나눠 줘");
    expect(onOrganize).not.toHaveBeenCalled();
  });
});
