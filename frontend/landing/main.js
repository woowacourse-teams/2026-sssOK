// ─────────────────────────────────────────────────────────────
// 폰 하나를 두고, 스크롤 위치(T)만으로 장면을 그린다. 이야기는 '문제 → 해결 → 사용 방법' 순서다.
//  문제     단톡방: 모임이 끝나고 "사진 보내 줘"가 쏟아진다
//  해결·사용 방법 (INTRO) 내가 "아 어떡하지 ㅠㅠ / 한꺼번에 다운로드 받을 수 있는 방법 없나 / 잠시만!!" → ① 쏙으로 앱을 바꿔 방을 만들고 공유 링크 복사
//           → ② 단톡방으로 돌아와 링크를 보냄 → ③ 링크를 누르면 친구들이 설치 없이 들어오고 사진이 한곳에 모인다.
//           사진마다 올린 사람(민지·서연·나) 이름표가 붙는다
//  받기     원본 그대로 한 번에 저장(카톡으로 받은 깨진 사진과 나란히) → 내 사진만 받고 싶으면 사람별로 나눈다(인물 분류)
//  끝       링크 만들기 · App Store
// 공유가 본론이고, 정리 기능은 인물 분류 하나만 '받는 방법'으로 보여 준다 (비슷한 사진·검색은 보여 주지 않는다)
// ─────────────────────────────────────────────────────────────
const LOCAL = "/landing/assets";
// 첫 장면(단톡방 → 사진방, 약 3.6초)이 스크롤로도 너무 빠르지 않게 보이는 길이.
// 친구들 사진이 올라오는 시간을 줄인 만큼(시계 1.35초) 함께 줄여, 앞 장면들의 스크롤 빠르기는 그대로 둔다
const INTRO = 5.62;
// 스크롤하면: 단톡방이 왼쪽으로 밀리고 폰 바탕화면이 들어옴 → 쏙 앱 아이콘을 누르면 방 만들기 화면이 아이콘에서
// 커지며 열림 → 방 제목 입력 → '방 만들기' → "공유 링크를 복사했어요." → 단톡방으로 돌아와 링크를 보냄
// → 그 링크를 누르면(LINK_TAP) 사진방으로
const APP_IN = [0.1, 0.45];
const OS_TAP = 0.7; // 바탕화면의 쏙 아이콘 누름
const APP_OPEN = [0.78, 1.08]; // 방 만들기 화면이 아이콘 자리에서 커지며 열린다
const MAKE_TYPE = [1.2, 1.55]; // 방 제목 입력
const MAKE_TAP = 1.7; // '방 만들기' 누름
const MAKE_TOAST = [1.78, 1.9, 2.2, 2.3]; // 복사 알림
const APP_OUT = [2.3, 2.65];
const SEND_AT = [2.7, 3.1]; // 단톡방에 남은 두 메시지("그냥 여기 다 올려!" + 링크)가 올라온다
const LINK_TAP = 3.45;
// 첫 장면 뒤 장면들. len = 그 장면에 쓰는 스크롤 길이(초)
const STORY = [
  { id: "save", len: 2.5 },
  // 사람별: 정리 방식 시트가 올라와 '얼굴 분석해서 정리하기'를 누르기까지 (사람별 장면 시각 0.2 → 2.2)
  { id: "person", len: 0.9 },
  // 폴더가 생긴 뒤 '나' 탭을 눌러 내 사진만 남는다 (renderMeFolder)
  // 폰 폭은 폴더 칩이 탭으로 날아 들어가는 시간만큼 길다 (renderFolderChips)
  { id: "me", len: matchMedia("(max-width: 760px)").matches ? 1.3 : 1.15 },
  { id: "end", len: 1.6 },
];
{
  let at = INTRO;
  STORY.forEach((s) => {
    s.start = at;
    at += s.len;
  });
}
const PH = Object.fromEntries(STORY.map((s) => [s.id, s]));
const SCROLL_END = STORY.at(-1).start + STORY.at(-1).len;
// 스크롤 시각 T 에 그 장면 안에서 흐른 시간. 그 장면이 아니면 -1
const phH = (T, id) => {
  const s = PH[id];
  return T >= s.start && (T < s.start + s.len || s === STORY.at(-1))
    ? Math.min(T - s.start, s.len)
    : -1;
};
// 사람별 장면 시각: 시트를 누르기까지 흐르다가, 폴더가 생긴 뒤엔 그 시각(ME_AT)에 멈춘다
const PERSON_FROM = 0.2;
const PERSON_TO = 2.2;
const ME_AT = 6.2;
const personScene = (T) =>
  T >= PH.me.start
    ? ME_AT
    : T >= PH.person.start
      ? lerp(PERSON_FROM, PERSON_TO, (T - PH.person.start) / PH.person.len)
      : 0;
const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
const clamp = (v, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v));
const seg = (T, a, b) => clamp((T - a) / (b - a));
const ease = (t) => 1 - Math.pow(1 - t, 3);
const inOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const lerp = (a, b, t) => a + (b - a) * t;
const life = (T, a, b, c = Infinity, d = Infinity) =>
  Math.min(ease(seg(T, a, b)), 1 - seg(T, c, d));
const within = (T, a, b) => T >= a && T < b;
const $ = (id) => document.getElementById(id);
const icon = (name) => `<svg class="ico"><use href="#i-${name}" /></svg>`;

const film = $("film");
const stage = $("stage");
// 맨 앞 단톡방 구간(스크롤 초): 스크롤할 때마다 메시지가 한 줄씩 올라오고, 다 나온 뒤에야 다음 장면으로 넘어간다
const CHAT_LEN = 1.5;
// 폰 폭: 첫 화면엔 헤드라인 아래로 단톡방 윗부분만 보이고, 스크롤하면 헤드라인이 빠지며 단톡방이 위로 올라와
// 화면을 세로로 길게 채운다. 그 올라오는 데 쓰는 스크롤(초). 메시지는 다 올라온 뒤부터 스크롤로 넘긴다
const CHAT_RISE = matchMedia("(max-width: 760px)").matches ? 0.45 : 0;
const FILM_END = SCROLL_END + CHAT_LEN + CHAT_RISE;
let chatS = 0;
// 단톡방 메시지는 스크롤한 만큼 한 줄씩 올라온다 (저절로 올라오지 않는다)
const CHAT_STEPS = 8; // 처음 깔린 대화 두 줄(CHAT_PRE) 뒤로 "잠시만!!"까지 올라올 메시지 수. 나머지 둘은 SEND_AT 에 (CHAT 은 모두 12개)
let chatBase = null;
let chatStep = 0;
// 폰 폭에선 첫 화면에 단톡방이 없으니 저절로 올라오지 않는다. 올라왔을 때 위가 휑하지 않게
// 다섯 줄(CHAT_PRE + 2)이 깔린 채로 나타나고, 나머지는 스크롤로 넘긴다
// 저절로 올라오지 않는다: 스크롤해야 메시지가 올라온다 (폰 폭은 다섯 줄이 깔린 채로 시작)
const autoChatStep = () => (reduceMotion ? CHAT_STEPS : CHAT_RISE ? 2 : 0);
const chatLen = () =>
  CHAT_RISE +
  (chatBase === null
    ? CHAT_LEN
    : 0.25 + ((CHAT_LEN - 0.25) * (CHAT_STEPS - chatBase)) / CHAT_STEPS);
// 단톡방이 위로 올라온 정도 (0 = 첫 화면, 1 = 화면을 다 채움). 넓은 화면은 늘 1
let chatRise = 1;
film.style.setProperty("--secs", FILM_END);

function show(el, o, y = 0, extra = "") {
  el.style.opacity = o;
  el.style.visibility = o <= 0.001 ? "hidden" : "visible";
  el.style.transform = `${extra} translateY(${y}px)`;
}

// ── 장면 글 ──
// 사용 방법 장면마다 글 하나. ph = 그 글이 떠 있는 장면들
const TEXT = [
  {
    ph: ["save"],
    tag: "④ 원본으로 받기",
    // ② 단계처럼 큰 제목 두 줄 + 설명 한 줄로 나눈다
    title: "다 모이면<br /><em>원본 그대로</em>",
    desc: "한 번에 저장해요.",
  },
  {
    // 인물 분류는 따로 내세우는 기능이 아니라 '받는 방법' 중 하나로 보여 준다
    ph: ["person", "me"],
    tag: "⑤ 내 사진만 받기",
    // ②·④ 단계처럼 큰 제목 두 줄 + 설명 한 줄로 나눈다
    title: "내 사진만 받고 싶다면<br /><em>버튼 한 번</em>으로",
    desc: "내가 나온 사진만 골라 받아요.",
  },
];
const textEls = TEXT.map((t) => {
  const el = document.createElement("div");
  el.className = "tx";
  el.innerHTML =
    (t.tag ? `<span class="tag">${t.tag}</span>` : "") +
    (t.kicker ? `<span class="kicker">${t.kicker}</span>` : "") +
    `<p class="title">${t.title}</p>` +
    // 설명이 없는 장면 글은 설명 줄을 그리지 않는다 ("undefined" 가 찍히지 않게)
    (t.desc ? `<p class="desc">${t.desc}</p>` : "");
  return $("texts").appendChild(el);
});

// ── 사진 ──
const PEOPLE = [
  { name: "민지", total: 34 },
  { name: "서연", total: 28 },
  { name: "나", total: 24 },
];
// ── 사진: girls-night 세트 (같은 날 밤 잠옷 파자마 파티, 민지·서연·나) ──
// 사람 사진은 사람마다 5장(people/{minji|seoyeon|me}-1~5). 예전 스프라이트의 [줄, 컷] 번호를 장 번호로 옮긴다
const GN = `${LOCAL}/girls-night`;
const PERSON_FILE = ["minji", "seoyeon", "me"];
const SHOT_NO = {
  "0,0": 1,
  "1,0": 2,
  "0,1": 3,
  "1,1": 4,
  "1,2": 5,
  "0,2": 5,
};
const personSrc = (p, r, v) => `${GN}/people/${PERSON_FILE[p]}-${SHOT_NO[`${r},${v}`] ?? 1}.jpg`;
// 음식·장소 사진: 예전 photo-1~9 번호를 새 사진으로 옮긴다. 1·3·5 는 검색에 걸리는 메뉴(허니콤보·엽떡·마라샹궈)
const PHOTO_SRC = {
  1: "search/food-1",
  3: "search/food-2",
  5: "search/food-3",
  2: "search/etc-1",
  4: "group-2", // 집 외관(etc-2) 대신 단체 사진
  6: "search/etc-3",
  7: "search/etc-4",
  9: "cheers", // 택시 안 사진(etc-5) 대신 보내 준 건배 사진
  8: "group-1",
};
const photoSrc = (n) => `${GN}/${PHOTO_SRC[n] ?? "group-2"}.jpg`;

function paint(pic, p, r, v) {
  const el = pic.firstElementChild ?? pic.appendChild(document.createElement("i"));
  el.style.backgroundImage = `url(${personSrc(p, r, v)})`;
  el.style.backgroundSize = "cover";
  el.style.backgroundPosition = "center";
}
const picEl = (p, r, v) => {
  const pic = document.createElement("div");
  pic.className = "pic";
  paint(pic, p, r, v);
  return pic;
};

// 갤러리 사진 칸. 올린 사람·좋아요·선택 동그라미를 실제 앱 자리에 둔다
function tile({ img, pic, who, heart = 0 }) {
  const el = document.createElement("div");
  el.className = "gt";
  if (pic) el.appendChild(pic);
  else el.innerHTML = `<img src="${img}" alt="" />`;
  el.insertAdjacentHTML(
    "beforeend",
    `<span class="chk"><svg viewBox="0 0 24 24"><path d="m4.5 12.75 6 6 9-13.5" /></svg></span><span class="upl${who === "나" ? " mine" : ""}">${who}</span><span class="hrt">${icon("heart")}<b>${heart}</b></span>`,
  );
  return el;
}

// 사람별 정리용: 사람마다 5장, 화면에서는 섞어서 보여 준다
const SHOTS = [
  [0, 0],
  [1, 0],
  [0, 1],
  [1, 1],
  [1, 2],
];
const ITEMS = PEOPLE.flatMap((_, p) => SHOTS.map(([r, v], j) => ({ p, j, r, v })));
const GRID = ITEMS.map((_, k) => ITEMS[(k * 7) % ITEMS.length]);
const MINE = GRID.filter((g) => g.p === 2);
// 올린 사람: 사진방에 맨 먼저 올라오는 네 장(맨 뒤 네 칸)은 내가 사진첩에서 골라 올린 사진, 나머지는 친구들 사진
const MY_UP = 4;
const WHO = GRID.map((it, k) =>
  k >= GRID.length - MY_UP ? "나" : ["서연", "민지"][(k + it.p) % 2],
);
const setP = GRID.map((it, k) =>
  $("setP").appendChild(
    tile({
      pic: picEl(it.p, it.r, it.v),
      who: WHO[k],
      heart: (k * 3) % 7,
    }),
  ),
);

// 탭: 전체만 있을 때와 사람 폴더가 생긴 뒤
const TABS_A = [["전체", 86]];
const TABS_B = [
  ["전체", 86],
  ["민지", 34, "new"],
  ["서연", 28, "new"],
  ["나", 24, "new"],
];
let tabsKey = "";
let tabsLen = 0;
function renderTabs(list, active, total) {
  // 사진이 한 장도 없을 때만 빈 상태 문구를 보여 준다 (첫 사진이 내려오기 시작하면 사라진다)
  $("gEmpty").classList.toggle("off", total > 0);
  const key = `${list.length}|${active}|${total}`;
  if (key === tabsKey) return;
  tabsKey = key;
  // 새 폴더 탭은 탭 줄이 처음 늘어날 때만 튀어 들어온다 (탭만 바꿀 땐 그대로)
  const pop = list.length > tabsLen ? "pop" : "";
  tabsLen = list.length;
  $("gTabs").innerHTML =
    list
      .map(
        ([name, n, cls = ""], i) =>
          `<span class="${i === active ? "on" : ""} ${cls} ${cls && pop}">${name}<i>${i === 0 ? total : n}</i></span>`,
      )
      .join("") + `<span class="add">${icon("folder")}폴더 추가</span>`;
  // 고른 탭이 '폴더 추가' 버튼 밑에 가리면, 앱처럼 탭 줄을 왼쪽으로 밀어 다 보이게 한다.
  // 사람 폴더가 막 생긴 뒤(아직 '전체')에도 곧 누를 마지막 폴더 탭('나')이 가리지 않게 미리 민다
  const tabs = [...$("gTabs").children];
  const add = tabs.pop();
  const target = active > 0 ? active : tabs.length - 1;
  const on = tabs[target];
  const over = target > 0 ? on.offsetLeft + on.offsetWidth + 12 - add.offsetLeft : 0;
  tabs.forEach((t) => (t.style.transform = over > 0 ? `translateX(${-over}px)` : ""));
}

