import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { PREVIEW_STEP_DURATIONS_MS } from "../model/usePreviewStep";

const [LINK_MS, UPLOAD_MS, DOWNLOAD_MS] = PREVIEW_STEP_DURATIONS_MS;
import { OnboardingSection } from "./OnboardingSection";

const renderSection = () => {
  const router = createMemoryRouter([{ path: "*", element: <OnboardingSection /> }], {
    initialEntries: [ROUTES.home],
  });
  render(<RouterProvider router={router} />);
  return router;
};

const mockReducedMotion = (matches: boolean) => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches,
      media: query,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    }),
  });
};

const stepButtons = () =>
  within(screen.getByRole("list", { name: "쏙 사용 방법" })).getAllByRole("button");

const currentStep = () =>
  stepButtons().findIndex((button) => button.getAttribute("aria-current") === "step");

describe("OnboardingSection", () => {
  beforeEach(() => mockReducedMotion(false));

  afterEach(() => {
    jest.useRealTimers();
  });

  it("무엇을 하는지·어떻게 쓰는지·무엇을 누르는지를 한 화면에 보여준다", () => {
    renderSection();

    expect(screen.getByRole("heading", { name: /원하는 것만 쏙 다운 받기!/ })).toBeInTheDocument();

    const steps = within(screen.getByRole("list", { name: "쏙 사용 방법" })).getAllByRole(
      "listitem",
    );
    expect(steps).toHaveLength(3);
    expect(steps[0]).toHaveTextContent(/링크 보내/);
    expect(steps[1]).toHaveTextContent(/업로드 하/);
    expect(steps[2]).toHaveTextContent(/골라 받/);

    expect(screen.getByRole("button", { name: "링크 만들기" })).toBeInTheDocument();
  });

  it("단계 설명은 목록에서 한 번만 읽히고, 움직이는 그림은 읽히지 않는다", () => {
    renderSection();

    const caption = screen.getByText("방을 만들고 단톡방에 링크를 보내요");

    expect(caption.closest("[aria-hidden='true']")).toBeNull();
    // 그림 영역은 통째로 스크린 리더와 키보드에서 빠진다
    const scene = document.querySelector("[inert]");
    expect(scene).toHaveAttribute("aria-hidden", "true");
    expect(scene).not.toContainElement(caption);
  });

  it("단계마다 정해진 시간이 지나면 다음 단계로 넘어가고, 마지막 다음에는 처음으로 돌아간다", () => {
    jest.useFakeTimers();
    renderSection();

    expect(currentStep()).toBe(0);

    act(() => {
      jest.advanceTimersByTime(LINK_MS);
    });
    expect(currentStep()).toBe(1);

    // 단계마다 자기 시간만큼 머문다
    act(() => {
      jest.advanceTimersByTime(UPLOAD_MS - 1);
    });
    expect(currentStep()).toBe(1);

    act(() => {
      jest.advanceTimersByTime(1);
    });
    expect(currentStep()).toBe(2);

    act(() => {
      jest.advanceTimersByTime(DOWNLOAD_MS);
    });
    expect(currentStep()).toBe(0);
  });

  it("움직임을 줄이는 설정이면 단계를 넘기지 않고 세 단계를 정적으로 보여준다", () => {
    mockReducedMotion(true);
    jest.useFakeTimers();
    renderSection();

    expect(currentStep()).toBe(-1);

    act(() => {
      jest.advanceTimersByTime(UPLOAD_MS * 2);
    });
    expect(currentStep()).toBe(-1);
  });

  it("단계를 누르면 그 단계로 건너뛰고, 거기서부터 다시 넘어간다", async () => {
    jest.useFakeTimers();
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    renderSection();

    await user.click(stepButtons()[2]);
    expect(currentStep()).toBe(2);

    act(() => {
      jest.advanceTimersByTime(DOWNLOAD_MS - 1);
    });
    expect(currentStep()).toBe(2);

    act(() => {
      jest.advanceTimersByTime(1);
    });
    expect(currentStep()).toBe(0);
  });

  it("움직임을 줄이는 설정에서도 단계를 누르면 그 단계에 멈춰 보여준다", async () => {
    mockReducedMotion(true);
    jest.useFakeTimers();
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    renderSection();

    await user.click(stepButtons()[1]);
    expect(currentStep()).toBe(1);

    act(() => {
      jest.advanceTimersByTime(UPLOAD_MS * 2);
    });
    expect(currentStep()).toBe(1);
  });

  it("링크 만들기를 누르면 방 만들기 화면으로 간다", async () => {
    const user = userEvent.setup();
    const router = renderSection();

    await user.click(screen.getByRole("button", { name: "링크 만들기" }));

    expect(router.state.location.pathname).toBe(ROUTES.createRoom);
  });

  it("코드로 입장을 누르면 코드 입장 화면으로 간다", async () => {
    const user = userEvent.setup();
    const router = renderSection();

    await user.click(screen.getByRole("button", { name: /코드로 입장/ }));

    expect(router.state.location.pathname).toBe(ROUTES.joinRoom);
  });
});
