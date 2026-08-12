# 브랜치 전략

## 브랜치 종류

```
main    ─ 항상 배포 가능한 최신 안정 코드 (source of truth)
deploy  ─ 실제 서버에 배포되는 브랜치 (CD 트리거)

작업 브랜치 (main에서 분기)
  ├─ feature/be-#12-payment-ledger-api
  ├─ feature/fe-#15-payment-history-ui
  ├─ fix/be-#23-balance-calculation-bug
  ├─ refactor/be-#30-ledger-domain
  ├─ chore/common-#40-eslint-checkstyle-setup
  └─ hotfix/be-#50-critical-bug   (deploy에서 분기)
```

| 브랜치 | 역할 | push 방식 |
| --- | --- | --- |
| `main` | 개발 완료된 코드가 모이는 기준 브랜치 | PR만 허용 |
| `deploy` | 실서버에 배포되는 브랜치 | PR만 허용 (main에서만) |
| `feature/*` | 신규 기능 개발 | 자유 push |
| `fix/*` | 버그 수정 | 자유 push |
| `refactor/*` | 기능 변화 없는 구조 개선 | 자유 push |
| `chore/*` | 설정/빌드/문서 | 자유 push |
| `hotfix/*` | 배포 후 긴급 수정 | 자유 push |

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
2. `main`에서 작업 브랜치 생성
3. 작업 + 커밋 ([커밋 컨벤션](./COMMIT_CONVENTION.md) 준수)
4. `main`과 주기적으로 동기화
5. PR 생성 → 리뷰(CodeRabbit 1차 + 사람 승인) → CI 통과 → `main` 머지
6. 머지 후 브랜치 자동 삭제

### 배포

1. `main`이 안정적이라 판단되면 `main → deploy` PR 생성
2. 이미 리뷰된 코드이므로 재리뷰 없이 담당자(또는 당번)가 머지
3. `deploy` 머지 시 CD 파이프라인 트리거 → AWS 자동 배포
