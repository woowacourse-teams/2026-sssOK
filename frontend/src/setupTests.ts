import "@testing-library/jest-dom";

import { resetJoinedRooms } from "./mocks/handlers/room";
import { server } from "./mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  // 목이 들고 있는 입장 기록까지 지워야 다음 테스트가 첫 입장(201)부터 시작한다
  resetJoinedRooms();
});

afterAll(() => server.close());
