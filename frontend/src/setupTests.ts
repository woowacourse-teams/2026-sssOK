import "@testing-library/jest-dom";

import { resetNicknames } from "./mocks/handlers/auth";
import { resetDownloadJobs } from "./mocks/handlers/download";
import { resetRoomHandlers } from "./mocks/handlers/room";
import { resetUploads } from "./mocks/handlers/upload";
import { server } from "./mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  // 방의 입장·수정·삭제 상태를 지워야 다음 테스트가 초기 상태부터 시작한다.
  resetRoomHandlers();
  // 발급한 업로드 URL 도 비워야 다음 테스트가 첫 번째 미디어 번호부터 시작한다
  resetUploads();
  // 인증하며 기억한 이름도 지운다 — 업로드 등록 응답의 uploaderName 이 앞 테스트를 물고 오면 안 된다
  resetNicknames();
  // 압축 잡도 비운다 — 앞 테스트의 잡 번호가 남으면 "없는 잡" 검사가 통과하지 않는다
  resetDownloadJobs();
});

afterAll(() => server.close());
