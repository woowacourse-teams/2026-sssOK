import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { RadioGroup } from "./RadioGroup";

const options = [
  { label: "누구나", value: "everyone" },
  { label: "방장만", value: "host" },
];

describe("RadioGroup", () => {
  it("그룹 라벨과 옵션을 렌더링한다", () => {
    render(
      <RadioGroup
        label="업로드 권한"
        name="uploadPermission"
        value="everyone"
        options={options}
        onValueChange={jest.fn()}
      />,
    );

    expect(screen.getByRole("radiogroup", { name: "업로드 권한" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "누구나" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "방장만" })).not.toBeChecked();
  });

  it("옵션을 선택하면 선택값을 전달한다", async () => {
    const user = userEvent.setup();
    const handleChange = jest.fn();

    render(
      <RadioGroup
        label="업로드 권한"
        name="uploadPermission"
        value="everyone"
        options={options}
        onValueChange={handleChange}
      />,
    );

    await user.click(screen.getByText("방장만"));

    expect(handleChange).toHaveBeenCalledWith("host");
  });
});