// ── 첫 장면 '모으기': 글 두 개와 친구들 폰 세 대 (시각은 스크롤 시각 T) ──
const INTRO_TEXT = [
  {
    a: -1,
    c: 0.05,
    hero: true,
    title: "사진 보내달라는 말,<br />이제 <em>그만!</em>",
    cta: true,
  },
  // 사용 방법 세 단계: 단톡방 연출(앱 전환 → 링크 보내기 → 다 같이 모임)과 함께 하나씩 바뀐다. u = [뜨는 시각, 빠지는 시각] (시계 U)
  {
    u: [APP_IN[0] + 0.15, APP_OUT[0]],
    tag: "① 링크 만들기",
    kicker: "사진 보내 달라는 말이 나오면",
    title: "쏙에서<br /><em>방 하나</em> 만들고",
  },
  {
    u: [APP_OUT[0] + 0.1, LINK_TAP + 0.05],
    tag: "② 단톡방에 보내기",
    title: "단톡방에<br /><em>링크 하나만</em> 올리면",
    desc: "앱이 없어도<br />링크만 누르면 바로 들어와요.",
  },
  {
    // 링크를 누른 직후엔 아직 단톡방 말풍선이 걷히는 중이라, 단톡방이 사진방 화면으로 바뀌기 시작할 때(MS = 4.2) 띄운다.
    // MS 는 이 아래에서 정의되므로 같은 시각을 LINK_TAP 기준으로 적는다
    u: [LINK_TAP + 0.75, Infinity],
    c: INTRO - 0.05,
    tag: "③ 다 같이 올리기",
    // '링크'는 ②에서 이미 말했으니, 여기선 결과(각자 찍은 사진이 한곳에)만 말한다
    title: "각자 찍은 사진이<br /><em>한곳에</em> 쏙!",
    // 첫 화면 "사진 보내달라는 말, 이제 그만!"과 이어진다 ('원본'은 다음 장면 ④에서 말한다)
    desc: "이제 따로 보내 달라고 안 해도 돼요.",
  },
];
const introEls = INTRO_TEXT.map((t) => {
  const el = document.createElement("div");
  el.className = t.hero ? "tx hero" : "tx";
  el.innerHTML =
    // 첫 화면: 앱 아이콘·이름과 '무슨 서비스인지' 한 줄을 헤드라인 위에 둔다
    (t.hero
      ? // 머리글을 없앤 대신 로고(손 흔드는 쏙이 + 쏙)를 여기 둔다
        `<div class="hero-id"><span class="hero-kind"><span class="kind-logo"><img src="/landing/assets/mascot-hello.svg" alt="" />쏙</span></span></div>`
      : "") +
    (t.tag ? `<span class="tag">${t.tag}</span>` : "") +
    (t.kicker ? `<span class="kicker">${t.kicker}</span>` : "") +
    (t.hero ? `<h2 class="title">${t.title}</h2>` : `<p class="title">${t.title}</p>`) +
    (t.desc ? `<p class="desc">${t.desc}</p>` : "") +
    // 결론부터: 제목은 왜(문제), 이 한 줄은 무엇(서비스 정의), 스크롤은 어떻게
    (t.hero
      ? `<p class="hero-lead">링크 하나로 다 같이 찍은 사진을<br />원본 그대로 모으는 <b>사진 공유 서비스</b></p>`
      : "") +
    (t.cta
      ? `<div class="hero-ctas">` +
        // 첫 화면은 '설치 없이 링크 하나'가 메시지라 웹 버튼 하나만 둔다. 앱 설치는 끝 화면과 머리글 메뉴에 있다
        `<a class="cta hero-cta" href="/rooms/create?utm_source=landing&amp;utm_medium=web&amp;utm_content=hero">사진 모을 링크 만들기</a>` +
        `</div>`
      : "");
  return $("texts").appendChild(el);
});
// 세 폰의 사진이 빨려 들어가는 쏙 앱 아이콘(지금 홈 화면 아이콘과 같은 그림). 다 모이면 쏙 폰 안으로 들어간다
const APP_ICON = "/icons/apple-touch-icon.png";
$("cams").insertAdjacentHTML(
  "beforeend",
  `<div class="share" id="share"><span class="fcount" id="fCount">0</span>` +
    `<img class="sq-ico" src="${APP_ICON}" alt="" /></div>`,
);
// 폰 주인과 카메라에 비친 친구
// 친구들 폰: 각자 폰의 기본 사진 앱. 3칸 격자에 그날 찍은 사진이 빽빽하다
// 카드 k 는 폰 k%3 의 CELL_OF[k/3] 번째 칸에서 빠져나온다 (그 칸엔 같은 사진을 둔다)
const CELL_OF = [0, 4, 5, 7, 10];
const FOODS = [
  [1, 5, 3, 9, 2, 8, 6],
  [5, 1, 8, 3, 7, 9, 4],
  [3, 9, 1, 5, 6, 2, 8],
];
const cams = PEOPLE.map((p, c) => {
  const el = document.createElement("div");
  el.className = "cam";
  el.innerHTML =
    `<span class="cam-who">${p.name}의 사진첩</span><div class="cam-scr">` +
    `<i class="cam-island"></i><span class="cam-sb">9:41<i></i></span>` +
    `<div class="ga-top"><span>보관함</span><b>최근 항목</b></div><div class="ga-grid"></div>` +
    `<div class="ga-tab"><i class="on"></i><i></i><i></i></div></div>`;
  const grid = el.querySelector(".ga-grid");
  let f = 0;
  for (let i = 0; i < 15; i++) {
    const cell = document.createElement("div");
    cell.className = "ga-cell";
    const j = CELL_OF.indexOf(i);
    if (j >= 0) {
      const it = GRID[j * 3 + c];
      cell.appendChild(picEl(it.p, it.r, it.v));
    } else {
      const img = document.createElement("img");
      img.src = photoSrc(FOODS[c][f++ % FOODS[c].length]);
      img.alt = "";
      cell.appendChild(img);
    }
    grid.appendChild(cell);
  }
  return $("cams").appendChild(el);
});

// ── 첫 화면 연출 ──
//  스크롤 전: 모임이 끝난 뒤 단톡방에 메시지가 한 줄씩 올라온다 (페이지를 연 뒤 흐른 시간으로 그린다)
//  스크롤하면 (스크롤 시각 T): 맨 아래 쏙 링크를 누르고 → 말풍선이 걷히며 단톡방 틀이 쏙 사진방으로 바뀐다
// 단톡방 6명: 나·민지·서연·지수·하린·예나. [보낸 사람, 말, { pics, blur } | { link }]. 보낸 사람이 "나"면 오른쪽 노란 말풍선
const LINK_CARD =
  `<img src="/og/invite.png" alt="" />` +
  // 주소 줄을 <small> 로 쓰면 '내 메시지는 이름(small)을 숨긴다'는 규칙에 걸려 사라진다. span 으로 쓴다
  `<span class="lk-body"><b>사진방에 초대받았어요 📸</b>` +
  `<span class="lk-desc">설치 없이 바로 들어와서 사진을 쏙 올려 주세요!</span>` +
  `<span class="lk-url">ssssok.com</span></span>`;
// 단톡방 대본: 요청 하나 + 불만 셋 + 해결. 불만 셋은 그대로 뒤 장면의 답이 된다
//  흩어져서 여러 번 보냄 → 해결(한곳에) / 화질 깨짐 → 원본으로 받기 / 내 사진 누락 → 내 사진만 받기
// 첫 화면 제목("사진 보내달라는 말")과 이어지게, 요청 "다들 사진 좀 보내줘~"는 링크가 나올 때까지 화면에 남는다
const CHAT = [
  ["민지", "오늘 걸스나잇 완전 재밌었다!! ㅋㅋ"],
  ["민지", "다들 사진 좀 보내줘~"],
  ["서연", "", { pics: ["group-1", "people/minji-2", "cheers", "people/seoyeon-3"] }],
  ["서연", "용량 때문에 나눠서 보낼게 ㅠ"],
  ["하린", "", { pics: ["people/me-2"], blur: true }],
  ["하린", "이거 화질 왜 이래??"],
  ["지수", "나 나온 사진은 아무도 안 보냄? 🥲"],
  ["나", "아 어떡하지 ㅠㅠ"],
  ["나", "한꺼번에 다운로드 받을 수 있는 방법 없나"],
  ["나", "잠시만!!"],
  // 아래 두 메시지는 앱 전환(방 만들기) 뒤에 올라온다 (SEND_AT)
  // 마지막은 내가 보낸 메시지 (노란 말풍선). 내가 쏙 링크 하나를 올리고, 그 링크를 누르면 사진이 한곳에 모인다
  ["나", "그냥 여기 다 올려!"],
  ["나", "", { link: true }],
];
const CHAT_PRE = 2;
const CHAT_BASE = CHAT.length;
// 프로필은 모두 기본 프로필(실루엣), 사람마다 바탕색만 다르게 해 구분한다
const SILHOUETTE = `<svg viewBox="0 0 38 38"><circle cx="19" cy="15" r="6.5" /><path d="M7 34c1.5-7 6.3-10 12-10s10.5 3 12 10z" /></svg>`;
const AV_COLOR = {
  민지: "#8ccfa2",
  서연: "#f2a5c0",
  지수: "#f7b27e",
  하린: "#8fb8ea",
  예나: "#b9a2ec",
};
const chatEls = Array.from({ length: CHAT.length }, () => {
  const el = document.createElement("div");
  el.className = "msg";
  el.innerHTML = `<span class="av">${SILHOUETTE}</span><div class="mb"><small></small><div class="row"><p></p><span class="meta"><i class="ur"></i><time>오후 9:41</time></span></div></div>`;
  return $("chatBody").appendChild(el);
});
// ③ 다 같이 올리기: 사진방이 열린 뒤 폰 옆에 쌓이는 쏙 푸시 알림 (아이폰 알림 배너 모양).
// 폰 화면을 가리지 않게 폰 밖(넓은 화면은 오른쪽)에 둔다. 새 알림이 위에 오고 먼저 온 알림은 아래로 밀린다
const PUSHES = [
  "민지님, 서연님이 방에 들어왔어요",
  "민지님이 사진 34장을 올렸어요",
  "서연님이 사진 28장을 올렸어요",
  "사진 86장이 다 모였어요",
];
const pushEls = PUSHES.map((text) => {
  const el = document.createElement("div");
  el.className = "push";
  el.innerHTML =
    `<img src="/icons/apple-touch-icon.png" alt="" />` +
    `<div><p><b>쏙</b><small>지금</small></p><span>${text}</span></div>`;
  return $("joins").appendChild(el);
});
const introT0 = performance.now();
// 첫 장면 '모으기': 스크롤하면 단톡방 말풍선이 그 자리에서 하나씩 사진으로 바뀌고, 단톡방 틀이 흰 폰 화면으로
// 바뀌는 동안 말풍선이던 사진들이 각자 가까운 갤러리 칸으로 한꺼번에 쏙 들어가 앉는다(위아래 순서 그대로라 엇갈리지 않는다).
// 폰 테두리가 둘러진 뒤엔 나머지 사진이 실시간 업로드처럼 한 장씩 맨 위에 올라오고, 먼저 온 사진은 아래로 밀린다.
// 시계(U, 초)로 흐르고, 스크롤하면 스크롤만큼 앞당겨진다(그때만 빨라진다)
const PH_AT = 3.65; // 말풍선이 걷히기 시작하는 시각
const PH_STAG = 0.06; // 말풍선 사이 간격 (위 말풍선부터)
const PH_LEN = 0.45; // 말풍선 하나가 사진으로 바뀌는 시간
const MS = 4.2; // 단톡방 틀이 폰 화면으로 바뀌기 시작
const MORPH_END = MS + 0.7; // 단톡방 틀이 폰 화면 모양으로 다 바뀌는 시각
const SWAP = [MS + 0.6, MS + 0.85]; // 폰 테두리가 둘러지고 단톡방이 걷혀 갤러리가 드러나는 구간
const B_LAND = SWAP[1] + 0.05; // 말풍선이던 사진들이 갤러리 사진과 바뀌는 시각
// 사진방에 들어오면 '올리기'를 눌러 폰 기본 사진 선택기에서 네 장을 골라 '추가' → 내 사진이 먼저 올라온다
const FAB_TAP = SWAP[1] + 0.3;
const PK_IN = [FAB_TAP + 0.05, FAB_TAP + 0.35];
const PK_PICKS = [0, 1, 2, 3].map((i) => PK_IN[1] + 0.2 + i * 0.2);
const PK_ADD = PK_PICKS.at(-1) + 0.35;
const PK_OUT = [PK_ADD + 0.1, PK_ADD + 0.4];
const UP_AT = PK_OUT[1] + 0.05; // 내 사진이 사진방에 앉기 시작하는 시각
const PICK_TAPS = [[FAB_TAP, "fab"], ...PK_PICKS.map((t, i) => [t, `pk${i}`]), [PK_ADD, "pkAdd"]];
const UP_GAP = 0.06; // 나머지 사진 사이 간격 (가만히 둘 땐 끝까지 같은 빠르기)
const UP_PAUSE = 0.25; // 내 사진 네 장이 앉은 뒤 친구들 사진이 오기 시작할 때까지
const UP_DROP = 0.4; // 나머지 사진이 맨 위에 내려앉는 시간
const UP_PUSH = 0.11; // 앞 사진들이 한 줄 내려가는 시간 (두 장마다 한 줄이라 2 × UP_GAP 보다 짧게)
// 말풍선이던 사진 수 (말풍선을 잰 뒤 정해진다)
// 말풍선을 사진 조각으로 바꾸는 연출은 쓰지 않으니(ghostFrom 은 늘 빈 배열) 스크롤 전에도 0 으로 둔다.
// 스크롤 전후로 값이 바뀌면 첫 장면 길이(landAt)가 달라져 목차 위치가 어긋난다
const ghostCount = () => ghostFrom?.length ?? 0;
// n 번째(1부터) 사진이 갤러리에 앉는 시각: 앞 G 장(말풍선)은 함께, 그 뒤는 한 장씩
const landAt = (n) => {
  const G = ghostCount();
  if (n <= G) return B_LAND;
  // 내가 고른 네 장은 거의 한꺼번에, 그 뒤 친구들 사진은 한 장씩
  if (n <= MY_UP) return UP_AT + (n - 1) * 0.1;
  return UP_AT + MY_UP * 0.1 + UP_PAUSE + (n - MY_UP - 1) * UP_GAP;
};
const ghosts = Array.from({ length: CHAT.length }, () =>
  $("ghosts").appendChild(Object.assign(document.createElement("div"), { className: "ghost" })),
);
const INTRO_LEN = 1.5;
// 사진이 쏙으로 들어가는 되풀이: 카드 사이 간격, 한 장이 나는 시간, 한 바퀴 (초)
const LOOP_STEP = 0.2;
const LOOP_FLY = 0.6;
const LOOP_CYCLE = 15 * LOOP_STEP;
let loopT0 = null;
let introSkip = reduceMotion;
const introE = () => (introSkip ? 99 : (performance.now() - introT0) / 1000);
const backOut = (t) => 1 + 2.7 * Math.pow(t - 1, 3) + 1.7 * Math.pow(t - 1, 2);

