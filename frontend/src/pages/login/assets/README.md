# 로그인 버튼 리소스

- `kakao-symbol.svg`: [카카오 공식 버튼 SVG](https://developers.kakao.com/tool/images/resource/preview/login-complete-ko.svg)의 말풍선 path를 그대로 사용한다. [가이드](https://developers.kakao.com/docs/ko/kakaologin/design-guide).
- Apple 심볼은 기존 의존성 `react-icons/fa`의 `FaApple`을 사용한다. Apple 배포용 최종 적용 전 [공식 로고 리소스](https://developer.apple.com/design/resources/)와 대조·교체가 필요하다.

버튼 문구는 이미지에 포함하지 않고 서비스 폰트로 렌더링한다. 카카오 가이드는 시스템 서체를 지정하므로 공개 전 서체 적용을 재확인한다.
