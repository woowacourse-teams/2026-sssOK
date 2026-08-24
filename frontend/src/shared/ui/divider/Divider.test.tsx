import { render, screen } from "@testing-library/react";
import { Divider } from "./Divider";

test("구분선 역할의 hr을 렌더링한다", () => {
  render(<Divider />);

  expect(screen.getByRole("separator")).toBeInTheDocument();
});
