import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { MediaSearchField } from "./MediaSearchField";

describe("MediaSearchField", () => {
  it("열리면 검색창에 바로 입력할 수 있다", () => {
    render(<MediaSearchField onSearch={jest.fn()} onClose={jest.fn()} />);

    expect(screen.getByRole("searchbox", { name: "사진 검색" })).toHaveFocus();
    expect(screen.getByPlaceholderText("찾고 싶은 사진을 검색해주세요!")).toBeInTheDocument();
  });

  it("제출하면 공백을 정리한 검색어로 검색한다", async () => {
    const user = userEvent.setup();
    const onSearch = jest.fn();
    render(<MediaSearchField onSearch={onSearch} onClose={jest.fn()} />);

    await user.type(screen.getByRole("searchbox"), "  바다에서   찍은 사진 {Enter}");

    expect(onSearch).toHaveBeenCalledWith("바다에서 찍은 사진");
  });

  it("입력하는 동안에는 검색하지 않고, 빈 검색어는 제출하지 않는다", async () => {
    const user = userEvent.setup();
    const onSearch = jest.fn();
    render(<MediaSearchField onSearch={onSearch} onClose={jest.fn()} />);

    await user.type(screen.getByRole("searchbox"), "   ");
    expect(onSearch).not.toHaveBeenCalled();

    await user.keyboard("{Enter}");
    expect(onSearch).not.toHaveBeenCalled();
  });

  it("닫기 버튼을 누르면 접히는 애니메이션이 끝난 뒤 한 번만 닫는다", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    render(<MediaSearchField onSearch={jest.fn()} onClose={onClose} />);

    await user.click(screen.getByRole("button", { name: "검색 닫기" }));
    await user.click(screen.getByRole("button", { name: "검색 닫기" }));

    expect(onClose).not.toHaveBeenCalled();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("Escape 로도 검색을 닫는다", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    render(<MediaSearchField onSearch={jest.fn()} onClose={onClose} />);

    await user.type(screen.getByRole("searchbox"), "{Escape}");

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });
});
