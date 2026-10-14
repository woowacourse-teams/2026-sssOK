// 랜딩 방문을 앱과 같은 PostHog 프로젝트에 남긴다.
// 랜딩은 번들을 거치지 않는 정적 페이지라 src/shared/lib/analytics.ts 를 쓸 수 없어 따로 둔다.
// 키·주소·팀원 표시 규칙은 그 파일과 src/shared/config/analytics.ts 에 맞춘다. 바꿀 때 함께 바꾼다.
(() => {
  const POSTHOG_KEY = "phc_tJEUFUieM5VwSqS8BwfHTc3feYThtU9sa92zimfjMigd";
  const POSTHOG_HOST = "https://us.i.posthog.com";
  const INTERNAL_PARAM = "internal";
  const INTERNAL_PROPERTY = "is_internal";

  // 로컬에서 열어 본 방문이 운영 지표에 섞이지 않게 한다
  if (["localhost", "127.0.0.1"].includes(location.hostname)) return;

  // PostHog 공식 스니펫. SDK(array.js)를 받는 동안의 호출을 쌓아 뒀다가 로드되면 이어서 실행한다
  // prettier-ignore
  !function(t,e){var o,n,p,r;e.__SV||(window.posthog&&window.posthog.__loaded)||(window.posthog=e,e._i=[],e.init=function(i,s,a){function g(t,e){var o=e.split(".");2==o.length&&(t=t[o[0]],e=o[1]),t[e]=function(){t.push([e].concat(Array.prototype.slice.call(arguments,0)))}}(p=t.createElement("script")).type="text/javascript",p.crossOrigin="anonymous",p.async=!0,p.src=s.api_host.replace(".i.posthog.com","-assets.i.posthog.com")+"/static/array.js",(r=t.getElementsByTagName("script")[0]).parentNode.insertBefore(p,r);var u=e;for(void 0!==a?u=e[a]=[]:a="posthog",u.people=u.people||[],u.toString=function(t){var e="posthog";return"posthog"!==a&&(e+="."+a),t||(e+=" (stub)"),e},u.people.toString=function(){return u.toString(1)+".people (stub)"},o="init capture register register_once unregister identify reset".split(" "),n=0;n<o.length;n++)g(u,o[n]);e._i.push([i,s,a])},e.__SV=1)}(document,window.posthog||[]);

  const { posthog } = window;
  // 랜딩은 페이지 이동이 없는 한 장짜리라 첫 $pageview 만 잡히면 된다
  posthog.init(POSTHOG_KEY, { api_host: POSTHOG_HOST, defaults: "2026-08-30" });

  // `?internal=1` 이면 팀원 기기로 표시하고, `?internal=0` 이면 표시를 푼다
  const url = new URL(location.href);
  const internal = url.searchParams.get(INTERNAL_PARAM);
  if (internal !== "1" && internal !== "0") return;

  if (internal === "1") {
    posthog.register({ [INTERNAL_PROPERTY]: true });
  } else {
    posthog.unregister(INTERNAL_PROPERTY);
  }
  // 표시용 쿼리가 공유 링크에 딸려가지 않도록 주소창에서 지운다
  url.searchParams.delete(INTERNAL_PARAM);
  history.replaceState(history.state, "", url);
})();