// ── 측정 ──
const phone = $("phone");
// 폰 화면 안에서의 위치 (변형은 무시하고 제자리 기준)
function spot(el) {
  let x = 0;
  let y = 0;
  for (let n = el; n && n !== phone; n = n.offsetParent) {
    x += n.offsetLeft;
    y += n.offsetTop;
  }
  return {
    x,
    y,
    w: el.offsetWidth,
    h: el.offsetHeight,
    cx: x + el.offsetWidth / 2,
    cy: y + el.offsetHeight / 2,
  };
}

let M = null;
function measure() {
  const W = stage.clientWidth;
  // 무대는 아래로 더 길 수 있다(--stage-extra). 장면은 묶어 둔 화면 높이 안에 짠다
  const H =
    stage.clientHeight -
    (parseFloat(getComputedStyle(stage).getPropertyValue("--stage-extra")) || 0);
  const mobile = W <= 760;
  const sr = stage.getBoundingClientRect();
  const base = mobile ? "translateX(-50%)" : "translate(-50%, -50%)";
  // 폰 폭: 장면마다 글 길이가 달라도 화면 짜임은 같게 둔다. 글은 모두 머리글 아래 같은 자리에서 시작하고,
  // 폰은 가장 긴 글 아래로 TX_GAP 만큼 띄운 자리에서 화면 아래까지 채운다 (폭이 좁으면 폭에 맞춘다)
  const TX_GAP = 30;
  const sceneTexts = [...introEls.slice(1), ...textEls];
  sceneTexts.forEach((el) => (el.style.top = ""));
  [phone, $("phoneShell")].forEach((el) => el.style.removeProperty("--ph"));
  if (mobile) {
    const txTop = sceneTexts[0].offsetTop;
    const txH = Math.max(...sceneTexts.map((el) => el.offsetHeight));
    // 태그('① 링크 보내기' 같은 칩)가 없는 장면 글은 그만큼 짧아 폰과의 사이가 벌어진다.
    // 태그 자리만큼 내려서, 작은 글·제목이 태그 있는 장면과 같은 줄에 오게 한다
    const tagged = sceneTexts.find((el) => el.querySelector(".tag") && el.querySelector(".kicker"));
    const tagSpace = tagged ? tagged.querySelector(".kicker").offsetTop : 0;
    sceneTexts.forEach((el) => {
      if (!el.querySelector(".tag")) el.style.top = `${txTop + tagSpace}px`;
    });
    const ph = Math.min((W - 64) / 0.462, H * 0.982 - (txTop + txH + TX_GAP));
    [phone, $("phoneShell")].forEach((el) => el.style.setProperty("--ph", `${ph}px`));
  }
  phone.style.transform = base;
  const pr = phone.getBoundingClientRect();
  stage.style.setProperty("--pl", `${pr.left - sr.left}px`);
  stage.style.setProperty("--pr", `${pr.right - sr.left}px`);
  const bw = (pr.width - phone.clientWidth) / 2;
  const inner = {
    x: pr.left - sr.left + bw,
    y: pr.top - sr.top + bw,
    w: phone.clientWidth,
    h: phone.clientHeight,
  };
  const em = parseFloat(getComputedStyle(phone).fontSize);

  // 갤러리 두 줄 격자
  const pad = 1.03 * em;
  const gap = 0.55 * em;
  const tw = (inner.w - pad * 2 - gap) / 2;
  const th = tw * 1.12;
  const gridTop = spot($("gGrid")).y;
  // 빈 상태 문구는 사진 칸이 시작하는 자리부터 화면 아래까지의 가운데에 둔다
  $("gEmpty").style.top = `${$("gGrid").offsetTop}px`;
  const cell = (k) => ({
    x: pad + (k % 2) * (tw + gap),
    y: Math.floor(k / 2) * (th + gap),
  });
  const place = (el, k) => {
    const c = cell(k);
    Object.assign(el.style, {
      width: `${tw}px`,
      height: `${th}px`,
      transform: `translate(${c.x}px, ${c.y}px)`,
    });
  };
  setP.forEach((el) => Object.assign(el.style, { width: `${tw}px`, height: `${th}px` }));

  const taps = {
    kindGo: spot($("kindGo")),
    gAi: spot($("gAi")),
    selDl: spot($("selDl")),
    all: spot($("gAll")),
    mkGo: spot($("mkGo")),
    osSsok: spot($("osSsok")),
    fab: spot($("fab")),
    pkAdd: spot($("pkAdd")),
    ...Object.fromEntries(PK_SEL.map((c, i) => [`pk${i}`, spot(pkCells[c])])),
  };
  // 입장·올리기 알림 자리: 넓은 화면은 폰 오른쪽, 폰 폭(또는 오른쪽이 좁으면)은 폰 화면 위
  const side = !mobile && W - (inner.x + inner.w) > 280;
  const pill = (i) =>
    side
      ? { x: inner.x + inner.w + 36, y: inner.y + inner.h * 0.3 + i * 52, center: false }
      : { x: inner.x + inner.w / 2, y: inner.y + gridTop + th * 0.35 + i * 40, center: true };
  // 폰 폭 첫 화면엔 헤드라인만 두고, 단톡방은 CSS 자리(화면을 다 채운 자리)에서 화면 아래 밖으로 내려 둔다.
  // 스크롤하면 그 자리로 올라온다 (그림자까지 가리게 조금 더 내린다)
  // (사진방으로 바뀌는 중이면 단톡방에 인라인 top 이 남아 있으니 잠깐 지우고 CSS 자리를 잰다)
  const chatEl = $("chat");
  const chatTopInline = chatEl.style.top;
  const chatHeightInline = chatEl.style.height;
  chatEl.style.top = "";
  chatEl.style.height = "";
  const chatDrop = mobile ? H - chatEl.offsetTop + 40 : 0;
  // 폰 폭 ② 단계에서 단톡방 위쪽을 글 높이만큼 줄일 때의 기준 자리·높이
  const chatTop0 = chatEl.offsetTop;
  const chatH0 = chatEl.offsetHeight;
  // 폰 폭 ②·③: 단톡방 위 빈자리에 두 단계 글을 같은 자리(② 글 위치)로 이어서 보인다.
  // 그 자리를 비우려고 단톡방 위쪽을 줄일 길이(sendNeed)와, ③ 글을 ② 자리로 올릴 길이(galShift)를 잰다
  const layoutTop = (el) => {
    let y = 0;
    for (let n = el; n && n !== stage; n = n.offsetParent) y += n.offsetTop;
    return y;
  };
  const sendEl = introEls[INTRO_TEXT.findIndex((t) => t.tag === "② 단톡방에 보내기")];
  const galEl = introEls[INTRO_TEXT.findIndex((t) => t.tag === "③ 다 같이 올리기")];
  const sendTop = layoutTop(sendEl);
  const galShift = sendTop - layoutTop(galEl);
  const sendNeed = mobile
    ? Math.max(0, sendTop + Math.max(sendEl.offsetHeight, galEl.offsetHeight) + 14 - chatTop0)
    : 0;
  chatEl.style.top = chatTopInline;
  chatEl.style.height = chatHeightInline;
  FOLDER_SHOW = mobile ? 0.36 : 0.15;
  M = {
    W,
    H,
    mobile,
    chatDrop,
    chatTop0,
    chatH0,
    sendNeed,
    galShift,
    base,
    inner,
    em,
    tw,
    th,
    gridTop,
    cell,
    place,
    taps,
    makeAt: spot($("make")),
    pill,
  };
}

// 누르는 자리 (폰 화면 안 좌표)
function tapPoint(key) {
  // 새로 생긴 '나' 폴더 탭 (탭 줄은 그때그때 다시 그려지니 누를 때 잰다)
  if (key === "meTab") {
    const t = $("gTabs").children[3];
    if (!t) return { x: -99, y: -99 };
    const r = spot(t);
    return { x: r.cx, y: r.cy };
  }
  return { x: M.taps[key].cx, y: M.taps[key].cy };
}

// 마우스 화살표: 누를 자리로 0.3초 전부터 다가와 누르고, 다음 누름이 0.6초 안이면 사라지지 않고 그리로 옮겨 간다
function renderTap(T, taps = TAPS) {
  const tap = $("tap");
  const i = taps.findLastIndex(([t]) => T >= t - 0.3);
  if (i < 0 || T > taps[i][0] + 0.6) {
    tap.style.opacity = 0;
    return;
  }
  const [start, key] = taps[i];
  const p = tapPoint(key);
  const prev = taps[i - 1];
  const next = taps[i + 1];
  const chain = prev && start - prev[0] <= 0.6;
  const chainNext = next && next[0] - start <= 0.6;
  // 손가락은 누를 자리에서 바로 톡 나타난다 (연달아 누를 땐 앞 자리에서 미끄러져 온다)
  const from = chain ? tapPoint(prev[1]) : p;
  const mv = inOut(seg(T, chain ? Math.min(prev[0] + 0.1, start - 0.05) : start - 0.3, start));
  tap.style.left = `${lerp(from.x, p.x, mv)}px`;
  tap.style.top = `${lerp(from.y, p.y, mv)}px`;
  tap.style.opacity = Math.min(
    chain ? 1 : seg(T, start - 0.3, start - 0.18),
    chainNext ? 1 : 1 - seg(T, start + 0.35, start + 0.55),
  );
  const click = seg(T, start, start + 0.06) - seg(T, start + 0.06, start + 0.14);
  const ring = seg(T, start + 0.02, start + 0.4);
  // 나타날 때 살짝 커지며 톡 들어오고(연달아 누를 땐 그대로), 누를 때 꾹 작아졌다 돌아온다
  const popIn = chain ? 1 : 0.7 + 0.3 * ease(seg(T, start - 0.3, start - 0.15));
  tap.style.setProperty("--press", popIn * (1 - click * 0.18));
  tap.style.setProperty("--ring", ring > 0 ? 1 - ring : 0);
  tap.style.setProperty("--ring-s", 0.6 + ring * 1.2);
  tap.style.transform = "";
}

// ── 갤러리 화면 (사람별 장면 시각 S) ──
// 사진 칸마다 올린 사람·선택 동그라미 (장면마다 바꿔 쓴다)
const upls = setP.map((el) => el.querySelector(".upl"));
const chks = setP.map((el) => el.querySelector(".chk"));
const whoOf = WHO;
// 올린 사람 이름표는 실제 앱(UploaderBadge)처럼 흰 알약, 내 사진만 주황 알약이다 (CSS .upl / .upl.mine)
function renderGallery(S) {
  // 사람 폴더 탭은 폴더가 생긴 뒤(ME_AT)부터
  renderTabs(S < ME_AT ? TABS_A : TABS_B, 0, 86);
  setP.forEach((el, k) => {
    const mine = MINE.indexOf(GRID[k]);
    const only = S >= ME_AT;
    M.place(el, only ? Math.max(mine, 0) : k);
    el.style.visibility = !only || mine >= 0 ? "visible" : "hidden";
    el.style.opacity = 1;
    el.classList.remove("sel");
    chks[k].style.setProperty("--on", 0);
  });
  $("gAll").classList.remove("on");

  // 사람별 정리 확인 시트: 사람별 장면이 시작하면 올라오고, '얼굴 분석하고 정리하기'(1.0)를 누르면 내려간다
  const kindUp = ease(seg(S, 0.3, 0.6)) - ease(seg(S, 1.15, 1.3));
  $("kindSheet").style.transform = `translateY(${(1 - kindUp) * 110}%)`;
  $("ovl").style.opacity = kindUp;
  $("ovl").style.visibility = kindUp <= 0.001 ? "hidden" : "visible";

  $("selbar").style.transform = "translateY(180%)";
  $("saveToast").style.opacity = 0;
  $("folderToast").style.opacity = 0;
  // 시트가 떠 있는 동안 올리기 버튼은 숨는다
  $("fab").style.opacity = within(S, 0.3, PERSON_TO + 0.1) ? 0 : 1;
}

