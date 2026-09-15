---
name: promote-to-main
description: develop 에 쌓인 변경을 릴리즈 단위로 main 에 승격하는 PR을 만든다. develop을 main으로 올리거나, 운영 배포 전 단계를 준비해 달라고 할 때 사용한다. 실제 운영 서버 배포(deploy 트리거)는 deploy-to-production 스킬이 따로 맡는다.
---

# develop → main 승격 PR 생성

PR을 만들기 전에 저장소 루트의 `AGENTS.md` 를 읽고 "PR"·"배포" 섹션 규칙을 따른다.
(세부 배경이 필요하면 `docs/collaboration/BRANCH_STRATEGY.md`, `docs/collaboration/PR_GUIDE.md` 도 함께 읽는다.)

이 PR이 머지되면 `main` 은 "다음에 운영으로 나갈 후보"가 된다. 아직 실제 서버에 배포되는 건
아니다 — 그건 `main → deploy` PR(= `deploy-to-production` 스킬)이 따로 담당한다.

## 작업 절차

1. `develop` 과 `main` 을 최신으로 받는다.
2. `main..develop` 커밋 목록을 뽑아 **이번에 승격되는 게 뭔지** 정리한다 (이슈 번호 단위로 묶어서 보여주면 좋다).
3. `develop` 의 CI 가 통과 상태인지 확인한다. 실패했으면 승격하지 않고 알린다.
4. 커밋 목록을 바탕으로 PR 본문을 작성한다 — 팀 PR 템플릿을 그대로 쓰되,
   "변경 사항"은 개별 커밋이 아니라 **이번 릴리즈에 포함되는 이슈/기능 단위**로 요약한다.
5. 제목은 `deploy: develop 승격 (YYYY-MM-DD)` 처럼 릴리즈 단위임을 알 수 있게 짓는다.
6. **제목과 본문 전문, 승격되는 커밋 목록을 사용자에게 보여주고 PR을 만들지 확인받는다.**
7. 승인되면 `develop → main` PR을 만들고 링크를 알려준다.

## 주의 사항

- **이 스킬은 PR을 병합하지 않는다.** 병합은 사람이 한다.
- **머지 방식은 merge commit이다 (squash 아님)** — 릴리즈 단위 이력을 보존하기 위해서다.
  PR을 만드는 것 자체는 머지 방식과 무관하지만, 병합하는 사람에게 이 점을 알려준다.
- `main` 대상 PR은 branch protection 상 승인 없이도 머지 가능하지만, 셀프 머지 금지 원칙은 그대로
  적용된다 — 이 스킬이 만든 PR을 스스로 머지하지 않는다.
- `develop` 에 아직 CI 가 안 끝났거나 실패한 커밋이 섞여 있으면 승격 전에 알린다.
- `hotfix/* → main` PR은 이 스킬이 아니라 `create-pull-request` 스킬로 만든다 (대상 브랜치
  판단 로직이 이미 들어있다). 이 스킬은 `develop → main` 전용이다.
