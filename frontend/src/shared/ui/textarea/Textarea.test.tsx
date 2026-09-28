import { render, screen } from "@testing-library/react";
import { Textarea } from "./Textarea";

describe("Textarea", () => {
  it("전달받은 값을 보여준다.", () => {
    render(
      <Textarea
        label="문의 내용"
        value="업로드가 느려요"
        maxLength={300}
        onValueChange={jest.fn()}
      />,
    );

    const textarea = screen.getByRole("textbox", { name: "문의 내용" });

    expect(textarea).toHaveValue("업로드가 느려요");
  });
});
