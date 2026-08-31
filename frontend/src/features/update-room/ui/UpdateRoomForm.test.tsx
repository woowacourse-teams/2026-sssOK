import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import type { Room } from "@/entities/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { UpdateRoomForm } from "./UpdateRoomForm";

const room: Room = {
  roomId: 5031,
  code: "7K93QX2S",
  name: "제주 여행",
  status: "ACTIVE",
  hostId: 10234,
  hostName: "민수",
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: "2026-08-19T05:30:00Z",
  uploadPolicy: "everyone",
  joined: true,
  photoCount: 0,
  folders: [],
};

const renderForm = (onSuccess = jest.fn()) => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <UpdateRoomForm room={room} accessToken="mock-token" onSuccess={onSuccess} />
    </QueryClientProvider>,
  );

  return onSuccess;
};

describe("UpdateRoomForm", () => {
  it("수정하지 않은 값은 PATCH 요청에서 제외한다", async () => {
    const user = userEvent.setup();
    const onSuccess = renderForm();
    let requestBody: unknown;

    server.use(
      http.patch(`${API_BASE_URL}/rooms/:roomId`, async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json({ data: { ...room, name: "제주 3박 4일" } });
      }),
    );

    await user.clear(screen.getByRole("textbox", { name: "방 이름" }));
    await user.type(screen.getByRole("textbox", { name: "방 이름" }), "제주 3박 4일");
    await user.click(screen.getByRole("button", { name: "변경 사항 저장" }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(requestBody).toEqual({ name: "제주 3박 4일" });
  });

  it("변경 사항이 없으면 저장할 수 없다", () => {
    renderForm();

    expect(screen.getByRole("button", { name: "변경 사항 저장" })).toBeDisabled();
  });
});
