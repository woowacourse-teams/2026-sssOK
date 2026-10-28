# 쏙 법률 문서 관리

현재 상태: **보관·삭제 기준 논의 중, 공개 전 검토 필요**. 문서 페이지와 실제 삭제·익명화·동의 기록은 별도 작업이다.

## 문서 원본

- `frontend/src/pages/legal/content/privacyPolicy.tsx` → `/privacy`
- `frontend/src/pages/legal/content/termsOfService.tsx` → `/terms`
- 공통 렌더러: `frontend/src/pages/legal/ui/LegalPage.tsx`
- 로그인 화면 하단에서 두 페이지로 이동하며 회원 인증은 요구하지 않는다.
- Next.js·MDX·Tailwind 등 새 패키지를 설치하지 않고 기존 React·Emotion으로 구성했다.

사용자가 제공한 개인정보처리방침을 기준으로 소셜 인증·탈퇴·권리 행사 설명을 보완했다. 기존 초안 시행일 2026-10-27은 보존했고, 개정 적용일을 소급 확정하지 않았다. 이용약관 시행일은 공개 전에 정한다.

## 확정된 입력

- 운영명 SSSOK / 서비스명 쏙
- 개인정보 보호책임자·열람청구·고충처리 담당자: 정윤석
- 연락처: yundoll777@gmail.com / 서비스 내 문의하기
- 만 14세 이상 대상. 스토어 콘텐츠 등급과 서비스 가입 연령은 다르다.
- 이용 중인 방은 보관·공유·검색 기능 제공 동안 유지하며, 업로드 후 일괄 30일 삭제는 적용하지 않는다.
- 현재 탈퇴 시 회원 기록은 탈퇴 상태로 남고 닉네임은 ‘탈퇴한 사용자’로 변경된다. 로그인은 무효화된다.
- 본인이 만든 방은 삭제 상태로 변경되며, 다른 사람의 방에 올린 파일·활동 기록 일부는 유지된다.
- 소셜 연결 해제 성공 시 식별자·토큰을 삭제하며 실패 시 정리 전까지 보관한다.

## 논의 중인 기준

원본·파생 파일의 실제 삭제 범위와 기한, 탈퇴 후 남는 기록의 보관 기간, AI 설명의 익명 처리 및 보관 여부는 확정되지 않았다. 앞서 논의한 전량 삭제·익명 설명 보관은 제안이며 현재 구현된 정책으로 안내하지 않는다. 문서에는 현재 동작과 논의 중인 내용을 구분했다.

이번 범위는 읽기 전용 문서와 로그인 화면의 링크다. 사용자 요청에 따라 동의 화면은 추가하지 않는다. 동의 API와 기록 저장은 후속 범위다.

## 코드에서 확인한 사항

| 확인 사항                                                      | 근거                                                             |
| -------------------------------------------------------------- | ---------------------------------------------------------------- |
| 소셜 제공자 식별자와 암호화된 인증 토큰 보관                   | SocialIdentity, SocialAccountJpaEntity, SocialMemberLoginService |
| Apple 인증 시작 시 이름·이메일 scope 요청 없음                 | StartAppleLoginService                                           |
| 카카오 인증은 사용자 ID 중심, 이메일 회원 정보 저장 없음       | KakaoAuthClient, SocialIdentity                                  |
| 웹 회원 토큰은 메모리, 갱신 토큰은 HttpOnly 쿠키               | member-session, completeLogin, 서버 COOKIE 인증                  |
| PostHog에 방 코드·역할·앱 버전 전송, 사진·영상 리플레이 차단   | frontend/src/shared/lib/analytics.ts                             |
| OpenAI Responses 요청 store:false                              | OpenAiImageDescriptionClient                                     |
| 탈퇴는 상태 변경·인증 해제·연결 정리이며 원본 전량 파기가 아님 | WithdrawMemberService, WithdrawAccountService                    |
| 문의 알림에 본문 일부·방 이름·닉네임·시각·관리자 경로 포함     | DiscordFeedbackNotifier                                          |

