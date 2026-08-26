import "@testing-library/jest-dom";

import { resetJoinedRooms } from "./mocks/handlers/room";
import { resetUploads } from "./mocks/handlers/upload";
import { server } from "./mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  // 목이 들고 있는 입장 기록까지 지워야 다음 테스트가 첫 입장(201)부터 시작한다
  resetJoinedRooms();
  // 발급한 업로드 URL 도 비워야 다음 테스트가 첫 번째 스토리지 키부터 시작한다
  resetUploads();
});

afterAll(() => server.close());