// ③ 받기: 전체 선택 → 다운로드 → "86장을 원본 그대로 저장했어요"
const SAVE_TAPS = [
  [0.35, "all"],
  [0.85, "selDl"],
];
function renderSave(h) {
  const off = seg(h, 1.0, 1.15);
  setP.forEach((el, k) => {
    const on = seg(h, 0.4 + k * 0.02, 0.48 + k * 0.02) * (1 - off);
    chks[k].style.setProperty("--on", on);
    el.classList.toggle("sel", on > 0.5);
  });
  $("gAll").classList.toggle("on", within(h, 0.4, 1.0));
  const selUp = ease(seg(h, 0.45, 0.65)) - ease(seg(h, 0.95, 1.15));
  $("selbar").style.transform = `translateY(${(1 - selUp) * 180}%)`;
  $("selN").textContent = Math.round(86 * seg(h, 0.4, 0.75));
  $("saveToast").style.opacity = life(h, 1.1, 1.25, PH.save.len - 0.3, PH.save.len - 0.1);
  // 저장한 뒤: 카톡으로 받은 깨진 사진과 쏙 원본을 나란히 보여 준다 (첫 화면 "사진 쫌 깨져있는데??"의 답)
  const cmp = $("cmp");
  const ca = ease(seg(h, 1.25, 1.55));
  const P = M.pill(0);
  // 넓은 화면: 폰 오른쪽 가장자리에 살짝 걸쳐 띄우고, 남는 오른쪽 자리를 다 써서 넓게 (최대 460px)
  if (P.center) {
    cmp.style.left = `${P.x}px`;
    cmp.style.width = "";
  } else {
    const left = M.inner.x + M.inner.w - 56;
    cmp.style.left = `${left}px`;
    cmp.style.width = `${Math.max(300, Math.min(460, M.W - left - 28))}px`;
  }
  cmp.style.top = `${P.center ? M.inner.y + M.gridTop : P.y - M.H * 0.06}px`;
  show(
    cmp,
    ca * (1 - seg(h, PH.save.len - 0.25, PH.save.len)),
    (1 - ca) * 16,
    P.center ? "translateX(-50%)" : "",
  );
  $("fab").style.opacity = within(h, 0.3, PH.save.len - 0.1) ? 0 : 1;
  renderTap(h, SAVE_TAPS);
}

// 사용 방법 글: 장면이 시작하면 들어오고 끝날 때 빠진다
function renderTexts(T) {
  TEXT.forEach((t, i) => {
    const a = PH[t.ph[0]].start;
    const last = PH[t.ph.at(-1)];
    const b = last.start + last.len;
    const o = life(T, a + 0.05, a + 0.35, b - 0.3, b);
    show(
      textEls[i],
      o,
      (1 - ease(seg(T, a + 0.05, a + 0.35))) * 20,
      M.mobile ? "" : "translateY(-50%)",
    );
  });
}

// 끝: 폰이 위로 빠지고 링크 만들기·App Store 가 뜬다 (e = 끝 장면에서 흐른 시간, 그 전이면 -1)
function renderEnd(e) {
  const on = e >= 0;
  if (on) {
    const exit = inOut(seg(e, 0, 0.5));
    phone.style.opacity = 1 - exit;
    phone.style.visibility = exit >= 0.999 ? "hidden" : "visible";
    phone.style.transform = `${M.base} translateY(${-exit * M.H * 0.12}px) scale(${1 - exit * 0.1})`;
  }
  $("end").style.top = M.mobile ? `${M.H * 0.5 - stage.getBoundingClientRect().top}px` : "";
  show($("end"), e >= 0.3 ? 1 : 0, 0, "translateY(-50%)");
  const pop = (el, a, b, from = 0.7) => {
    const p = on ? ease(seg(e, a, b)) : 0;
    el.style.opacity = p;
    el.style.transform = `translateY(${(1 - p) * 16}px) scale(${from + p * (1 - from)})`;
  };
  pop($("endLogo"), 0.3, 0.6, 0.6);
  pop($("endKicker"), 0.5, 0.8, 1);
  pop($("endTitle"), 0.65, 0.95, 1);
  pop($("endDesc"), 0.8, 1.1, 1);
  pop($("endCta"), 0.95, 1.25, 0.5);
  pop($("endApps"), 1.1, 1.4, 1);
  ["endCta", "endApps"].forEach((id) => {
    $(id).style.pointerEvents = e > 1.2 ? "auto" : "none";
    $(id).tabIndex = e > 1.2 ? 0 : -1;
  });
}

// 내 사진만 받기: 얼굴로 나뉜 사람별 폴더 카드 (폰 옆). 생겨나는 모습을 먼저 보여 주고 '나' 폴더를 연다
const folderEls = PEOPLE.map((p, i) => {
  const el = document.createElement("div");
  el.className = "fcard";
  el.innerHTML =
    `<span class="f-ava"></span><span class="f-tx"><b>${p.name}</b><span>${p.total}장</span></span>` +
    `<span class="f-th"></span>`;
  el.querySelector(".f-ava").appendChild(picEl(i, 0, 1));
  const th = el.querySelector(".f-th");
  SHOTS.slice(1, 4).forEach(([r, v]) => {
    const t = document.createElement("span");
    t.appendChild(picEl(i, r, v));
    th.appendChild(t);
  });
  return $("joins").appendChild(el);
});
// 폴더 카드가 다 생겨난 뒤에 '나' 탭을 누른다 (renderMeFolder 의 시각을 이만큼 늦춘다).
// 폰 폭은 칩이 폴더 탭으로 날아 들어가는 시간만큼 더 늦춘다 (measure 가 정한다)
let FOLDER_SHOW = 0.15;
function renderFolders(h) {
  const P = M.pill(0);
  if (P.center) {
    renderFolderChips(h);
    return;
  }
  const meOn = h >= FOLDER_SHOW + 0.46;
  folderEls.forEach((el, i) => {
    el.classList.remove("chip");
    el.style.left = `${P.x}px`;
    el.style.top = `${P.y - M.H * 0.04 + i * 84}px`;
    const a = ease(seg(h, 0.04 + i * 0.06, 0.22 + i * 0.06));
    show(el, a, (1 - a) * 14, `scale(${meOn && i === 2 ? 1.06 : 1})`);
    el.classList.toggle("on", meOn && i === 2);
    el.classList.toggle("off", meOn && i !== 2);
  });
}

// 폰 폭: 사람별 폴더를 작은 칩 세 개(얼굴·이름·장수)로 '폴더 3개를 만들었어요' 알림 위에 가로 한 줄로 띄우고,
// 잠깐 보인 뒤 하나씩 폰 안 탭 줄의 제 폴더 탭(민지·서연·나)으로 작아지며 날아 들어간다. 닿는 순간 탭이 톡 튄다
function renderFolderChips(h) {
  const sr = stage.getBoundingClientRect();
  const toast = $("folderToast").getBoundingClientRect();
  const tabs = [...$("gTabs").children];
  const gap = 4;
  folderEls.forEach((el) => {
    el.classList.add("chip");
    el.classList.remove("on", "off");
  });
  // 칩 줄이 폰 화면 폭(양옆 10px 여백)보다 넓으면 줄 전체를 그만큼 줄여 화면 안에 맞춘다
  const raw = folderEls.map((el) => el.offsetWidth);
  const fit = Math.min(
    1,
    (M.inner.w - 20) / (raw.reduce((sum, w) => sum + w, 0) + gap * (raw.length - 1)),
  );
  const widths = raw.map((w) => w * fit);
  const total = widths.reduce((sum, w) => sum + w, 0) + gap * (folderEls.length - 1);
  // 폰 폭에선 알림·카드의 가운데 자리(P.x)가 폰 화면 가운데다
  let x = M.pill(0).x - total / 2;
  folderEls.forEach((el, i) => {
    const w = widths[i];
    const ch = el.offsetHeight * fit;
    const y = toast.top - sr.top - 10 - ch;
    // 줄인 만큼(fit) 칩 왼쪽 위를 기준으로 줄어든 자리에 그린다
    el.style.left = `${x - (raw[i] - w) / 2}px`;
    el.style.top = `${y}px`;
    el.style.transformOrigin = "50% 50%";
    const a = ease(seg(h, 0.04 + i * 0.05, 0.19 + i * 0.05));
    const flyAt = 0.34 + i * 0.05;
    const f = inOut(seg(h, flyAt, flyAt + 0.18));
    // 날아갈 곳: 탭 줄에서 같은 사람의 폴더 탭 (0번은 '전체')
    const tab = tabs[i + 1];
    let dx = 0;
    let dy = 0;
    if (tab) {
      const r = tab.getBoundingClientRect();
      dx = r.left - sr.left + r.width / 2 - (x + w / 2);
      dy = r.top - sr.top + r.height / 2 - (y + ch / 2);
      // 닿는 순간 탭이 톡 커졌다 돌아온다
      const end = flyAt + 0.18;
      tab.style.scale = 1 + 0.18 * (seg(h, end - 0.03, end) - seg(h, end, end + 0.1));
    }
    const o = a * (1 - seg(f, 0.75, 1));
    el.style.opacity = o;
    el.style.visibility = o <= 0.001 ? "hidden" : "visible";
    el.style.transform = `translate(${dx * f}px, ${dy * f + (1 - a) * 10 - (el.offsetHeight - ch) / 2}px) scale(${fit * (0.9 + 0.1 * a) * (1 - 0.72 * f)})`;
    x += w + gap;
  });
}

