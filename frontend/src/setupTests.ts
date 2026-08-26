import "@testing-library/jest-dom";

import { resetRoomHandlers } from "./mocks/handlers/room";
import { server } from "./mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  // 핸들러가 들고 있는 입장·수정·삭제 상태를 초기화한다.
  resetRoomHandlers();
});

afterAll(() => server.close());
