import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { RoomMenuButton } from "./RoomMenuButton";

describe("RoomMenuButton", () => {
  it("메뉴 버튼을 누르면 방 메뉴를 열고 항목 선택 후 닫는다", async () => {
    const user = userEvent.setup();
    const onOpenSettings = jest.fn();

    render(<RoomMenuButton isHost onOpenSettings={onOpenSettings} />);

    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));

    expect(screen.getByRole("button", { name: "방 설정" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "방 삭제" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "방 설정" }));

    expect(onOpenSettings).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: "방 설정" })).not.toBeInTheDocument();
  });

  it("일반 참여자에게는 폴더 메뉴만 보여준다", async () => {
    const user = userEvent.setup();
    render(<RoomMenuButton />);

    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));

    expect(screen.queryByRole("button", { name: "방 설정" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "방 삭제" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 추가" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 설정" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "폴더 삭제" })).toBeDisabled();
  });

  it("메뉴 바깥을 누르면 닫는다", async () => {
    const user = userEvent.setup();
    render(<RoomMenuButton />);

    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByTestId("dropdown-menu-overlay"));

    expect(screen.queryByRole("button", { name: "방 설정" })).not.toBeInTheDocument();
  });
});
