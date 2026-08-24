import { http, HttpResponse } from "msw";

export const roomHandlers = [
  http.post("/rooms", async ({ request }) => {
    const { name, uploadPolicy } = (await request.json()) as {
      name: string;
      uploadPolicy: "everyone" | "host";
      expiryHours: 24 | 72;
    };

    return HttpResponse.json(
      {
        roomId: 5031,
        code: "7K93QX2S",
        name,
        hostId: 10234,
        hostName: "민수",
        createdAt: "2026-08-18T05:30:00Z",
        expiresAt: "2026-08-19T05:30:00Z",
        uploadPolicy,
      },
      { status: 201 },
    );
  }),
];
