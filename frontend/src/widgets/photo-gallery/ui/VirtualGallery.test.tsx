import { render, screen } from "@testing-library/react";
import { VirtualGallery } from "./VirtualGallery";

it("2000개 ID를 유지하면서 주변 카드만 마운트하고 선택 변경을 반영한다", () => {
  const ids = Array.from({ length: 2000 }, (_, index) => index + 1);
  const card = (selected: boolean): ((index: number) => import("react").ReactNode) =>
    function TestCard(index: number): import("react").ReactNode {
      return <button aria-pressed={selected}>사진 {ids[index]}</button>;
    };
  const { container, rerender } = render(
    <VirtualGallery itemIds={ids} selectedIds={[]} renderCard={card(false)} />,
  );
  const count = container.querySelectorAll("[data-gallery-index]").length;
  expect(count).toBeGreaterThan(0);
  expect(count).toBeLessThan(200);
  expect(screen.queryByRole("button", { name: "사진 2000" })).not.toBeInTheDocument();
  rerender(<VirtualGallery itemIds={ids} selectedIds={ids} renderCard={card(true)} />);
  expect(screen.getByRole("button", { name: "사진 1" })).toHaveAttribute("aria-pressed", "true");
  expect(container.querySelectorAll("[data-gallery-index]")).toHaveLength(count);
});