// ── 첫 장면 '모으기': 찰칵 → 사진이 흩어짐 → 쏙 폰으로 모여 갤러리에 쌓임 (T 는 스크롤 시각) ──
// 첫 장면 상태: 시계 U, 말풍선 조각이 출발한 자리(처음 한 번 잰다)
let introU = 0;
// 폰 폭 ② 단계에서 단톡방 위쪽을 줄이는 길이(px). renderIntro 가 정하고 renderChat 이 쓴다
let sendPush = 0;
let ghostFrom = null;
function renderIntro(T) {
  const N = setP.length;
  const UP_END = landAt(N) + UP_DROP;
  const UP_FULL = UP_END + 0.2;
  // 맨 위로 되돌리면(T < 0.03) 첫 화면(단톡방)으로 돌아간다
  if (T < 0.03) {
    ghostFrom = null;
    chatEls.forEach((el) => delete el.dataset.ghost);
  }
  // 저절로 흐르지 않는다: 스크롤한 만큼만 진행한다 (멈추면 그 자리에 멈춘다)
  const U = reduceMotion ? UP_FULL : seg(T, 0.05, INTRO - 0.3) * UP_FULL;
  introU = U;

  // 폰 폭: 단톡방이 올라오는 만큼 헤드라인은 위로 빠진다 (넓은 화면은 그대로)
  const heroOut = CHAT_RISE ? chatRise : 0;
  // 폰 폭: 단톡방이 화면을 꽉 채우는 동안(앱 전환 중 제외)엔 단계 글이 단톡방 위에 겹치니 단계 이름표만 띄운다
  const appSw = inOut(seg(U, APP_IN[0], APP_IN[1])) - inOut(seg(U, APP_OUT[0], APP_OUT[1]));
  const chipOnly = M.mobile && U < SWAP[1] && appSw < 0.5;
  // 폰 폭: ② 글이 들어오는 동안 단톡방 위쪽이 줄어들고, ③ 을 지나 사진방 화면으로 바뀔 때까지 그대로 둔다
  const sendIn = INTRO_TEXT.find((t) => t.tag === "② 단톡방에 보내기").u[0];
  const galIn = INTRO_TEXT.find((t) => t.tag === "③ 다 같이 올리기").u[0];
  sendPush = M.sendNeed * ease(seg(U, sendIn, sendIn + 0.3));
  INTRO_TEXT.forEach((t, i) => {
    // ② 단톡방에 보내기는 처음부터 끝까지 단톡방 안의 장면이라, 이름표만 두면 폰 폭에선 제목·설명이 한 번도 안 보인다.
    // 그래서 ②·③ 은 글을 다 보이고, 그 대신 단톡방 위쪽을 글 높이만큼 줄여 둔다(sendPush).
    // ③ 글은 ② 글이 있던 자리에 이어서 뜨고, 폰 화면이 둘러지는 동안 제자리로 내려간다
    const isGal = M.mobile && t.tag === "③ 다 같이 올리기";
    const isTop = isGal || (M.mobile && t.tag === "② 단톡방에 보내기");
    introEls[i].classList.toggle("chip-only", !!t.u && chipOnly && !isTop);
    // 사용 단계 글은 시계 U 로 뜨고 빠진다. 마지막 단계는 첫 장면이 끝날 때(t.c) 빠진다
    const inA = t.u ? ease(seg(U, t.u[0], t.u[0] + 0.3)) : 0;
    // 첫 화면 헤드라인은 스크롤을 조금만 해도(시계가 흐르면) 빠진다
    const o = t.u
      ? Math.min(
          inA,
          // 마지막 단계는 빠지는 시각이 없다 (Infinity + 0.25 - Infinity 는 NaN 이 된다).
          // 폰 폭 ② 는 단톡방 위 빈자리가 비지 않게 ③ 글이 들어오기 직전까지 남았다가, 다 사라진 뒤 ③ 이 뜬다
          // (둘이 겹쳐 비치면 글씨가 뒤섞여 보인다)
          isTop && !isGal
            ? 1 - seg(U, galIn - 0.25, galIn)
            : Number.isFinite(t.u[1])
              ? 1 - seg(U, t.u[1], t.u[1] + 0.25)
              : 1,
          t.c ? 1 - seg(T, t.c, t.c + 0.3) : 1,
        )
      : Math.min(life(T, t.a, t.a + 0.3, t.c, t.c + 0.3), 1 - seg(U, 0.05, 0.3), 1 - heroOut);
    show(
      introEls[i],
      o,
      (1 - (t.u ? inA : ease(seg(T, t.a, t.a + 0.3)))) * 20 -
        (i === 0 ? heroOut * 48 : 0) +
        (isGal ? M.galShift * (1 - inOut(seg(U, SWAP[0], SWAP[1]))) : 0),
      M.mobile && i !== 0 ? "" : "translateY(-50%)",
    );
    // 폰 폭에서 이름표만 보일 때: 단계마다 글 길이가 달라 이름표가 단톡방 바로 위에 붙거나 겹친다.
    // 위 '링크 만들기' 버튼과 단톡방 사이 한가운데로 옮겨 위아래 간격을 같게 둔다
    if (t.u && chipOnly && !isTop && o > 0.001) {
      const tag = introEls[i].querySelector(".tag").getBoundingClientRect();
      const above = $("startBtn").getBoundingClientRect().bottom;
      const below = $("chat").getBoundingClientRect().top;
      const shift = (above + below) / 2 - (tag.top + tag.bottom) / 2;
      introEls[i].style.transform += ` translateY(${shift}px)`;
    }
  });
  // 첫 화면 연출 도중에 스크롤하면 바로 마지막 상태로 넘긴다
  if (T > 0.02) introSkip = true;
  const E = introE();
  show(
    $("hint"),
    // 폰 폭에선 올라오는 단톡방 위에 겹치지 않게 스크롤하자마자 걷는다
    Math.min(ease(seg(E, 1.0, 1.3)), 1 - seg(chatS + T, 0.02, CHAT_RISE ? 0.1 : 0.3)),
    0,
    "translateX(-50%)",
  );

  renderChat(E, T, chatStep + 2 * seg(U, SEND_AT[0], SEND_AT[1]));
  renderLinkTap(T);

  // 예전 '친구들 사진첩 세 대 → 쏙 아이콘' 연출은 쓰지 않는다 (초대 카드·입장 알림은 '① 링크 보내기'에서 쓴다)
  cams.forEach((el) => (el.style.visibility = "hidden"));
  $("share").style.visibility = "hidden";

  // 쏙 폰: 바뀐 단톡방(이제 갤러리 화면) 둘레에 테두리가 둘러진다. 화면은 그 위의 단톡방이 걷히며 드러난다
  const appear = inOut(seg(U, SWAP[0], SWAP[1]));
  phone.style.opacity = appear;
  phone.style.visibility = appear <= 0.001 ? "hidden" : "visible";
  phone.style.transform = `${M.base} scale(${1.03 - appear * 0.03})`;
  // ① 링크 만들기: 앱 전환 동안엔 폰이 쏙 '방 만들기' 화면으로 먼저 들어온다 (위 폰 상태를 덮어쓴다)
  renderMake(U);
  renderPicker(U);
  if (appSw <= 0.001) renderTap(U, PICK_TAPS);

  // 갤러리: 사진이 실시간으로 한 장씩 맨 위에 올라온다 (사진 k 는 N-k 번째로 올라온다).
  // 두 칸짜리 격자라 줄 단위로 민다: 맨 윗줄이 차 있을 때 새 사진이 오면 모든 사진이 한 줄 아래로 내려가고,
  // 새 사진은 빈 윗줄에 앉는다. S = 아직 내려가지 않은 줄 수. 끝나면 0 이 되어 사진 k 가 k 번째 칸에 놓인다
  const G = ghostCount();
  let S = Math.floor(N / 2);
  for (let n = 2; n <= N; n++) {
    if ((N - n) % 2 !== 1) continue;
    // 말풍선이던 앞 G 장은 처음부터 '그만큼 올라온 뒤'의 자리에 앉는다
    S -= n <= G ? 1 : inOut(seg(U, landAt(n) - UP_PUSH, landAt(n)));
  }
  const rowH = M.cell(2).y;
  let landed = 0;
  setP.forEach((el, k) => {
    // 말풍선이던 사진(앞 G 장)은 조각과 바뀌는 순간 그대로 앉고, 나머지는 위에서 살짝 내려앉는다
    const flown = N - k <= G;
    const a = flown ? (U >= landAt(N - k) ? 1 : 0) : seg(U, landAt(N - k), landAt(N - k) + UP_DROP);
    if (a > 0) landed++;
    const c = M.cell(k);
    // 위에서 살짝 내려앉으며 톡 커진다
    const y = c.y - S * rowH - (1 - ease(a)) * rowH * 0.12;
    const o = clamp(a * 2.5);
    el.style.zIndex = a < 1 ? 2 : 1;
    el.style.transform = `translate(${c.x}px, ${y}px) scale(${0.86 + 0.14 * backOut(a)})`;
    el.style.opacity = o;
    el.style.visibility = o <= 0.001 ? "hidden" : "visible";
  });
  renderTabs(TABS_A, 0, Math.round((86 * landed) / N));
  // 폰 테두리가 둘러지면(사진방에 들어오면) 쏙 푸시 알림이 하나씩 위에서 내려와 쌓이고, 다음 장면 직전에 빠진다.
  // 알림이 네 개라 간격을 좁혀, 마지막 알림이 들어오는 시각은 세 개일 때와 같게 둔다
  const pushIn = pushEls.map((_, i) =>
    ease(seg(U, UP_AT + 0.45 + i * 0.25, UP_AT + 0.7 + i * 0.25)),
  );
  const pushOut = seg(T, INTRO - 0.4, INTRO - 0.1);
  // 자리: 넓은 화면은 폰 오른쪽, 폰 폭(옆에 자리가 없음)은 폰 위쪽 가운데
  const P = M.pill(0);
  const top = P.center ? M.inner.y + M.em * 0.6 : M.inner.y + M.inner.h * 0.2;
  pushEls.forEach((el, i) => {
    const a = pushIn[i];
    // 나보다 나중에 온 알림 수(들어오는 중이면 소수)
    const below = pushIn.slice(i + 1).reduce((sum, b) => sum + b, 0);
    if (P.center) {
      // 폰 폭: 아래로 늘어놓으면 폰 화면 위쪽 절반을 가린다. 아이폰 알림처럼 최신 알림만 또렷하게 두고,
      // 먼저 온 알림은 그 뒤에 한 칸마다 9px 아래로·6% 작게 겹쳐 두 장까지만 비친다(세 번째부터는 사라진다)
      const depth = Math.min(below, 3);
      const o = a * (1 - pushOut) * clamp(3 - depth);
      el.style.left = `${P.x}px`;
      el.style.top = `${top + Math.min(depth, 2) * 9 - (1 - a) * 18}px`;
      el.style.zIndex = 18 + i;
      el.style.transformOrigin = "50% 0";
      el.style.transform = `translateX(-50%) scale(${1 - 0.06 * Math.min(depth, 2)})`;
      el.style.opacity = o;
      el.style.visibility = o <= 0.001 ? "hidden" : "visible";
      return;
    }
    // 넓은 화면: 폰 오른쪽에 나보다 나중에 온 알림만큼 아래로 밀려 나란히 쌓인다
    const y = top + below * (el.offsetHeight + 10) - (1 - a) * 18;
    el.style.left = `${P.x}px`;
    el.style.top = `${y}px`;
    el.style.zIndex = "";
    el.style.transformOrigin = "";
    el.style.opacity = a * (1 - pushOut);
    el.style.visibility = a * (1 - pushOut) <= 0.001 ? "hidden" : "visible";
    el.style.transform = "";
  });

  // 말풍선 조각: 말풍선 자리에서 글자가 지워지며 작은 사진으로 바뀌고(PH) → 틀이 바뀌는 동안 가까운 갤러리 칸으로 들어가 앉는다.
  // 위 말풍선이 가장 최근 사진(위 칸), 아래로 갈수록 먼저 올라온 사진(아래 칸)이라 서로 엇갈리지 않는다.
  // 폰 화면 아래로 넘치는 칸은 화면 안에서 잘린다. B_LAND 에 진짜 갤러리 사진과 바뀐다
  const S_G = Math.floor((N - G) / 2);
  const bottom = M.inner.y + M.inner.h;
  ghosts.forEach((el, i) => {
    const F = ghostFrom?.[i];
    const vis = F && U > 0 && U < B_LAND;
    el.style.opacity = vis ? 1 : 0;
    el.style.visibility = vis ? "visible" : "hidden";
    if (!vis) return;
    // ① 말풍선 → 작은 사진 (말풍선 높이 가운데, 내 말풍선은 오른쪽 끝에 맞춘다).
    // 메시지 사이가 좁으니 사진 높이는 말풍선 높이 정도로 (옆 메시지 사진과 겹치지 않게)
    const ph = inOut(seg(U, PH_AT + i * PH_STAG, PH_AT + i * PH_STAG + PH_LEN));
    const phh = Math.min(M.th * 0.5, Math.min(F.h, 40) * 1.05);
    const pw = (phh * M.tw) / M.th;
    const P = {
      x: F.mine ? F.x + F.w - pw : F.x,
      y: F.y + F.h / 2 - phh / 2,
      w: pw,
      h: phh,
    };
    // ② 그 사진이 앉을 갤러리 칸 ('G 장 올라온 뒤' 배치)
    const c = M.cell(F.cell);
    const D = {
      x: M.inner.x + c.x,
      y: M.inner.y + M.gridTop + c.y,
      w: M.tw,
      h: M.th,
    };
    // 칸은 모두 말풍선보다 아래쪽이라, 아래 말풍선부터 먼저 움직여 길을 비킨다
    const lag = (G - 1 - i) * 0.025;
    const mv = inOut(seg(U, MS + 0.1 + lag, MORPH_END + lag));
    // 작은 사진 크기 그대로 칸 가운데로 먼저 옮겨 가고, 거의 다 와서 칸 크기로 커진다
    // (함께 커지며 옮겨 가면 이웃 사진과 가는 길에 겹친다)
    // 폰 폭에선 말풍선 사이가 좁아, 넓은 말풍선 위에 사진이 흐리게 겹쳐 보인다.
    // 그래서 말풍선을 먼저 사진 크기로 줄이고(글자는 그동안 지움) 다 줄어든 뒤에 사진을 띄운다
    const sz = M.mobile ? ease(seg(ph, 0, 0.5)) : ph;
    const A = {
      x: lerp(F.x, P.x, sz),
      y: lerp(F.y, P.y, sz),
      w: lerp(F.w, P.w, sz),
      h: lerp(F.h, P.h, sz),
    };
    // 가운데가 먼저 칸 가운데에 닿고(0~0.7), 그 자리에서 칸 크기로 커진다(0.7~1)
    const go = ease(seg(mv, 0, 0.7));
    const grow = inOut(seg(mv, 0.7, 1));
    const w = lerp(A.w, D.w, grow);
    const hh = lerp(A.h, D.h, grow);
    // 옆으로 먼저 비켜선 뒤 위아래로 간다 (같은 줄로 모이는 이웃 사진과 가는 길에 부딪히지 않게)
    const cx = lerp(A.x + A.w / 2, D.x + D.w / 2, ease(seg(mv, 0, 0.4)));
    const cy = lerp(A.y + A.h / 2, D.y + D.h / 2, go);
    const x = cx - w / 2;
    const y = cy - hh / 2;
    const h = Math.min(hh, bottom - y);
    if (h < 1) {
      el.style.opacity = 0;
      el.style.visibility = "hidden";
      return;
    }
    el.style.width = `${w}px`;
    el.style.height = `${h}px`;
    el.style.transform = `translate(${x}px, ${y}px)`;
    el.style.zIndex = 30;
    el.style.borderRadius = `${lerp(F.r, M.em * 0.77 * (0.6 + 0.4 * mv), ph)}px`;
    el.style.padding = F.pad.replace(/[\d.]+px/g, (v) => `${parseFloat(v) * (1 - sz)}px`);
    el.style.fontSize = F.fs;
    if (M.mobile) {
      // 글자가 다 지워진 뒤 사진이 빠르게 또렷해지고, 사진이 다 덮으면 말풍선 색(카톡 노랑 등)도 걷힌다
      el.style.background = `rgba(${F.bg.join(",")},${1 - seg(ph, 0.5, 0.6)})`;
      el.firstChild.style.opacity = 1 - seg(ph, 0, 0.25);
      el.lastChild.style.opacity = seg(ph, 0.3, 0.55);
    } else {
      el.style.background = `rgb(${F.bg.join(",")})`;
      el.firstChild.style.opacity = 1 - seg(ph, 0, 0.4);
      el.lastChild.style.opacity = seg(ph, 0.25, 0.85);
    }
    el.style.boxShadow = mv < 1 ? `0 6px 16px -8px rgba(43,33,27,${0.3 * (1 - mv)})` : "none";
  });
}

// '올리기' → 폰 기본 사진 선택기. 칸 PK_SEL[i] 에 내가 고를 사진(맨 먼저 올라올 네 장)을 두고, 나머지 칸엔 다른 사진을 채운다
const PK_SEL = [1, 3, 4, 8];
const pkCells = (() => {
  const N = GRID.length;
  const others = GRID.slice(0, N - MY_UP);
  let o = 0;
  return Array.from({ length: 15 }, (_, c) => {
    const i = PK_SEL.indexOf(c);
    const it = i >= 0 ? GRID[N - 1 - i] : others[o++ % others.length];
    const cell = document.createElement("div");
    cell.className = "pk-cell";
    cell.append(picEl(it.p, it.r, it.v));
    if (i >= 0) cell.insertAdjacentHTML("beforeend", `<b class="pk-chk">${i + 1}</b>`);
    return $("pkGrid").appendChild(cell);
  });
})();
function renderPicker(U) {
  const a = inOut(seg(U, PK_IN[0], PK_IN[1])) - inOut(seg(U, PK_OUT[0], PK_OUT[1]));
  const picker = $("picker");
  const dim = $("pkDim");
  const vis = a > 0.001 ? "visible" : "hidden";
  picker.style.visibility = vis;
  dim.style.visibility = vis;
  dim.style.opacity = a;
  picker.style.transform = `translateY(${(1 - a) * 105}%)`;
  if (a <= 0.001) return;
  let n = 0;
  PK_SEL.forEach((c, i) => {
    const s = seg(U, PK_PICKS[i] + 0.02, PK_PICKS[i] + 0.16);
    if (s > 0) n++;
    pkCells[c].classList.toggle("sel", s > 0);
    pkCells[c].querySelector(".pk-chk").style.transform = `scale(${backOut(s)})`;
  });
  $("pkAdd").classList.toggle("on", n > 0);
  $("pkAdd").textContent = n > 0 ? `추가(${n})` : "추가";
  $("pkFoot").textContent = n > 0 ? `${n}장의 사진이 선택됨` : "사진을 선택하세요";
}

