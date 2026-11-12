import { buildInviteAppUrl } from "./browserEntry";

const invite = new URL("https://www.ssssok.com/join/ABCDEF23?linkCode=483920");

it("Android는 쏙 패키지를 지정하고 실패 시 같은 초대로 돌아온다", () => {
  const url = buildInviteAppUrl(invite, "Android");
  expect(url).toContain(
    "intent://www.ssssok.com/join/ABCDEF23?linkCode=483920#Intent;scheme=https;package=com.ssssok.app;",
  );
  expect(url).toContain(encodeURIComponent(invite.href));
});

it("iOS 앱에 초대 도메인과 입장 정보를 전달한다", () => {
  expect(buildInviteAppUrl(invite, "iPhone")).toBe(
    "sssok://www.ssssok.com/join/ABCDEF23?linkCode=483920",
  );
});
