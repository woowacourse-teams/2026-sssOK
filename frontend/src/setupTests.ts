import "@testing-library/jest-dom";

import { resetAdminLoginAttempts } from "./mocks/handlers/admin";
import { resetNicknames } from "./mocks/handlers/auth";
import { resetDownloadJobs } from "./mocks/handlers/download";
import { resetRoomHandlers } from "./mocks/handlers/room";
import { resetUploads } from "./mocks/handlers/upload";
import {
  installIntersectionObserverMock,
  resetIntersectionObservers,
} from "./mocks/intersectionObserver";
import { server } from "./mocks/server";

class MockEventSource {
  addEventListener = jest.fn();
  removeEventListener = jest.fn();
  close = jest.fn();
}

Object.defineProperty(globalThis, "EventSource", {
  writable: true,
  configurable: true,
  value: MockEventSource,
});

// jsdom 에 없는 API 라, 무한 스크롤을 쓰는 화면은 렌더링만 해도 터진다.
installIntersectionObserverMock();

// jsdom 에 없는 dialog API를 실제 브라우저의 open 상태만큼 흉내 낸다.
HTMLDialogElement.prototype.showModal = function showModal() {
  this.setAttribute("open", "");
};
HTMLDialogElement.prototype.close = function close() {
  this.removeAttribute("open");
};

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
  // 관리자 로그인 실패 횟수도 되돌린다 — 앞 테스트의 실패가 쌓이면 엉뚱한 곳에서 429 가 난다
  resetAdminLoginAttempts();
  // 앞 테스트가 남긴 관찰자가 남아 있으면 엉뚱한 곳에서 다음 페이지를 부른다
  resetIntersectionObservers();
});

afterAll(() => server.close());
