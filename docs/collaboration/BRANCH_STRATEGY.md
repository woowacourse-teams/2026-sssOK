# 브랜치 전략

## 브랜치 종류

```
main                    ─ 운영 배포 후보가 모이는 브랜치
  └─ develop            ─ 통합 개발 브랜치 (백/프론트 공용, 단일)
       ├─ feature/be-#12-payment-ledger-api
       ├─ feature/fe-#15-payment-history-ui
       ├─ fix/be-#23-balance-calculation-bug
       ├─ refactor/be-#30-ledger-domain
       └─ chore/common-#40-eslint-checkstyle-setup

deploy                  ─ 실제 서버(운영)에 배포되는 브랜치 (CD 트리거, main에서만 분기)
hotfix/be-#50-critical-bug   ─ 배포 후 긴급 수정 (main에서 분기)
```

`develop`을 프론트/백엔드로 나누지 않고 하나로 통합한다 — 어차피 합쳐야 하니 나눠봤자 합치는
단계만 늦춰진다. 배포 대상 분리는 브랜치가 아니라 CI/CD의 경로 기반 트리거(`backend/**`,
`frontend/**`)로 해결한다.

| 브랜치 | 역할 | push 방식 |
| --- | --- | --- |
| `main` | 운영 배포 후보가 모이는 브랜치 | PR만 허용 (승인 불필요, `hotfix`는 관행상 승인) |
| `develop` | 통합 개발 브랜치, 일반 작업이 모이는 곳 | PR만 허용 (승인 1명 필수) |
| `deploy` | 실서버에 배포되는 브랜치 | PR만 허용 (`main`에서만, 승인 불필요) |
| `feature/*` | 신규 기능 개발 | 자유 push (`develop`에서 분기) |
| `fix/*` | 버그 수정 | 자유 push (`develop`에서 분기) |
| `refactor/*` | 기능 변화 없는 구조 개선 | 자유 push (`develop`에서 분기) |
| `chore/*` | 설정/빌드/문서 | 자유 push (`develop`에서 분기) |
| `hotfix/*` | 배포 후 긴급 수정 | 자유 push (`main`에서 분기) |

`main`, `develop`, `deploy` 모두 GitHub 브랜치 보호 규칙이 걸려 있다 — 직접 push·강제 push·삭제가
막혀 있고 PR로만 반영된다. PR이 머지되면 작업 브랜치는 자동 삭제된다
(`delete_branch_on_merge` 설정).

## 브랜치 네이밍 규칙

**형식**

```
<타입>/<영역>-#<이슈번호>-<간단한-설명>
```

- 영역 구분(모노레포 필수): `be`, `fe`, `common`(양쪽 걸치거나 루트 설정)
- 소문자 + 하이픈(-)만 사용, 공백/언더스코어 금지
- 이슈 번호 필수 포함 (이슈 ↔ 브랜치 ↔ PR 추적)
- 설명은 3~5단어 이내로 간결하게

예: `feature/be-#12-payment-ledger-api`, `fix/fe-#23-form-validation-bug`

## 워크플로우

### 일반 개발

1. 이슈 생성 (담당자·라벨·영역 지정)
2. `develop`에서 작업 브랜치 생성
3. 작업 + 커밋 ([커밋 컨벤션](./COMMIT_CONVENTION.md) 준수)
4. `develop`과 주기적으로 동기화
5. PR 생성 (대상: `develop`, squash merge) → 리뷰(CodeRabbit 1차 + 사람 승인 1명) → CI 통과
   → `develop` 머지
6. 머지 후 브랜치 자동 삭제

### 백엔드 dev 자동 배포

`develop`에 `backend/**` 변경이 병합되면 자동으로 dev 서버에 배포된다(`deploy-dev.yml`,
커밋 해시로 이미지 식별). 별도 PR·승인 없이 **머지 자체가 배포 트리거**다. dev 서버는 운영
서버·DB·시크릿과 완전히 분리돼 있다. 프론트엔드 dev 자동 배포는 아직 없다 — 필요해지면
별도 이슈로 진행한다.

### 운영 배포 (2단계 승격)

1. `develop`이 릴리즈할 만큼 쌓이고 안정적이라 판단되면 `develop → main` PR 생성
   (**merge commit 사용** — 릴리즈 단위 이력 보존이 목적이라 squash 아님)
2. `main`이 안정적이라 판단되면 `main → deploy` PR 생성
3. 두 PR 모두 이미 리뷰된 코드이므로 재리뷰 없이 담당자(또는 당번)가 머지
4. `deploy` 머지 시 CD 파이프라인(`deploy-prod.yml`) 트리거 → AWS 운영 서버 자동 배포

### 긴급 수정 (hotfix)

1. `main`에서 `hotfix/*` 브랜치 분기
2. 수정 + PR 생성 (대상: `main`) → 승인(관행) → CI 통과 → `main` 머지
3. `main → deploy` PR로 즉시 운영 반영
4. **같은 수정을 `develop`에도 반영**(머지 또는 cherry-pick) — 빼먹으면 다음 `develop → main`
   승격 때 수정 사항이 도로 덮어써진다

## 버저닝

서비스별 독립 버전(SemVer)을 쓴다 — `backend-vX.Y.Z`, `frontend-vX.Y.Z`. 전체를 하나로 묶으면
실제 변경 없는 서비스도 버전이 올라가는 문제가 생긴다.

- 태그는 **운영 배포(`main → deploy` 머지) 시점에만** 사람이 직접 찍는다
- dev 배포는 버전 태그 없이 커밋 해시로 충분하다
- Changesets 같은 자동화 도구는 도입하지 않는다 — 이 레포가 npm workspaces 기반 모노레포가
  아니라서 도입 비용 대비 실익이 작다. 나중에 패키지가 여러 개로 쪼개지거나 필요성이 커지면
  재검토한다