// ① 링크 만들기 (앱 전환): 단톡방이 왼쪽으로 밀리고 폰 바탕화면이 들어온다 → 쏙 아이콘 → '방 만들기' 화면.
// 방 제목을 쓰고 '방 만들기'를 누르면 "공유 링크를 복사했어요." → 다시 단톡방으로 돌아간다 (문구는 실제 앱과 같다)
const MAKE_TITLE = "걸스나잇 파티 ♥";
function renderMake(U) {
  const sw = inOut(seg(U, APP_IN[0], APP_IN[1])) - inOut(seg(U, APP_OUT[0], APP_OUT[1]));
  const make = $("make");
  make.style.visibility = sw > 0.001 ? "visible" : "hidden";
  // 앱 전환 동안 단톡방은 폰 뒤로 물러난다 (폰 z-index 3, 옆 버튼 껍데기 2 보다 아래)
  $("chat").style.zIndex = sw > 0.001 ? "1" : "";
  if (sw <= 0.001) {
    phone.classList.remove("os-dark");
    // 방 만들기 화면에 직접 준 visible 은 부모(#make)의 hidden 을 이긴다. 같이 숨겨야 사진방이 드러난다
    $("mkForm").style.visibility = "hidden";
    $("mkSplash").style.visibility = "hidden";
    return;
  }
  const chat = $("chat");
  // 단톡방은 옆으로 밀린 채 반투명하게 남아, 카톡에서 잠깐 나왔다는 게 보이게 한다.
  // 폰 폭에선 단톡방이 화면을 꽉 채워서, 조금만 밀면 단계 글과 폰 뒤에 겹쳐 보인다 → 화면 밖까지 밀고 거의 지운다
  const slide = M.mobile ? 100 : 58;
  chat.style.transform += ` translateX(${-sw * slide}%) scale(${1 - sw * 0.08})`;
  chat.style.opacity = +chat.style.opacity * (1 - sw * (M.mobile ? 0.95 : 0.6));
  phone.style.opacity = sw;
  phone.style.visibility = "visible";
  phone.style.transform = `${M.base} translateX(${(1 - sw) * 58}%) scale(${0.92 + sw * 0.08})`;
  const typed = MAKE_TITLE.slice(
    0,
    Math.round(seg(U, MAKE_TYPE[0], MAKE_TYPE[1]) * MAKE_TITLE.length),
  );
  $("mkTitle").innerHTML = typed
    ? `${typed}<i class="caret"></i>`
    : `<i class="caret"></i><em>예) 제주 여행</em>`;
  $("mkToast").style.opacity = life(U, ...MAKE_TOAST);
  // 쏙 아이콘을 누르면 방 만들기 화면이 아이콘 자리에서 커지며 열린다
  const open = inOut(seg(U, APP_OPEN[0], APP_OPEN[1]));
  // 바탕화면이 들어온 뒤 쏙 아이콘을 누르기 전까지 쏙을 점점 강조한다 (다른 아이콘은 흐려짐)
  $("osHome").style.setProperty("--focus", ease(seg(U, APP_IN[1] - 0.05, OS_TAP - 0.05)));
  // 손가락이 닿으면 아이폰처럼 쏙 아이콘이 꾹 눌려 작아지고 어두워졌다가 돌아온다
  const iconPress = seg(U, OS_TAP - 0.02, OS_TAP + 0.04) - seg(U, OS_TAP + 0.06, OS_TAP + 0.14);
  $("osSsok").style.setProperty("--press", ease(clamp(iconPress)));
  // 검은 바탕화면이 보이는 동안(앱이 열리기 전)엔 상태 표시줄 글자를 흰색으로
  phone.classList.toggle("os-dark", open < 0.5);
  // 아이폰에서 앱을 여는 것처럼: 아이콘 네모가 화면 전체로 늘어나고(모서리는 점점 펴진다),
  // 늘어나는 동안엔 쏙 로고 화면이 보이다가 다 열릴 즈음 방 만들기 화면으로 바뀐다. 뒤 바탕화면은 살짝 다가오며 어두워진다
  const ic = M.taps.osSsok;
  const W = M.makeAt.w;
  const H = M.makeAt.h;
  const ix = ic.x - M.makeAt.x;
  const iy = ic.y - M.makeAt.y;
  const top = iy * (1 - open);
  const left = ix * (1 - open);
  const right = (W - ix - ic.w) * (1 - open);
  const bottom = (H - iy - ic.h) * (1 - open);
  const radius = ic.w * 0.225 * (1 - open) + 28 * open;
  const form = $("mkForm");
  form.style.transform = "";
  form.style.opacity = 1;
  form.style.borderRadius = "";
  form.style.clipPath = `inset(${top}px ${right}px ${bottom}px ${left}px round ${radius}px)`;
  form.style.visibility = open > 0.001 ? "visible" : "hidden";
  const content = clamp((open - 0.55) / 0.4);
  for (const c of form.children) {
    if (c.id === "mkToast") continue;
    c.style.opacity = content;
    c.style.transform = `translateY(${(1 - content) * 0.6}em)`;
  }
  const splash = $("mkSplash");
  splash.style.visibility = open > 0.001 && content < 1 ? "visible" : "hidden";
  splash.style.left = `${left}px`;
  splash.style.top = `${top}px`;
  splash.style.width = `${W - left - right}px`;
  splash.style.height = `${H - top - bottom}px`;
  splash.style.borderRadius = `${radius}px`;
  splash.style.opacity = 1 - content;
  // 로고는 처음엔 아이콘을 꽉 채우고, 커지는 동안 아이콘 크기의 1.4배까지만 자란다
  splash.firstElementChild.style.width = `${ic.w * (1 + 0.4 * open)}px`;
  const home = $("osHome");
  home.style.transformOrigin = `${ic.cx - M.makeAt.x}px ${ic.cy - M.makeAt.y}px`;
  home.style.transform = `scale(${1 + 0.12 * open})`;
  home.style.filter = `brightness(${1 - 0.5 * open})`;
  // 앱이 열리며 커지는 동안 뒤 아이콘들은 점점 흐려지다가 사라진다 (열리는 화면 옆에 아이콘이 남지 않게).
  // 검은 바탕은 그대로 둬야 뒤의 흰 화면이 비치지 않는다
  const fade = ease(clamp(open / 0.8));
  for (const part of home.children) {
    part.style.filter = `blur(${fade * 8}px)`;
    part.style.opacity = 1 - fade;
  }
  renderTap(U, [
    [OS_TAP, "osSsok"],
    [MAKE_TAP, "mkGo"],
  ]);
}

// 해결: 스크롤을 시작하면 단톡방 맨 아래 쏙 링크를 누른다. 누르면 말풍선이 사진이 되어 사진방에 모인다
function renderLinkTap(T) {
  const tap = $("chatTap");
  const U = introU;
  const bub = chatEls.find((el) => el.dataset.link === "1")?.querySelector("p");
  const o =
    T > 0.02 && bub ? life(U, LINK_TAP - 0.3, LINK_TAP - 0.22, LINK_TAP + 0.2, LINK_TAP + 0.35) : 0;
  tap.style.opacity = o;
  if (!bub) return;
  const click = seg(U, LINK_TAP - 0.06, LINK_TAP) - seg(U, LINK_TAP, LINK_TAP + 0.08);
  const ring = seg(U, LINK_TAP, LINK_TAP + 0.35);
  bub.style.transform = `scale(${1 - click * 0.05})`;
  bub.style.boxShadow =
    ring > 0 && ring < 1 ? `0 0 0 ${3 + ring * 6}px rgba(246, 109, 59, ${0.45 * (1 - ring)})` : "";
  if (o <= 0.001) return;
  const r = bub.getBoundingClientRect();
  const sr = stage.getBoundingClientRect();
  tap.style.left = `${r.left - sr.left + r.width * 0.5}px`;
  tap.style.top = `${r.top - sr.top + r.height * 0.42}px`;
  tap.style.setProperty("--press", 1 - click * 0.18);
  tap.style.setProperty("--ring", ring > 0 ? 1 - ring : 0);
  tap.style.setProperty("--ring-s", 0.6 + ring * 1.2);
}

