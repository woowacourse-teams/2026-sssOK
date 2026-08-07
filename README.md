# sssOK

## 프로젝트 구조

```
.
├── backend/    # 백엔드 서버
├── frontend/   # 프론트엔드 앱
└── docs/       # 협업 문서
```

## 시작하기

> TODO: 백엔드/프론트엔드 스택이 정해지면 실행 방법을 채워주세요.

### Backend

```bash
cd backend
# TODO
```

### Frontend

```bash
cd frontend
# TODO
```

## 협업 문서

새로 합류한 개발자는 아래 문서를 먼저 읽어주세요.

| 문서 | 내용 |
| --- | --- |
| [커밋 컨벤션](./docs/COMMIT_CONVENTION.md) | 커밋 메시지 타입 및 작성 규칙 |
| [이슈 전략](./docs/ISSUE_GUIDE.md) | 라벨, 템플릿, 이슈 작성 규칙 |
| [브랜치 전략](./docs/BRANCH_STRATEGY.md) | 브랜치 종류, 네이밍, 워크플로우 |
| [PR 규칙](./docs/PR_GUIDE.md) | PR 생성 조건, 리뷰, 머지 조건 |

## 개발 워크플로우 요약

1. [이슈](./docs/ISSUE_GUIDE.md) 생성 (담당자·라벨·영역 지정)
2. `main`에서 [브랜치 전략](./docs/BRANCH_STRATEGY.md)에 따라 작업 브랜치 생성
3. [커밋 컨벤션](./docs/COMMIT_CONVENTION.md)에 맞게 작업 + 커밋
4. [PR 규칙](./docs/PR_GUIDE.md)에 따라 PR 생성 → 리뷰 → CI 통과 → `main` 머지
