---
name: promote-to-main
description: develop 에 쌓인 변경의 버전을 릴리즈 준비 PR에서 확정한 뒤 main 승격 PR을 만든다. develop을 main으로 올리거나, 릴리즈·운영 배포 전 단계를 준비해 달라고 할 때 사용한다. 실제 운영 서버 배포는 deploy-to-production 스킬이 맡는다.
---

# develop → main 승격 PR 생성

PR을 만들기 전에 저장소 루트의 `AGENTS.md` 를 읽고 "PR"·"배포" 섹션 규칙을 따른다.
(세부 배경이 필요하면 `docs/collaboration/BRANCH_STRATEGY.md`, `docs/collaboration/PR_GUIDE.md` 도 함께 읽는다.)

이 PR이 머지되면 `main` 은 "다음에 운영으로 나갈 후보"가 된다. 아직 실제 서버에 배포되는 건
아니다 — 그건 `main → deploy` PR(= `deploy-to-production` 스킬)이 따로 담당한다.

## 1단계: 릴리즈 준비

`develop → main` PR을 만들기 전에 최신 `develop`에서 아래를 확인한다.

1. `main..develop` 변경을 이슈·서비스 단위로 정리한다.
2. 루트 `VERSION`, `backend/gradle.properties`, `frontend/package.json` 의 현재 값을 읽는다.
3. `.github/backend-version-intents/*.txt` 가 있으면 이슈 번호와 `major`·`minor`·`patch` 분포를 보여준다.
4. 아직 소비하지 않은 백엔드 의도가 있거나 통합·프론트엔드 버전을 올려야 하면 곧바로 승격 PR을
   만들지 않는다. 릴리즈 준비 이슈를 만들고 최신 `develop`에서 준비 브랜치를 만든다.
5. 준비 브랜치에서 다음 명령으로 백엔드 의도를 집계한다.

```bash
bash .github/scripts/prepare-backend-version.sh
```

이 명령은 누적 의도 중 가장 높은 단계(`major > minor > patch`)를 백엔드 버전에 한 번 적용하고
소비한 의도 파일을 모두 삭제한다. 의도가 없으면 백엔드 버전을 유지한다.

6. 이번 릴리즈 전체 변경을 기준으로 루트 `VERSION` 증가 단계를 제안해 사용자에게 확인받는다.
7. 프론트엔드 변경이 있으면 `frontend/package.json` 증가 단계도 제안해 확인받는다.
8. 변경 전후 세 버전, 소비한 백엔드 의도와 이슈 목록, 실행한 검증 결과를 보여준다.
9. 커밋과 PR 내용을 각각 확인받아 릴리즈 준비 브랜치 → `develop` PR을 만든다.
10. 릴리즈 준비 PR은 사람이 병합한다. 병합되기 전에는 2단계 승격 PR을 만들지 않는다.

릴리즈 준비 PR에는 버전과 의도 소비 외의 기능 변경을 섞지 않는다. 백엔드 의도가 여러 개여도 가장
높은 단계를 릴리즈당 한 번만 적용하며, 의도 파일을 일부만 소비하지 않는다.

## 2단계: develop → main 승격

1. `develop` 과 `main` 을 최신으로 받는다.
2. 미소비 백엔드 의도가 남아 있지 않고 릴리즈 버전이 준비됐는지 확인한다. 준비되지 않았으면 1단계로 돌아간다.
3. `main..develop` 커밋 목록을 뽑아 **이번에 승격되는 게 뭔지** 정리한다 (이슈 번호 단위로 묶어서 보여주면 좋다).
4. `develop` 의 CI 가 통과 상태인지 확인한다. 실패했으면 승격하지 않고 알린다.
5. 커밋 목록을 바탕으로 PR 본문을 작성한다 — 팀 PR 템플릿을 그대로 쓰되,
   "변경 사항"은 개별 커밋이 아니라 **이번 릴리즈에 포함되는 이슈/기능 단위**로 요약한다.
6. 제목은 `deploy: develop 승격 (YYYY-MM-DD)` 처럼 릴리즈 단위임을 알 수 있게 짓는다.
7. **제목과 본문 전문, 승격되는 커밋 목록을 사용자에게 보여주고 PR을 만들지 확인받는다.**
8. 승인되면 `develop → main` PR을 만들고 링크를 알려준다.

## 주의 사항

- **이 스킬은 PR을 병합하지 않는다.** 병합은 사람이 한다.
- **머지 방식은 merge commit이다 (squash 아님)** — 릴리즈 단위 이력을 보존하기 위해서다.
  PR을 만드는 것 자체는 머지 방식과 무관하지만, 병합하는 사람에게 이 점을 알려준다.
- `main` 대상 PR은 branch protection 상 승인 없이도 머지 가능하지만, 셀프 머지 금지 원칙은 그대로
  적용된다 — 이 스킬이 만든 PR을 스스로 머지하지 않는다.
- `develop` 에 아직 CI 가 안 끝났거나 실패한 커밋이 섞여 있으면 승격 전에 알린다.
- 릴리즈 준비 PR이나 승격 PR을 사용자 확인 없이 만들거나 병합하지 않는다.
- `hotfix/* → main` PR은 이 스킬이 아니라 `create-pull-request` 스킬로 만든다 (대상 브랜치
  판단 로직이 이미 들어있다). 이 스킬은 `develop → main` 전용이다.