// 단톡방: 메시지가 스크롤한 만큼 아래에서 올라오고, 오래된 것은 위로 밀려 흐려진다. 끝까지 가면 멈춘다.
// 그려 둔 말풍선 k 는 k 번째로 최근 메시지를 보여 준다. 같은 사람이 이어 보내면 프로필·이름은 첫 말풍선에만.
// 시각은 같은 사람 무리의 마지막 말풍선에만, 안 읽은 수는 최근 것일수록 크다. 스크롤하면 단톡방이 빠진다
let chatN = -2;
let chatH = [];
const chatAt = (g) => CHAT[((g % CHAT.length) + CHAT.length) % CHAT.length];
function renderChat(E, T, S) {
  // 스크롤하면 단톡방 틀이 폰 화면 자리·크기로 바뀌며 바탕이 하늘색 → 흰색이 되고, 폰 테두리가 둘러지면 걷힌다
  const chat = $("chat");
  const U = introU;
  const o = ease(seg(E, 0.15, 0.6)) * (1 - inOut(seg(U, SWAP[0] + 0.1, SWAP[1])));
  const m = inOut(seg(U, MS, MORPH_END));
  const clear = () =>
    [
      "left",
      "top",
      "right",
      "bottom",
      "width",
      "height",
      "background",
      "borderRadius",
      "boxShadow",
    ].forEach((k) => (chat.style[k] = ""));
  if (m <= 0) {
    clear();
    show(
      chat,
      o,
      (1 - ease(seg(E, 0.15, 0.6))) * 30 + M.chatDrop * (1 - chatRise),
      M.mobile ? "" : "translate(-50%, -50%)",
    );
    // 폰 폭 ②: 아래(링크 말풍선·입력칸)는 그대로 두고 위쪽만 내려, 비운 자리에 ② 제목·설명을 보인다
    if (M.mobile && sendPush > 0.5) {
      chat.style.top = `${M.chatTop0 + sendPush}px`;
      chat.style.height = `${M.chatH0 - sendPush}px`;
    }
  } else {
    // 제자리(덮어쓴 값을 지우고 잰다) → 폰 화면
    if (!M.chatRest) {
      clear();
      chat.style.transform = M.mobile ? "" : "translate(-50%, -50%)";
      const r = chat.getBoundingClientRect();
      const sr = stage.getBoundingClientRect();
      M.chatRest = {
        x: r.left - sr.left,
        y: r.top - sr.top,
        w: r.width,
        h: r.height,
      };
    }
    // 폰 폭에선 ② 에서 줄여 둔 단톡방(위쪽이 sendNeed 만큼 내려온 자리)에서 폰 화면으로 바뀐다
    const A = M.mobile
      ? { ...M.chatRest, y: M.chatRest.y + M.sendNeed, h: M.chatRest.h - M.sendNeed }
      : M.chatRest;
    const B = M.inner;
    chat.style.right = "auto";
    chat.style.bottom = "auto";
    chat.style.left = `${lerp(A.x, B.x, m)}px`;
    chat.style.top = `${lerp(A.y, B.y, m)}px`;
    chat.style.width = `${lerp(A.w, B.w, m)}px`;
    chat.style.height = `${lerp(A.h, B.h, m)}px`;
    chat.style.borderRadius = `${lerp(24, phone.offsetWidth * 0.12, m)}px`;
    const bg = [178, 199, 217].map((v) => Math.round(lerp(v, 255, seg(U, MS - 0.05, MS + 0.2))));
    chat.style.background = `rgb(${bg.join(",")})`;
    chat.style.boxShadow = `0 40px 80px -40px rgba(43,33,27,${0.55 * (1 - m)})`;
    show(chat, o, 0, "");
  }
  // 머리줄·입력창은 바뀌기 시작하면 흐려진다 (갤러리 머리글이 그 자리를 잇는다)
  // 머리줄·입력창은 틀이 바뀌기 시작하면, 프로필·이름·시각은 말풍선이 사진으로 바뀌는 동안 흐려진다
  const chrome = 1 - seg(U, MS, MS + 0.3);
  chat.querySelector(".chat-head").style.opacity = chrome;
  chat.querySelector(".chat-input").style.opacity = chrome;
  // 폰 폭에선 남은 말풍선·이름이 사진 조각과 겹쳐 보이지 않게 더 빨리 걷는다
  const msgFade = 1 - seg(U, PH_AT, PH_AT + (M.mobile ? 0.15 : 0.4));
  // 링크 말풍선은 사진이 다 모이기 시작할 때까지 남아 있다가 걷힌다
  const linkFade = 1 - seg(U, MS - 0.1, MS + 0.2);
  if (o <= 0.001) return;
  // 두 개가 깔린 채로 시작해, step(저절로 + 스크롤)만큼 한 줄씩 올라오고 마지막 메시지에서 멈춘다
  const step = S;
  // 처음 깔린 대화(CHAT_PRE 줄)는 다 보인 채로 시작한다 (가장 최근 줄 n 은 올라오는 중이라 아직 안 보인다)
  let n = Math.min(Math.floor(step) + CHAT_PRE, CHAT_BASE - 1);
  let p = inOut(clamp((step % 1) / 0.6));
  if (reduceMotion || step >= CHAT_BASE - CHAT_PRE) {
    n = CHAT_BASE - 1;
    p = 1;
  }
  // 메시지가 바뀔 때만 내용을 다시 넣고 높이를 잰다
  if (n !== chatN) {
    chatN = n;
    chatEls.forEach((el, k) => {
      const g = n - k;
      if (g < 0) return;
      const [who, text, opt] = chatAt(g);
      const cont = g > 0 && chatAt(g - 1)[0] === who;
      const last = k === 0 || chatAt(g + 1)[0] !== who;
      el.classList.toggle("mine", who === "나");
      el.classList.toggle("cont", cont);
      el.style.setProperty("--av", AV_COLOR[who] ?? "#9fb6cc");
      el.querySelector("small").textContent = who;
      // 사진 말풍선: 카톡처럼 사진만 (여러 장이면 2×2 묶음, 화질 깨진 사진은 흐리게)
      const bub = el.querySelector("p");
      bub.classList.toggle("pics", !!opt?.pics);
      bub.classList.toggle("many", (opt?.pics?.length ?? 0) > 1);
      bub.classList.toggle("lots", (opt?.pics?.length ?? 0) > 4);
      bub.classList.toggle("three", (opt?.pics?.length ?? 0) === 3);
      bub.classList.toggle("blur", !!opt?.blur);
      // 쏙 초대 링크 말풍선 (카톡 링크 미리보기 모양, 문구·그림은 실제 초대 링크 OG 와 같다)
      bub.classList.toggle("link", !!opt?.link);
      el.dataset.link = opt?.link ? "1" : "";
      if (opt?.pics)
        bub.innerHTML = opt.pics.map((f) => `<img src="${GN}/${f}.jpg" alt="" />`).join("");
      else if (opt?.link) bub.innerHTML = LINK_CARD;
      else bub.textContent = text;
      // 카톡의 안 읽음 숫자는 랜딩에선 뜻 없이 떠 있는 숫자로 보여 쓰지 않는다
      el.querySelector(".ur").textContent = "";
      el.dataset.last = last ? "1" : "";
    });
    chatH = chatEls.map((el, k) => {
      const g = n - k;
      const gap = g > 0 && chatAt(g - 1)[0] === chatAt(g)[0] ? 4 : M.mobile ? 7 : 10;
      return { h: el.offsetHeight, gap };
    });
  }
  const bodyH = $("chatBody").clientHeight;
  // 가장 최근 것은 아래 바깥에서 제자리로 올라오고, 나머지는 그 높이만큼 밀려 올라간다
  const push = (1 - p) * (chatH[0].h + chatH[0].gap);
  let acc = 10;
  chatEls.forEach((el, k) => {
    const g = n - k;
    const { h, gap } = chatH[k];
    const y = bodyH - acc - h + push;
    acc += h + gap;
    // 바뀌기 시작하면 말풍선은 조각(ghost)이 이어받고, 프로필·이름·시각은 흐려진다
    el.dataset.live = g >= 0 ? "1" : "";
    // 단톡방 말풍선은 모두 사진 조각이 이어받는다 (앞의 평범한 대화도, 요청도)
    // 말풍선을 사진 조각으로 바꾸는 연출은 쓰지 않는다: 링크 카드만 또렷이 남고 주변 말풍선이 반쯤 녹아
    // 깨진 화면처럼 보였다. 링크를 누르면 말풍선은 걷히고, 사진은 사진방에서 한 장씩 올라온다
    const isReq = false;
    el.dataset.req = isReq ? "1" : "";
    // 위 끝에 닿은 메시지는 반쯤 잘린 채 머리줄 밑에 남지 않게, 닿는 순간 짧게 흐려져 사라진다
    const edge = clamp((y + 8) / 14);
    const mo = (g < 0 ? 0 : k === 0 ? p : 1) * (el.dataset.link ? linkFade : msgFade) * edge;
    // 시각은 같은 사람 무리의 마지막 말풍선에만. 같은 사람의 다음 메시지가 아직 올라오지 않았으면 지금 말풍선이 마지막이다
    el.querySelector("time").style.display = el.dataset.last || (k === 1 && p < 0.5) ? "" : "none";
    el.style.opacity = mo;
    el.style.visibility = mo <= 0.001 ? "hidden" : "visible";
    el.style.transform = `translateY(${y}px)`;
  });
  // 바뀌기 시작하는 첫 순간, 말풍선 자리·모양을 한 번 재 둔다 (위 말풍선이 0번).
  // 조각마다 그 차례에 올라갈 갤러리 사진을 미리 넣어 둔다 (사진 k 는 N-k 번째로 올라온다)
  if (U > 0 && !ghostFrom) {
    const sr = stage.getBoundingClientRect();
    const br = $("chatBody").getBoundingClientRect();
    ghostFrom = chatEls
      .map((el) => {
        const b = el.querySelector("p");
        const r = b.getBoundingClientRect();
        const cs = getComputedStyle(b);
        return {
          el,
          x: r.left - sr.left,
          y: r.top - sr.top,
          w: r.width,
          h: r.height,
          r: parseFloat(cs.borderTopLeftRadius),
          pad: cs.padding,
          fs: cs.fontSize,
          // 사진 말풍선은 바탕이 투명하니 빈 칸 색에서 시작한다
          bg: b.classList.contains("pics")
            ? [235, 232, 227]
            : cs.backgroundColor.match(/\d+/g).slice(0, 3).map(Number),
          text: b.textContent,
          html: b.innerHTML,
          mine: el.classList.contains("mine"),
          // 사진 말풍선의 격자 모양 (many·lots·three)
          grid: ["many", "lots", "three", "blur"].filter((c) => b.classList.contains(c)),
          // 단톡방에 보이는 말풍선은 모두 사진이 된다
          // 위 끝에 걸려 흐려진 말풍선은 빼고, 온전히 보이는 것만 사진이 된다
          on: el.dataset.live === "1" && el.dataset.req === "1" && r.top > br.top - 4,
          top: r.top,
        };
      })
      .filter((g) => g.on)
      .sort((a, b) => a.top - b.top);
    const N = setP.length;
    ghostFrom = ghostFrom.slice(0, N);
    ghostFrom.forEach((F) => (F.el.dataset.ghost = "1"));
    const G = ghostFrom.length;
    // 앉을 칸: 위 말풍선부터 차례로 ('G 장 올라온 뒤' 배치에서 차 있는 칸들).
    // 같은 줄 두 칸에서는 말풍선이 있던 쪽 칸으로 간다 (내 말풍선=오른쪽 → 오른쪽 칸). 서로 길이 엇갈리지 않게
    const S_G = Math.floor((N - G) / 2);
    const off = N - G - 2 * S_G;
    // 말풍선은 모두 사진이 되어 폰으로 들어간다. 다만 폰 화면에 보이는 칸은 몇 줄뿐이라,
    // 보이는 칸(VIS)에 위에서부터 고르게 나눠 앉힌다 (넘치는 칸으로 가면 화면 아래로 빠져 잘린다).
    // 한 칸에 둘 이상 겹쳐 들어가면 모두 그 칸의 사진을 들고 있어 바뀌는 순간이 티 나지 않는다
    const VIS = [];
    for (let c = off; c < off + G; c++) {
      // 아래 끝이 조금(칸 높이의 1/4 까지) 잘리는 줄은 보이는 칸으로 친다
      if (M.cell(c).y + M.th * 0.75 > M.inner.h - M.gridTop) break;
      VIS.push(c);
    }
    if (!VIS.length) VIS.push(off);
    ghostFrom.forEach((F, i) => (F.cell = VIS[Math.floor((i * VIS.length) / G)]));
    for (let i = 0; i + 1 < G; i++) {
      const a = ghostFrom[i];
      const b = ghostFrom[i + 1];
      const sameRow = a.cell % 2 === 0 && b.cell === a.cell + 1;
      if (sameRow && a.mine && !b.mine) [a.cell, b.cell] = [b.cell, a.cell];
    }
    ghosts.forEach((el, i) => {
      const F = ghostFrom[i];
      el.innerHTML = "";
      if (!F) return;
      const t = document.createElement("div");
      t.className = ["gin", ...(F.grid ?? [])].join(" ");
      t.innerHTML = F.html;
      // 칸 c 에 앉는 사진 = 갤러리 사진 c + 2·S_G (그 칸에 그대로 남는 사진)
      el.append(t, setP[F.cell + 2 * S_G].querySelector(".pic").cloneNode(true));
    });
  }
  // 사진이 된 말풍선은 조각이 이어받으니 숨긴다
  chatEls.forEach((el) => {
    el.querySelector("p").style.visibility = U > 0 && ghostFrom && el.dataset.ghost ? "hidden" : "";
  });
}

// 머리글 '링크 만들기': 첫 화면 버튼이 사라지기 시작하면 바로 뜨고, 마지막 장면(버튼이 따로 있다)에선 숨는다
let headerOn = null;
function renderHeader(T) {
  // 첫 화면 '사진 모을 링크 만들기' 버튼이 사라지기 시작하면(헤드라인이 흐려지기 시작하면) 바로 뜬다
  const heroGone = T >= INTRO || +introEls[0].style.opacity < 0.95;
  const on = heroGone && T < PH.end.start + 0.25;
  if (on === headerOn) return;
  headerOn = on;
  $("siteHeader").classList.toggle("on", on);
  $("startBtn").tabIndex = on ? 0 : -1;
  if (!on) {
    $("headerMenu").hidden = true;
    $("startBtn").setAttribute("aria-expanded", "false");
  }
}

// 사람별 폴더를 만든 직후(h 0~1.2): 새 폴더 탭이 붙은 전체 탭에서 시작해
// '나' 탭을 누르면(0.32) 내 사진만 앞으로 모이고 나머지는 빠진다. 알림은 그동안 떠 있다가 끝 무렵 사라진다
function renderMeFolder(h) {
  const me = h >= 0.46;
  renderTabs(TABS_B, me ? 3 : 0, 86);
  const go = inOut(seg(h, 0.48, 0.74));
  setP.forEach((el, k) => {
    const mine = MINE.indexOf(GRID[k]);
    const a = M.cell(k);
    const b = M.cell(Math.max(mine, 0));
    if (mine >= 0) {
      el.style.transform = `translate(${lerp(a.x, b.x, go)}px, ${lerp(a.y, b.y, go)}px)`;
      el.style.opacity = 1;
      el.style.visibility = "visible";
    } else {
      const o = 1 - seg(h, 0.46, 0.58);
      el.style.transform = `translate(${a.x}px, ${a.y}px) scale(${1 - 0.08 * (1 - o)})`;
      el.style.opacity = o;
      el.style.visibility = o <= 0.001 ? "hidden" : "visible";
    }
  });
  $("folderToast").style.opacity = 1 - seg(h, 0.8, 0.95);
  $("fab").style.opacity = 0;
  renderTap(h, [[0.32, "meTab"]]);
}

function render(T) {
  if (!M) measure();
  const S = personScene(T);
  // 폰·갤러리 바탕 (장면마다 아래에서 덧그린다)
  phone.style.opacity = 1;
  phone.style.visibility = "visible";
  phone.style.transform = M.base;
  renderGallery(S);
  $("tap").style.opacity = 0;
  $("chatTap").style.opacity = 0;
  show($("cmp"), 0);
  folderEls.forEach((el) => show(el, 0));
  pushEls.forEach((el) => {
    el.style.opacity = 0;
    el.style.visibility = "hidden";
  });

  if (T < INTRO) renderIntro(T);
  else {
    // 첫 장면이 끝나면 스크롤 안내와 첫 장면 글, 단톡방은 다시 나오지 않는다
    show($("hint"), 0, 0, "translateX(-50%)");
    introEls.forEach((el) => show(el, 0));
    cams.forEach((el) => (el.style.visibility = "hidden"));
    introSkip = true;
    show($("chat"), 0);
    $("share").style.visibility = "hidden";
    ghosts.forEach((el) => {
      el.style.opacity = 0;
      el.style.visibility = "hidden";
    });
  }

  if (phH(T, "save") >= 0) renderSave(phH(T, "save"));
  // 사람별 장면: 링크 옆 AI 반짝임을 눌러 확인 시트를 열고(시트는 0.3 부터 올라온다), '얼굴 분석하고 정리하기'를 누른다
  if (phH(T, "person") >= 0)
    renderTap(S, [
      [0.22, "gAi"],
      [1.0, "kindGo"],
    ]);
  if (phH(T, "me") >= 0) {
    renderMeFolder(Math.max(0, phH(T, "me") - FOLDER_SHOW));
    renderFolders(phH(T, "me"));
  }
  if (phH(T, "end") >= 0) renderMeFolder(PH.me.len - FOLDER_SHOW);
  renderEnd(phH(T, "end"));
  renderTexts(T);

  // 옆 목차: 지금 장면에 불을 켠다
  tocItems.forEach((btn, i) => {
    const next = tocItems[i + 1] ? tocAt(tocItems[i + 1])[0] : Infinity;
    if (T >= tocAt(btn)[0] && T < next) btn.setAttribute("aria-current", "step");
    else btn.removeAttribute("aria-current");
    btn.classList.toggle("done", T >= next);
  });

  // 머리글은 첫 화면 헤드라인을 그린 뒤에 정한다 (헤드라인이 흐려지는 순간에 맞춰 뜨도록)
  renderHeader(T);
  // 옆 버튼 껍데기는 폰의 위치·크기·투명도를 그대로 따른다
  const shell = $("phoneShell");
  shell.style.transform = phone.style.transform;
  shell.style.opacity = phone.style.opacity;
  shell.style.visibility = phone.style.visibility;
}

// ── 옆 목차: [켜지는 스크롤 시각, 누르면 가는 스크롤 시각]. 장면 이름(data-ph)으로 정한다 ──
// 해결: 사진방에 사진이 다 모인 자리로 간다. 받기: 원본 저장부터 내 사진만 받기까지 한 칸이다
// 첫 장면 시계 U → 스크롤 시각 T (renderIntro 의 U = seg(T, 0.05, INTRO - 0.3) × 전체 길이 를 거꾸로)
const introFull = () => landAt(setP.length) + UP_DROP + 0.2;
const uToT = (u) => 0.05 + (u / introFull()) * (INTRO - 0.35);
// ①~③ 단계: [켜지는 시계, 누르면 가는 시계]
const TOC_U = {
  make: () => [APP_IN[0], MAKE_TYPE[1]], // 방 만들기 화면에 제목이 다 써진 자리
  send: () => [APP_OUT[0], SEND_AT[1] + 0.1], // 단톡방에 링크가 올라온 자리
  gather: () => [LINK_TAP + 0.1, UP_AT + 1.4], // 사진이 모이고 알림이 쌓인 자리
};
function tocAt(btn) {
  const ph = btn.dataset.ph;
  if (TOC_U[ph]) return TOC_U[ph]().map(uToT);
  const s = PH[ph];
  return [s.start, s.start + Math.min(1.6, s.len)];
}
const tocItems = [...document.querySelectorAll("#toc button")];
tocItems.forEach((btn) => {
  btn.addEventListener("click", () => {
    const go = tocAt(btn)[1] + chatLen();
    userScroll(1);
    if (reduceMotion) scrollTo({ top: rawToTop(go) });
    else playTo(go, 1);
  });
});

