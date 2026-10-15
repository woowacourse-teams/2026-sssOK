// CloudFront Function (cloudfront-js-2.0, viewer request).
// 초대 링크(/join/:code)와 방 주소(/rooms/:code/...) 요청을 초대 전용 미리보기가 담긴 join.html 로 보낸다.
//
// 카카오톡 같은 미리보기 수집기는 JS 를 실행하지 않고 처음 받은 HTML 의 og 태그만 읽는다.
// join.html 은 앱과 같은 번들을 싣고 og 태그만 다르므로, 사람에게는 원래 화면이 그대로 보인다.
// 주소를 바꾸는 리다이렉트가 아니라 S3 에서 꺼낼 파일만 바꾸므로 주소창도 그대로다.
//
// 방 주소도 보내는 이유 — 공유 버튼 대신 주소창을 복사해 보내도, 받는 사람은 방 입장 화면으로 가게 된다.
//
// 콘솔에 붙여 넣어 배포한다. 바꿀 때는 이 파일을 먼저 고치고 같은 내용을 콘솔에 반영한다.

// /rooms/ 아래에서 방 코드가 아닌 고정 화면. 이런 화면이 새로 생기면 여기에 더한다
var FIXED_ROOM_PAGES = ["create", "closed"];

function isInviteLike(uri) {
  if (uri.startsWith("/join/")) return true;
  if (!uri.startsWith("/rooms/")) return false;

  var segment = uri.slice("/rooms/".length).split("/")[0];
  return segment !== "" && FIXED_ROOM_PAGES.indexOf(segment) === -1;
}

function handler(event) {
  var request = event.request;

  if (isInviteLike(request.uri)) {
    request.uri = "/join.html";
  }

  return request;
}