배포 환경변수와 외부 콘솔 설정까지 검증한 목록은 아니다.

## 공개·동의 적용 전 확인

- [ ] 실제 개인정보처리자의 법적 명칭·운영자 정보와 공개 연락 창구 최종 확인.
- [ ] 외부 처리의 위탁/제공 분류와 실제 계약 상대 법인·연락처 확인.
- [ ] Cloudflare R2, OpenAI, PostHog, Discord 및 호스팅/DB/CDN 전체의 실제 이전 국가·시기·방법·항목·목적·기간·근거 확인. `region:auto`로 실제 국가를 단정하지 않는다.
- [ ] 방·탈퇴 삭제 대상을 실제 삭제 작업에 연결. 원본·썸네일·미리보기·임베딩·관련 DB 레코드의 완료 여부 확인.
- [ ] AI 설명 익명 처리·재식별 위험 확인 절차 구현. 검증 전 설명을 익명 보관소로 복사하지 않고, 확인 실패·불확실 시 삭제하도록 처리.
- [ ] 로그·메타데이터·매핑 테이블·백업에서 원본과 설명을 다시 연결할 수 있는지 확인. 익명 설명 원문과 기존 사용자 데이터의 분리만으로 익명화 완료로 표시하지 않는다.
- [ ] 문의·소셜 연결 해제 실패·예외 보관 기록의 종료 기준과 백업 정리 주기 확정.
- [ ] 원본·파생 파일·AI 정보·분석 기록·Discord 알림·백업의 실제 삭제 절차 확인. 문구만으로 파기를 대체하지 않는다.
- [ ] PostHog 프로젝트의 실제 이벤트·오류·리플레이 보관 설정 확인. 플랜 안내만으로 물리적 삭제 시점을 보장하지 않는다.
- [ ] 만 14세 이상 확인 UI와 미성년자 이용계약 관련 절차 검토. 문서만으로 연령 확인이 구현되지 않는다.
- [ ] 개인정보 수집·이용의 처리 근거와 필수/선택 항목 결정. 처리방침 전체 동의 한 개로 대체하지 않는다.
- [ ] 국외 이전·AI 처리의 별도 고지/동의 필요성과 시점·범위 검토. 익명 업로드 이용자도 고려한다.
- [ ] 분석 선택 동의를 운영한다면 SDK 초기화 전 수집 제어. 현재 PostHog는 선택 동의에 의해 차단되지 않는다.
- [ ] 실제 운영 정책·법률 검토 후 버전/적용일 확정, 초안 표시 해제 및 이전 공개본 별도 보관.

미확정 값은 임의로 만들지 않았다. 페이지에도 검토용 초안과 국외 처리 내역 미확정 상태를 표시했다.

## 관리 규칙

1. 내용 파일만 수정하며, 약관과 방침은 독립적으로 버전을 관리한다.
2. 공개본 개정 전 기존 파일을 버전별로 보관하고 적용일·변경 내용·고지/재동의 필요성을 기록한다.
3. 동의 기록은 문서 ID/버전과 연결한다. draft ID를 실제 동의 버전으로 사용하지 않는다.
4. 문서 페이지에 가입 완료나 동의 저장 부수 효과를 넣지 않는다.
5. 설정 화면은 추후 같은 ROUTES.privacy / ROUTES.terms를 사용한다.

## 출처

- [korean-privacy-terms / privacy-kr](https://github.com/kimlawtech/korean-privacy-terms/tree/main/skills/privacy-kr): 구조·체크리스트 참고. Next.js 설치 절차와 법령·제재 주장을 그대로 적용하지 않았다.
- [개인정보 보호법 제15조](https://law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1020398563)
- [개인정보 보호법 제28조의8](https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029334957)
- [PostHog 요금제](https://posthog.com/pricing)
- [PostHog 녹화 보관 기간](https://posthog.com/docs/session-replay/recording-retention)
- [OpenAI API 데이터 처리](https://developers.openai.com/api/docs/guides/your-data)

확인일: 2026-10-28. 스킬의 문서 생성은 법령 준수나 심사 통과 인증이 아니다.
