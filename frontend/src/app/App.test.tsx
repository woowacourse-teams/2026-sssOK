import { render } from "@testing-library/react";
import App from "./App";

test("App 컴포넌트를 렌더링한다", () => {
  const { container } = render(<App />);

  expect(container.querySelector(".container")).toBeInTheDocument();
});
