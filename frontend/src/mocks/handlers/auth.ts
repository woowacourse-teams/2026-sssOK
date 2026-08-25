import { http, HttpResponse } from "msw";

import { API_PREFIX } from "../config";

export const authHandlers = [
  http.post(`${API_PREFIX}/auth/anonymous`, async ({ request }) => {
    const { nickname } = (await request.json()) as { nickname: string };

    return HttpResponse.json(
      {
        data: {
          accessToken: "mock-access-token",
          userId: 10234,
          nickname,
          expiresAt: "2026-09-17T05:30:00Z",
        },
      },
      { status: 201 },
    );
  }),
];
