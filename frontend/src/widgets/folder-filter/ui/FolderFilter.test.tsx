import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { FolderFilter } from "./FolderFilter";

const folders = [
  {
    id: 501,
    name: "첫째 날",
    createdAt: "2026-08-18T06:10:00Z",
    photoCount: 4,
  },
];

describe("FolderFilter", () => {
  it("클릭한 폴더 ID를 전달한다", async () => {
    const user = userEvent.setup();
    const onSelectFolder = jest.fn();
    render(
      <FolderFilter
        totalCount={13}
        folders={folders}
        selectedFolderId={null}
        onSelectFolder={onSelectFolder}
      />,
    );

    expect(screen.getByRole("button", { name: "전체 사진 및 동영상, 13개" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    const folderButton = screen.getByRole("button", { name: "첫째 날 폴더, 사진 및 동영상 4개" });
    expect(folderButton).toHaveAttribute("aria-pressed", "false");
    await user.click(folderButton);

    expect(onSelectFolder).toHaveBeenCalledWith(501);
  });

  it("추가 버튼을 누르면 폴더 생성 동작을 호출한다", async () => {
    const user = userEvent.setup();
    const onAddFolder = jest.fn();

    render(
      <FolderFilter
        totalCount={13}
        folders={folders}
        selectedFolderId={null}
        onSelectFolder={jest.fn()}
        onAddFolder={onAddFolder}
      />,
    );

    await user.click(screen.getByRole("button", { name: "폴더 추가" }));
    expect(onAddFolder).toHaveBeenCalledTimes(1);
  });
});