// ── 머리글 '링크 만들기' 메뉴: 시작 방법 목록을 복사해 쓴다 ──
$("headerMenu").append(...[...$("startMenu").content.children].map((n) => n.cloneNode(true)));
[[$("startBtn"), $("headerMenu")]].forEach(([btn, menu]) => {
  const setMenu = (open) => {
    menu.hidden = !open;
    btn.setAttribute("aria-expanded", String(open));
    if (open) menu.querySelector("a")?.focus();
  };
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    setMenu(menu.hidden);
  });
  document.addEventListener("click", (e) => {
    if (!menu.hidden && !menu.contains(e.target)) setMenu(false);
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !menu.hidden) {
      setMenu(false);
      btn.focus();
    }
  });
});

// ── 시간 흐름: 스크롤 위치만으로 정한다 ──
// 무대가 붙어 있는(sticky) 동안의 스크롤 길이 = 타임라인 길이. 무대가 화면보다 길 수 있어 무대 높이로 뺀다
function scrollT() {
  const travel = film.offsetHeight - stage.offsetHeight;
  if (travel <= 0) return 0;
  return clamp(-film.getBoundingClientRect().top / travel) * FILM_END;
}

let lastT = -1;
let smoothRaw = 0;
function frame() {
  followSvh();
  autoTick(performance.now());
  // 앞 단톡방 구간은 장면 시각을 0 에 묶어 두고, 그 안의 스크롤은 채팅에만 쓴다
  // 장면은 스크롤 위치를 바로 따르지 않고 부드럽게 쫓아간다. 휠 한 칸마다 장면이 덜컥 튀지 않게 한다
  const target = scrollT();
  smoothRaw =
    reduceMotion || play || auto.on || Math.abs(target - smoothRaw) < 0.002
      ? target
      : smoothRaw + (target - smoothRaw) * 0.09;
  const raw = smoothRaw;
  if (chatBase === null && raw > 0.01) chatBase = autoChatStep();
  const len = chatLen();
  chatS = Math.min(raw, len);
  chatRise = CHAT_RISE ? inOut(clamp(chatS / CHAT_RISE)) : 1;
  const msgS = Math.max(0, chatS - CHAT_RISE);
  const msgLen = len - CHAT_RISE;
  chatStep =
    chatBase === null
      ? autoChatStep()
      : chatBase + clamp(msgS / Math.max(msgLen - 0.25, 1e-6)) * (CHAT_STEPS - chatBase);
  const T = Math.max(0, raw - len);
  // 첫 화면 연출이 재생되는 동안은 스크롤이 없어도 매 프레임 그린다
  const playing = (!introSkip && introE() < INTRO_LEN + 0.2) || (T < INTRO && !reduceMotion);
  if (raw !== lastT || playing) {
    render(T);
    lastT = raw;
    document.documentElement.classList.remove("js-wait");
  }
  requestAnimationFrame(frame);
}

// ── 목차로 이동할 때만 스크롤 위치를 정해진 시간 동안 천천히 옮긴다 (평소엔 스크롤한 만큼만 움직인다) ──
const STEP_SPEED = 2.3; // 스크롤 시각 1 만큼 흐르는 데 걸리는 초
const rawToTop = (raw) =>
  film.offsetTop + ((film.offsetHeight - stage.offsetHeight) * raw) / FILM_END;
let play = null;
const easeSine = (t) => 0.5 - Math.cos(Math.PI * t) / 2;
// span: 재생 시간을 정하는 길이 (기본은 지금 자리부터 목표까지)
function playTo(to, maxSec = Infinity, span = null) {
  const from = scrollT();
  if (Math.abs(to - from) < 0.005) return;
  const dist = span ?? Math.abs(to - from);
  const dur = Math.min(maxSec, Math.max(0.6, dist * STEP_SPEED)) * 1000;
  play = { from, to, t0: performance.now(), dur };
  const tick = (now) => {
    if (!play) return;
    const k = clamp((now - play.t0) / play.dur);
    scrollTo({ top: rawToTop(lerp(play.from, play.to, easeSine(k))), behavior: "instant" });
    if (k < 1) requestAnimationFrame(tick);
    else play = null;
  };
  requestAnimationFrame(tick);
}

// ── 자동 재생: 단계(①~⑤·끝)마다 아주 느리게 저절로 흐르고, 그 단계 끝에서 멈춰 기다린다.
// 다음 단계는 사용자가 아래로 스크롤해야 시작한다: 아래로 스크롤하고 손을 떼면 곧바로(AUTO_RESUME) 그 자리부터
// 다음 단계 끝까지 다시 저절로 흐른다. 흐르는 중에 휠·터치·클릭·키를 쓰면 그 순간 멈춘다.
// 터치·클릭처럼 스크롤하지 않은 동작으로 멈췄거나 위로 스크롤해 되돌아갔다면, 다시 아래로 스크롤할 때까지 멈춰 있다.
// 첫 화면은 들어오자마자 저절로 시작해 ① 끝까지 흐른다
const AUTO_SPEED = 0.35; // 1초에 흐르는 스크롤 시각 (작을수록 느리다)
const AUTO_FAST = 1.6; // ④ 원본으로 받기·⑤ 내 사진만 받기 구간만 이만큼 더 빠르게 흐른다
const AUTO_RESUME = 350; // 아래로 스크롤하고 손을 뗀 뒤(관성이 멈춘 뒤) 다시 저절로 흐르기까지 (ms)
const AUTO_START = 2200; // 첫 화면에서 저절로 시작하기까지 (ms). 첫 화면 글·버튼이 다 나타난(약 1.3초) 뒤 잠깐 머문다
function autoStops() {
  if (chatBase === null) chatBase = autoChatStep();
  const len = chatLen();
  // 첫 단톡방 장면("잠시만!!")에서는 멈추지 않고 ① 끝까지 이어서 흐른다
  return [
    len + uToT(MAKE_TOAST[2]), // ① 방을 만들고 "공유 링크를 복사했어요."
    len + uToT(SEND_AT[1] + 0.15), // ② 단톡방에 링크를 보냄
    len + INTRO - 0.45, // ③ 사진을 골라 올리고 친구들 사진까지 다 모임
    len + PH.save.start + 2.0, // ④ 원본 비교 카드
    len + PH.me.start + PH.me.len - 0.1, // ⑤ 내 사진만
    FILM_END, // 끝
  ];
}
const auto = { on: false, raw: 0, to: 0, last: 0, userAt: 0, userDir: 1, started: false };
const t0 = performance.now();
function autoTick(now) {
  if (reduceMotion || play) {
    auto.on = false;
    return;
  }
  const dt = Math.min(0.05, (now - (auto.last || now)) / 1000);
  auto.last = now;
  if (!auto.on) {
    // 시작할 때: 첫 화면은 잠깐 뒤에, 그 뒤로는 손을 떼고 AUTO_RESUME 지난 뒤 (앞으로 가던 중일 때만)
    const firstStart = !auto.started && now - t0 > AUTO_START && auto.userAt === 0;
    const resume =
      auto.started && auto.userDir > 0 && auto.userAt && now - auto.userAt > AUTO_RESUME;
    if (!firstStart && !resume) return;
    const raw = scrollT();
    const next = autoStops().find((s) => s > raw + 0.02);
    // 단계 끝에 서 있으면(바로 앞 멈춤이 아주 가까우면) 기다린다
    const atStop = autoStops().some((s) => Math.abs(s - raw) <= 0.02);
    auto.started = true;
    auto.userAt = 0;
    if (next === undefined || (atStop && !firstStart)) return;
    auto.on = true;
    auto.raw = raw;
    auto.to = next;
  }
  // ③ 끝(멈춤 3번째)부터 ⑤ 끝(멈춤 5번째)까지, 즉 ④·⑤ 단계를 흐르는 동안만 빠르게
  const stops = autoStops();
  const fast = auto.raw >= stops[2] - 1e-4 && auto.raw < stops[4];
  auto.raw = Math.min(auto.to, auto.raw + AUTO_SPEED * (fast ? AUTO_FAST : 1) * dt);
  scrollTo({ top: rawToTop(auto.raw), behavior: "instant" });
  if (auto.raw >= auto.to - 1e-4) auto.on = false; // 단계 끝: 멈추고 기다린다
}
// 사용자가 직접 스크롤하거나 화면을 만지면 자동 재생을 멈춘다.
// dir: 1 아래로 스크롤 · -1 위로 스크롤 · 0 스크롤 없는 동작(터치 시작·클릭). 마지막 동작이 아래로 스크롤일 때만 다시 흐른다
function userScroll(dir) {
  auto.on = false;
  auto.started = true;
  auto.userAt = performance.now();
  auto.userDir = dir;
}
if (!reduceMotion) {
  addEventListener("wheel", (e) => userScroll(Math.sign(e.deltaY)), { passive: true });
  let touchY = null;
  addEventListener(
    "touchstart",
    (e) => {
      touchY = e.touches[0].clientY;
      userScroll(0);
    },
    { passive: true },
  );
  addEventListener(
    "touchmove",
    (e) => {
      if (touchY === null) return;
      const dir = Math.sign(touchY - e.touches[0].clientY);
      // 손가락이 거의 안 움직였으면(떨림) 스크롤로 치지 않는다 → 터치로 멈춘 상태가 유지된다
      if (dir && Math.abs(touchY - e.touches[0].clientY) > 6) userScroll(dir);
    },
    { passive: true },
  );
  // 손을 뗀 뒤 관성으로 흐르는 동안은 기다렸다가 다시 시작한다
  addEventListener(
    "scroll",
    () => {
      if (auto.userAt) auto.userAt = performance.now();
    },
    { passive: true },
  );
  addEventListener("keydown", (e) => {
    const dir = ["ArrowDown", "PageDown", " "].includes(e.key)
      ? 1
      : ["ArrowUp", "PageUp", "Home"].includes(e.key)
        ? -1
        : 0;
    if (dir) userScroll(dir);
  });
  // 마우스로 화면을 누르거나 스크롤 막대를 잡으면 멈춘다 (터치는 위 touchstart 가 맡는다)
  addEventListener("pointerdown", (e) => {
    if (e.pointerType !== "touch") userScroll(0);
  });
}

// 폰 폭: 장면은 지금 보이는 화면 높이(innerHeight)에 맞춰 짠다(--svh).
// 사파리처럼 스크롤하면 주소창이 접혀 화면이 길어지는 브라우저에서, 작은 화면 높이에 묶어 두면 아래가 크게 비고
// 폰 그림도 작아 보인다. 그래서 화면이 눈에 띄게(40px 넘게) 길어지면 그 높이로 부드럽게 늘이고, 그 뒤로는 줄이지 않는다
const svhProbe = Object.assign(document.createElement("div"), { ariaHidden: "true" });
svhProbe.style.cssText =
  "position:fixed;top:0;left:0;width:0;height:100svh;visibility:hidden;pointer-events:none";
document.body.append(svhProbe);
let lockW = 0;
let lockH = 0;
let scrollVhW = 0;
let svhTarget = 0;
let svhCur = 0;
// 주소창이 접히고 펴질 때 장면 높이를 몇 프레임에 걸쳐 따라가게 한다
function followSvh() {
  if (svhCur === svhTarget) return;
  const d = svhTarget - svhCur;
  svhCur = Math.abs(d) < 0.02 || reduceMotion ? svhTarget : svhCur + d * 0.12;
  document.documentElement.style.setProperty("--svh", `${svhCur}px`);
  M = null;
  tabsKey = "";
  lastT = -1;
}
function lockViewport() {
  const w = innerWidth;
  const mobile = w <= 760;
  const h = mobile ? innerHeight : Math.min(svhProbe.offsetHeight || innerHeight, innerHeight);
  // 폰 폭은 화면이 길어질 때만 따라간다 (주소창이 접힐 때). 다시 펴질 때 줄이면 스크롤 방향에 따라
  // 폰 크기가 달라 보여서, 한 번 길어진 뒤엔 그 크기를 유지한다 (폭이 바뀌면 처음부터 다시)
  const changed = w !== lockW || (mobile ? h > lockH + 40 : h !== lockH);
  if (!changed) return false;
  lockW = w;
  lockH = h;
  const root = document.documentElement.style;
  if (mobile) {
    root.setProperty("--stage-extra", "80px");
    svhTarget = h / 100;
    // 처음·회전 때는 바로 맞추고, 주소창이 접히고 펴질 때는 frame() 에서 부드럽게 늘이고 줄인다
    // (한 번에 바꾸면 단톡방·폰이 짧은 직사각형이었다가 갑자기 길어진다)
    if (w !== scrollVhW) {
      scrollVhW = w;
      svhCur = svhTarget;
      root.setProperty("--svh", `${svhCur}px`);
      // 스크롤 길이는 폭이 바뀔 때(처음·회전)만 다시 정한다
      root.setProperty("--scroll-vh", `${h / 100}px`);
    }
  } else {
    root.removeProperty("--svh");
    root.removeProperty("--stage-extra");
    root.removeProperty("--scroll-vh");
    scrollVhW = 0;
    svhCur = svhTarget = 0;
  }
  return true;
}
lockViewport();
addEventListener("resize", () => {
  if (innerWidth <= 760 && !lockViewport()) return;
  lockViewport();
  M = null;
  tabsKey = "";
  lastT = -1;
});
document.fonts?.ready.then(() => {
  M = null;
  tabsKey = "";
  lastT = -1;
});
requestAnimationFrame(frame);
