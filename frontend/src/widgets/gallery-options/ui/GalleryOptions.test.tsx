import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { GalleryOptions } from "./GalleryOptions";

describe("GalleryOptions", () => {
  it("클릭한 사진 옵션을 전달한다", async () => {
    const user = userEvent.setup();
    const onSelectOption = jest.fn();
    render(
      <GalleryOptions
        selectedOption="all"
        onSelectOption={onSelectOption}
        selectedSort="upload"
        onSelectSort={jest.fn()}
        isAllSelected={false}
        canSelectAll
        onToggleAll={jest.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "전체 필터 열기" }));
    await user.click(screen.getByRole("button", { name: "내 사진" }));

    expect(onSelectOption).toHaveBeenCalledWith("mine");
  });

  it("선택한 정렬 기준을 전달한다", async () => {
    const user = userEvent.setup();
    const onSelectSort = jest.fn();
    render(
      <GalleryOptions
        selectedOption="all"
        onSelectOption={jest.fn()}
        selectedSort="upload"
        onSelectSort={onSelectSort}
        isAllSelected={false}
        canSelectAll
        onToggleAll={jest.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "업로드 순 정렬 열기" }));
    await user.click(screen.getByRole("button", { name: "인기순" }));

    expect(onSelectSort).toHaveBeenCalledWith("popular");
  });

  it("전체 선택 버튼을 누르면 전체 선택 동작을 전달한다", async () => {
    const user = userEvent.setup();
    const onToggleAll = jest.fn();
    render(
      <GalleryOptions
        selectedOption="all"
        onSelectOption={jest.fn()}
        selectedSort="upload"
        onSelectSort={jest.fn()}
        isAllSelected={false}
        canSelectAll
        onToggleAll={onToggleAll}
      />,
    );

    await user.click(screen.getByRole("button", { name: "전체 선택" }));

    expect(onToggleAll).toHaveBeenCalled();
  });
});
