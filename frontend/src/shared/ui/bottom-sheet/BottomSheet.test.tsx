import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BottomSheet } from "./BottomSheet";

test("title과 children을 렌더링한다", () => {
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.getByText("새 폴더 만들기")).toBeInTheDocument();
  expect(screen.getByText("폴더 이름을 입력해 주세요.")).toBeInTheDocument();
});

test("배경(오버레이)을 클릭하면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <BottomSheet title="새 폴더 만들기" onClose={handleClose}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await user.click(screen.getByTestId("bottom-sheet-overlay"));

  expect(handleClose).not.toHaveBeenCalled();
  fireEvent.animationEnd(screen.getByRole("heading").parentElement!);
  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("시트 내부를 클릭해도 onClose가 호출되지 않는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <BottomSheet title="새 폴더 만들기" onClose={handleClose}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await user.click(screen.getByText("새 폴더 만들기"));

  expect(handleClose).not.toHaveBeenCalled();
});

test("onClose 없이도 렌더링되고 배경 클릭 시 에러가 발생하지 않는다", async () => {
  const user = userEvent.setup();
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await expect(user.click(screen.getByTestId("bottom-sheet-overlay"))).resolves.not.toThrow();
});

test("onClose를 전달하면 닫기 핸들을 렌더링한다", () => {
  render(
    <BottomSheet title="새 폴더 만들기" onClose={jest.fn()}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.getByTestId("bottom-sheet-handle")).toBeInTheDocument();
});

test("onClose를 전달하지 않으면 닫기 핸들을 렌더링하지 않는다", () => {
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.queryByTestId("bottom-sheet-handle")).not.toBeInTheDocument();
});

test("PC 닫기 버튼에 닫기 동작을 연결한다", () => {
  const onClose = jest.fn();
  render(
    <BottomSheet title="다운로드" onClose={onClose}>
      선택 항목
    </BottomSheet>,
  );

  fireEvent.click(screen.getByLabelText("닫기"));

  expect(onClose).not.toHaveBeenCalled();
  fireEvent.animationEnd(screen.getByRole("heading").parentElement!);
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("dialog", { name: "다운로드" })).toBeInTheDocument();
});

test("닫기가 제한된 시트에는 닫기 버튼이 없다", () => {
  render(<BottomSheet title="이름 입력">입력 항목</BottomSheet>);

  expect(screen.queryByLabelText("닫기")).not.toBeInTheDocument();
});

test("열린 동안 배경 스크롤을 잠그고 닫으면 기존 스타일을 복원한다", () => {
  const previousOverflow = document.body.style.overflow;
  document.body.style.overflow = "auto";
  const { unmount } = render(<BottomSheet title="폴더 만들기">내용</BottomSheet>);

  expect(screen.getByRole("dialog")).toHaveAttribute("open");
  expect(document.body.style.overflow).toBe("hidden");
  expect(document.body.style.position).toBe("fixed");

  unmount();

  expect(document.body.style.overflow).toBe("auto");
  expect(document.body.style.position).toBe("");
  document.body.style.overflow = previousOverflow;
});

test("닫기가 제한된 시트는 Escape 취소 이벤트로도 닫히지 않는다", () => {
  render(<BottomSheet title="이름 입력">내용</BottomSheet>);
  const dialog = screen.getByRole("dialog");
  const event = new Event("cancel", { cancelable: true });

  fireEvent(dialog, event);

  expect(event.defaultPrevented).toBe(true);
  expect(dialog).toHaveAttribute("open");
});

test("손잡이를 끌면 시트가 이동하고 80px 이상 내리면 애니메이션 후 닫힌다", () => {
  const onClose = jest.fn();
  render(
    <BottomSheet title="정리 방식" onClose={onClose}>
      내용
    </BottomSheet>,
  );
  const handle = screen.getByTestId("bottom-sheet-handle");
  handle.setPointerCapture = jest.fn();
  const sheet = screen.getByRole("heading").parentElement!;
  const pointer = (type: string, clientY: number): void => {
    const event = new Event(type, { bubbles: true });
    Object.defineProperties(event, {
      pointerId: { value: 1 },
      clientY: { value: clientY },
    });
    fireEvent(handle, event);
  };

  pointer("pointerdown", 100);
  pointer("pointermove", 150);
  expect(sheet.style.transform).toBe("translateY(50px)");
  pointer("pointerup", 150);
  expect(sheet.style.transform).toBe("");
  expect(onClose).not.toHaveBeenCalled();

  pointer("pointerdown", 100);
  pointer("pointermove", 200);
  expect(sheet.style.transform).toBe("translateY(100px)");
  pointer("pointerup", 200);
  expect(sheet.style.transform).toBe("translateY(100px)");
  expect(sheet.style.getPropertyValue("--sheet-exit-offset")).toBe("100px");
  expect(onClose).not.toHaveBeenCalled();
  fireEvent.animationEnd(sheet);
  expect(onClose).toHaveBeenCalledTimes(1);
});
