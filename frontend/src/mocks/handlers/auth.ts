import { http, HttpResponse } from "msw";

export const authHandlers = [
  http.post("/auth/anonymous", async ({ request }) => {
    const { nickname } = (await request.json()) as {
      nickname: string;
    };

    return HttpResponse.json(
      {
        accessToken: "mock-access-token",
        userId: 10234,
        nickname,
        expiresAt: "2026-09-17T05:30:00Z",
      },
      { status: 201 },
    );
  }),
];
