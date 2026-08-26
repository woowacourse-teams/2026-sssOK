# AGENTS.md — sssOK 팀 컨벤션

이 저장소에서 작업하는 모든 AI 코딩 에이전트(Claude Code, Codex 등)가 지켜야 할 규칙이다.
여기에는 요약만 두고, 배경과 근거는 `docs/collaboration/` 의 각 문서에 있다.
규칙이 바뀌면 해당 문서와 이 파일을 함께 고친다.

## 작업 순서

1. 이슈부터 생성한다 (작업 먼저 하고 이슈를 나중에 만들지 않는다)
2. `main` 에서 작업 브랜치를 만든다
3. 커밋한다
4. `main` 대상 PR을 만든다 (셀프 머지 금지, 리뷰·CI 통과 후 사람이 머지)

이슈 번호가 브랜치·커밋·PR을 관통하는 유일한 추적 키다. 각 단계로 그대로 넘긴다.

## 이슈

- 제목 접두사: `[FE]` `[BE]` `[FULL]`
- 라벨: `feature` `bug` `refactor` `chore` 중 하나 + `frontend` `backend` (둘 다 걸치면 `fullstack` 추가)
- `.github/ISSUE_TEMPLATE/*.md` 템플릿을 그대로 사용한다
- Assignee 지정 필수
- 자세히: [docs/collaboration/ISSUE_GUIDE.md](docs/collaboration/ISSUE_GUIDE.md)

## 브랜치

- 형식: `<타입>/<영역>-#<이슈번호>-<설명>` (예: `feature/be-#12-payment-ledger-api`)
- 타입: `feature` `fix` `refactor` `chore` `hotfix`
- 영역: `be` `fe` `common`
- 설명은 3~5단어 이내, 소문자 + 하이픈만 (공백·언더스코어 금지)
- `main` 에서 분기한다. `hotfix` 만 `deploy` 에서 분기한다
- 자세히: [docs/collaboration/BRANCH_STRATEGY.md](docs/collaboration/BRANCH_STRATEGY.md)

## 커밋

- 형식: `<타입>(<도메인>): <제목>`
- 타입: `feat` `fix` `refactor` `docs` `test` `chore` `style` `design` `perf` `ci` `rename` `remove`
- 도메인: `backend` `frontend` `common`
- 제목은 한글 단답형으로 쓰고 종결어미를 붙이지 않는다
- 본문은 선택이며, 무엇을 왜 바꿨는지 항목으로 적는다
- 자세히: [docs/collaboration/COMMIT_CONVENTION.md](docs/collaboration/COMMIT_CONVENTION.md)

## PR

- 대상 브랜치: `main`
- 제목: 커밋과 같은 타입 접두사 (예: `feat: 결제 원장 등록 API 구현`)
- 본문: `.github/pull_request_template.md` 구조를 그대로 쓰고, `Closes #이슈번호` 필수
- 하나의 PR은 하나의 관심사만 다룬다. diff 500줄 이내 권장
- 머지 조건: CodeRabbit 1차 리뷰 + 사람 승인 + CI 통과 + `main` 최신 동기화
- 리뷰어는 CODEOWNERS 로 자동 지정되므로 직접 지정하지 않는다
- 자세히: [docs/collaboration/PR_GUIDE.md](docs/collaboration/PR_GUIDE.md)

## 배포

- `main` 이 안정적이라고 판단되면 `main → deploy` PR을 만든다
- 이미 리뷰된 코드이므로 재리뷰 없이 담당자가 머지한다
- `deploy` 머지가 CD 파이프라인을 트리거해 실제 서버에 배포된다
- 자세히: [docs/deployment/DEPLOYMENT.md](docs/deployment/DEPLOYMENT.md)

## 하지 말아야 할 것

- `main` / `deploy` 에 직접 커밋·푸시
- 이슈 없이 브랜치부터 생성
- 본인 PR 셀프 승인·머지
- `main → deploy` 배포 PR을 사용자 확인 없이 병합
- 커밋 메시지·PR 본문에 에이전트 서명이나 `Co-Authored-By` 트레일러 삽입 (공개 저장소다)
- 사용자가 요청하지 않은 커밋·푸시·PR 생성

## 확인이 필요한 순간

되돌리기 어렵거나 저장소 밖으로 나가는 동작은 실행 전에 사용자에게 확인한다.

- 커밋, 푸시, PR 생성, 이슈 생성
- 브랜치 삭제, 강제 푸시, 히스토리 재작성
- 배포 PR 병합