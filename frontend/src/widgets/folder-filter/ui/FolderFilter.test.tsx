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

    await user.click(screen.getByRole("button", { name: "첫째 날 4" }));

    expect(onSelectFolder).toHaveBeenCalledWith(501);
  });
});
