import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { MOCK_ROOM_CODES } from "@/mocks/handlers/room";
import { ROUTE_PATTERNS } from "@/shared/config";
import { RoomEntryPage } from "./RoomEntryPage";

const renderAt = (code: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/rooms/${code}`]}>
        <Routes>
          <Route path={ROUTE_PATTERNS.roomEntry} element={<RoomEntryPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

describe("RoomEntryPage", () => {
  it("조회하는 동안 로딩 문구를 보여준다", () => {
    renderAt(MOCK_ROOM_CODES.active);

    expect(screen.getByText(/불러오는 중/)).toBeInTheDocument();
  });

  it("활성 방은 방 이름을 보여준다", async () => {
    renderAt(MOCK_ROOM_CODES.active);

    expect(await screen.findByText(/제주 여행/)).toBeInTheDocument();
  });

  it("만료된 방은 이름과 함께 만료 안내를 보여준다", async () => {
    renderAt(MOCK_ROOM_CODES.expired);

    expect(await screen.findByText(/만료된 방이에요/)).toBeInTheDocument();
    expect(screen.getByText(/제주 여행/)).toBeInTheDocument();
  });

  it("삭제된 방은 삭제 안내를 보여준다", async () => {
    renderAt(MOCK_ROOM_CODES.deleted);

    expect(await screen.findByText(/삭제된 방이에요/)).toBeInTheDocument();
  });

  it("존재하지 않는 방은 없는 방이라고 안내한다", async () => {
    renderAt(MOCK_ROOM_CODES.notFound);

    expect(await screen.findByText("존재하지 않는 방이에요.")).toBeInTheDocument();
  });

  it("형식이 틀린 코드는 코드 형식 문제라고 안내한다", async () => {
    renderAt(MOCK_ROOM_CODES.invalid);

    expect(await screen.findByText("방 코드 형식이 올바르지 않아요.")).toBeInTheDocument();
  });
});
