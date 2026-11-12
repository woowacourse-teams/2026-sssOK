const { test } = require("node:test");
const assert = require("node:assert/strict");
const { Linter } = require("eslint");
const tseslint = require("typescript-eslint");
const team = require("./typescript.cjs");
function lint(code) {
  return new Linter().verify(
    code,
    [
      {
        files: ["**/*.tsx"],
        languageOptions: { parser: tseslint.parser },
        plugins: { team },
        rules: {
          "no-alert": "error",
          "team/explicit-return-type": "error",
          "team/exception-description": "error",
        },
      },
    ],
    { filename: "sample.tsx" },
  );
}
test("일반 함수·훅의 반환 타입 누락을 검출한다", () => {
  assert.equal(lint("function count() { return 1; } const useCount = () => 1;").length, 2);
});
test("명시적 반환 타입과 컴포넌트·인라인 콜백 추론을 허용한다", () => {
  assert.equal(
    lint(
      "function count(): number { return 1; } function View() { return <div/>; } [1].map(x => x + 1);",
    ).length,
    0,
  );
});
test("사유 없는 예외와 파일 전체 해제를 거부한다", () => {
  assert.equal(lint("// eslint-disable-next-line no-alert\nalert('a');").length, 1);
  assert.equal(
    lint("/* eslint-disable no-alert -- 파일 전체 해제는 허용하지 않는다 */\nalert('a');").length,
    1,
  );
});
test("사유가 있는 한 줄 예외를 허용한다", () => {
  assert.equal(
    lint("// eslint-disable-next-line no-alert -- 외부 API 예제를 재현하기 위한 알림\nalert('a');")
      .length,
    0,
  );
});
