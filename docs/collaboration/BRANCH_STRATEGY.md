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
데이터와 실행 환경이 운영과 분리돼 있다. 현재 dev와 prod는 같은 RDS 인스턴스를 사용하지만,
서로 다른 데이터베이스를 사용한다. 프론트엔드 dev 자동 배포는 아직 없다 — 필요해지면
별도 이슈로 진행한다.

### CI와 머지 기준

`main`, `develop`, `deploy`의 PR은 모두 최신 대상 브랜치 기준으로 CI를 통과해야 한다. 필수
상태 체크는 `백엔드 CI 결과`, `프론트엔드 CI 결과`다. 경로와 무관한 서비스의 세부 잡이 생략돼도
각 워크플로의 최종 결과 잡은 항상 실행돼 PR의 머지 가능 여부를 일관되게 판단한다.

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

버전은 두 종류다. **서비스 버전**은 그 서비스 자체의 변경 이력이고, **통합 릴리스 버전**은 특정
프론트엔드·백엔드 조합을 하나의 제품으로 묶어 운영에 내보낸 단위다. 셋을 하나로 합치지 않는
이유는, 합치면 실제로 바뀌지 않은 서비스의 버전까지 따라 올라가 "이 버전에서 뭐가 바뀌었나"가
의미를 잃기 때문이다. 첫 정식 통합 릴리스는 `1.0.0`이다.

| 버전 | 기준 파일 | 태그 |
| --- | --- | --- |
| 통합 릴리스 | 저장소 루트 `VERSION` | `release-vX.Y.Z` |
| 백엔드 | `backend/gradle.properties` 의 `version` | `backend-vX.Y.Z` |
| 프론트엔드 | `frontend/package.json` 의 `version` | `frontend-vX.Y.Z` |

셋 다 SemVer `MAJOR.MINOR.PATCH` 를 쓰고, 그 파일이 **유일한 기준 위치**다. 다른 곳에 버전을
또 적지 않는다 (`build.gradle` 에 있던 `0.0.1-SNAPSHOT` 하드코딩은 그래서 없앴다).

### 무엇을 올릴지

| 자리 | 서비스 버전 | 통합 릴리스 버전 |
| --- | --- | --- |
| `MAJOR` | 호환되지 않는 변경 (API 스펙 변경·제거 등) | 사용자가 체감하는 제품 단위의 큰 변화 |
| `MINOR` | 기능 추가 (기존 동작은 그대로) | 기능이 추가된 릴리스 |
| `PATCH` | 버그 수정·리팩터링·내부 개선 | 수정만 나가는 릴리스 |

- 서비스 버전은 **그 서비스가 실제로 바뀐 릴리스에서만** 올린다. 백엔드만 바뀐 릴리스라면
  프론트엔드 버전은 그대로 둔다.
- 통합 릴리스 버전은 운영에 나가는 **모든** 릴리스에서 올린다. 직전 릴리스보다 반드시 커야 한다.
- 백엔드 작업 PR은 `backend/gradle.properties`를 직접 바꾸지 않고 이슈별 버전 의도 파일을
  `.github/backend-version-intents/`에 하나 추가한다. 파일명이 이슈 번호라 동시에 열린 PR끼리
  같은 파일을 수정하지 않는다.
- 버전 의도는 `major`·`minor`·`patch` 중 하나다. 백엔드 CI가 백엔드 변경 여부와 의도 파일의
  추가 여부·형식을 검증한다.
- 릴리스 준비에서는 누적된 의도 중 가장 높은 단계를 백엔드 버전에 한 번 적용한다. `patch`만
  있으면 `PATCH`, `minor`가 하나라도 있으면 `MINOR`, `major`가 하나라도 있으면 `MAJOR`를 올린다.
- 통합 릴리스 버전과 프론트엔드 버전도 릴리스 준비에서 사람이 확정한다.

백엔드 작업 PR에서는 다음 명령으로 버전 의도를 만든다.

```bash
bash .github/scripts/declare-backend-version.sh <이슈번호> <major|minor|patch>
```

### 릴리스 준비 절차

`develop → main` 승격 전에 별도 릴리스 준비 브랜치를 `develop`에서 만들고 다음 변경을 하나의
릴리스 준비 PR로 `develop`에 반영한다. 보호 브랜치인 `develop`에는 직접 커밋하지 않는다.

1. `VERSION` — 통합 릴리스 버전을 올린다 (항상)
2. 백엔드 변경이 있으면 아래 명령으로 누적 의도를 집계하고 `backend/gradle.properties`를 올린다
3. `frontend/package.json` — 이번 릴리스에 프론트엔드 변경이 포함되면 올린다

```bash
bash .github/scripts/prepare-backend-version.sh
```

백엔드 준비 명령은 누적 의도 중 가장 높은 단계를 적용한 뒤 소비한 의도 파일을 삭제한다. 릴리스
준비 PR이 병합되고 나면 기존 절차대로 `develop → main` 승격 PR을 만든다. 따라서 추가 PR은 작업
PR마다 생기지 않고 릴리스당 하나만 생긴다.

`main`·`deploy` 대상 PR에서는 `Release Check` 워크플로(`release-check.yml`)가 형식·증가·태그
중복을 검증한다. 검증 규칙과 실패 시 대처는 [DEPLOYMENT.md](../deployment/DEPLOYMENT.md#버전과-릴리스-태그)에
정리돼 있다.

### 태그

태그는 **운영 배포 헬스체크까지 성공한 뒤** CD 파이프라인이 자동으로 찍는다. 배포가 실패하면
그 버전의 태그는 남지 않는다. dev 배포는 태그 없이 커밋 해시로만 식별한다.

과거 태그 `fe-v0.1.0` 은 그대로 두되, 이후 프론트엔드 태그는 `frontend-vX.Y.Z` 형식만 쓴다.

### hotfix 의 버전

`hotfix/* → main → deploy` 도 운영 배포라 통합 릴리스 버전을 올려야 한다. 대개 긴급 버그 수정이므로
통합 릴리스와 해당 서비스 버전의 `PATCH` 를 올린다. 이때 올린 버전 변경도 `develop` 에 같이
반영한다 — 빼먹으면 다음 승격 때 버전이 되돌아가 CI 의 증가 검사에서 막힌다.
