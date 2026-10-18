const fs = require("fs");
const path = require("path");

// CloudFront Function 은 모듈이 아니라 handler 를 전역에 선언하는 스크립트라, 파일을 읽어 꺼낸다
const source = fs.readFileSync(path.join(__dirname, "invite-preview.js"), "utf8");
const handler = new Function(`${source}; return handler;`)();

const requestTo = (uri) => handler({ request: { uri, headers: {} } });

describe("초대 링크 미리보기 CloudFront Function", () => {
  it.each([
    "/join/7K93QX2S",
    "/rooms/7K93QX2S",
    "/rooms/7K93QX2S/",
    "/rooms/7K93QX2S/gallery",
    "/rooms/7K93QX2S/media/12",
    "/rooms/7K93QX2S/settings",
  ])("%s 는 join.html 로 보낸다", (uri) => {
    expect(requestTo(uri).uri).toBe("/join.html");
  });

  it.each([
    "/",
    "/home",
    "/rooms/create",
    "/rooms/create/",
    "/rooms/closed",
    "/rooms/",
    "/admin",
    "/landing.html",
    "/join",
  ])("%s 는 그대로 통과시킨다", (uri) => {
    expect(requestTo(uri).uri).toBe(uri);
  });

  it("주소 외의 요청 정보는 건드리지 않는다", () => {
    const request = { uri: "/join/7K93QX2S", headers: { host: { value: "www.ssssok.com" } } };

    expect(handler({ request }).headers).toEqual({ host: { value: "www.ssssok.com" } });
  });
});
