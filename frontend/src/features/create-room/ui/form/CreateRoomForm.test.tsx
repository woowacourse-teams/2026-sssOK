import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { CreateRoomForm } from "./CreateRoomForm";

describe("CreateRoomForm", () => {
  it("입력값을 하나의 객체로 전달한다", async () => {
    const user = userEvent.setup();
    const handleSubmit = jest.fn();

    render(<CreateRoomForm onSubmit={handleSubmit} />);

    const submitButton = screen.getByRole("button", { name: "방 만들기" });
    expect(submitButton).toBeDisabled();

    await user.type(screen.getByRole("textbox", { name: "내 이름" }), "민수");
    await user.type(screen.getByRole("textbox", { name: "방 이름" }), "제주 여행");
    await user.click(screen.getByRole("radio", { name: "방장만" }));
    await user.click(screen.getByRole("radio", { name: "3일" }));
    await user.click(submitButton);

    expect(handleSubmit).toHaveBeenCalledWith({
      nickname: "민수",
      name: "제주 여행",
      uploadPolicy: "host",
      expiryHours: "72",
    });
  });
});
