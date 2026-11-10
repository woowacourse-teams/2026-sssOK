// ─────────────────────────────────────────────────────────────
// 폰 하나를 두고, 스크롤 위치(T초)만으로 장면을 그린다.
//  0    ~ 6.6  사람별: 정리 방식 시트 → 진행 막대 → 사진이 폰 밖으로 쏟아져 사람별 세 줄
//               → 검토 화면 썸네일로 들어감 → 폴더 만들기 → 사람 폴더 탭
//  6.6  ~ 12.8 비슷한 사진: 진행 막대 → 사진이 폰 밖으로 나와 세 더미로 쌓임 → 검토 화면 썸네일로 들어감
//               → 3개 폴더 만들기 → '폴더 3개를 만들었어요' 알림 → 건배샷 폴더 탭 열기
//  12.8 ~ 20.8 검색 → 찾은 사진을 골라 받기 (업로드·좋아요 구간은 건너뜀). 이 구간은 L = T - SHIFT 시각으로 그린다
// ─────────────────────────────────────────────────────────────
const LOCAL = "/landing/assets";
const END = 24.5;
// 스크롤 시각(T) → 장면 시각. 앞에 '모으기' 장면(INTRO 초)을 붙이고, 검색 화면을 보여 준 뒤부터 고르기 직전까지(15.2~18.9)는 건너뛴다.
// 업로드·좋아요 장면은 보여 주지 않고, 검색으로 찾은 사진을 그대로 골라 받는다
// 첫 장면(잠금화면 알림 → 사진방 → 사진이 올라옴, 약 3.6초)이 스크롤로도 너무 빠르지 않게 보이는 길이
const INTRO = 2.8;
const SKIP = [17.3, 21.0];
// 장면 시각을 잠깐 멈춰 두고 그동안 따로 그리는 구간 [멈출 장면 시각, 길이]
//  0: 사람별 폴더를 만든 직후(6.2) 1.2초 — 전체 탭에서 '나' 탭을 눌러 내 사진만 남는다
//  1: 비슷한 사진 장면 앞(6.9) 0.9초 — '어떻게 정리할까요?' 시트에서 비슷한 사진 묶기를 누른다
// SKIP·END 는 멈춤을 넣기 전 시각, 그 밖의 장면 상수는 멈춤을 뺀 시각이다
const HOLDS = [
  [6.2, 1.2],
  [6.9, 0.9],
];
const SCROLL_END = END + INTRO - (SKIP[1] - SKIP[0]);
const unhold = (s) => {
  let off = 0;
  for (const [b, len] of HOLDS) {
    if (s < b + off) break;
    if (s < b + off + len) return b;
    off += len;
  }
  return s - off;
};
// 멈춤 구간 i 안이면 0~길이, 아니면 -1
const holdT = (s, i) => {
  const start = HOLDS[i][0] + HOLDS.slice(0, i).reduce((a, [, len]) => a + len, 0);
  return s >= start && s < start + HOLDS[i][1] ? s - start : -1;
};
const toRaw = (T) => {
  const o = T - INTRO;
  return o >= SKIP[0] ? o + (SKIP[1] - SKIP[0]) : o;
};
const toScene = (T) => unhold(toRaw(T));
const toScroll = (b) => {
  const o = b + HOLDS.filter(([at]) => b >= at).reduce((a, [, len]) => a + len, 0);
  return (o >= SKIP[1] ? o - (SKIP[1] - SKIP[0]) : o) + INTRO;
};
// 정리 장면이 길어진 만큼 검색 장면부터 뒤로 민다 (비슷한 사진 폴더 장면 1.6초 포함)
const SHIFT = 5.2;
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
const FILM_END = SCROLL_END + CHAT_LEN;
let chatS = 0;
// 단톡방은 처음엔 저절로 한 줄씩 올라오다가(CHAT_GAP 초마다), 스크롤을 시작하면 그때부터 스크롤로 넘긴다.
// 스크롤을 시작한 순간까지 올라온 만큼(chatBase)은 그대로 두고, 남은 메시지만 스크롤 구간에 나눠 싣는다.
// 남은 게 적을수록 단톡방 구간도 짧아진다 (다 나온 뒤면 0.25초만 머물고 다음 장면으로)
const CHAT_GAP = 0.6;
const CHAT_STEPS = 7; // 처음 깔린 대화 세 줄(CHAT_PRE) 뒤로 하나씩 올라올 메시지 수 (CHAT 은 모두 10개)
let chatBase = null;
let chatStep = 0;
const autoChatStep = () =>
  reduceMotion
    ? CHAT_STEPS
    : clamp(((performance.now() - introT0) / 1000 - 0.9) / CHAT_GAP, 0, CHAT_STEPS);
const chatLen = () =>
  chatBase === null ? CHAT_LEN : 0.25 + ((CHAT_LEN - 0.25) * (CHAT_STEPS - chatBase)) / CHAT_STEPS;
film.style.setProperty("--secs", FILM_END);

function show(el, o, y = 0, extra = "") {
  el.style.opacity = o;
  el.style.visibility = o <= 0.001 ? "hidden" : "visible";
  el.style.transform = `${extra} translateY(${y}px)`;
}

// ── 장면 글 ──
const TEXT = [
  {
    a: 0.3,
    c: 2.2,
    tag: "2 · 사람별로 나누기",
    // 질문 → 답으로 이어진다: "나 나온 사진, 언제 다 골라요? … 이젠 AI가 얼굴을 알아봐서 내 사진만 폴더에 쏙 모여요"
    kicker: "사진 수백 장,",
    title: "<em>내 사진</em>은<br />언제 다 골라요?",
  },
  {
    // 사람별 카드가 검토 화면으로 다 들어간 뒤(5.6) 뜬다
    a: 5.6,
    c: 6.35,
    // 앞 장면과 한 문장으로 이어지니 태그는 다시 달지 않는다
    ai: true,
    // '나' 탭을 눌러 내 사진만 남는 순간. 질문(언제 다 골라요?) → 방법(버튼 한 번) → 결과(전부 있어요)로 끝난다
    kicker: "내 폴더만 열면",
    title: "<em>내가 나온 사진</em>이<br />전부 있어요",
  },
  {
    a: 6.7,
    c: 7.75,
    tag: "3 · 비슷한 컷 묶기",
    // 2번 장면처럼 질문 → 방법(버튼 한 번이면 비슷한 컷끼리 쏙 묶여요) → 결과(그 순간 사진이 한곳에)로 이어진다.
    // 베스트 컷을 골라 주는 기능은 아니니 '잘 나온 한 장'처럼 고르기를 약속하는 말은 쓰지 않는다
    kicker: "같은 포즈로 열 장씩 찍으면",
    title: "<em>비슷한 사진</em>만<br />계속 넘기게 되죠?",
    ai: true,
  },
  {
    // 비슷한 사진 카드가 검토 화면으로 다 들어간 뒤(10.5) 뜬다
    a: 10.5,
    c: 12.4,
    // 앞 장면과 한 문장으로 이어지니 태그는 다시 달지 않는다
    ai: true,
    // 건배샷 폴더를 여는 순간과 맞춘다
    kicker: "폴더 하나만 열면",
    title: "<em>보고 싶은 사진</em>이<br />바로 나와요",
  },
  {
    a: 12.9,
    c: 20.55,
    tag: "4 · 검색해서 받기",
    // kicker 의 질문을 검색창이 그대로 받아 '우리가 먹은 메뉴'를 입력한다 (SP_QUERY 와 맞춘다).
    // 음성 검색처럼 들리지 않게 '말로' 대신 '떠오르는 대로'를 쓴다
    kicker: "“우리 뭐 먹었더라?”",
    title: "<em>떠오르는 대로</em> 검색하면<br />바로 찾아 저장해요",
    ai: true,
    // 50장 = 사진첩 저장 묶음 단위 (한 번에 많이 넘기면 폰이 멈춰서 나눠 저장한다)
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
// 비슷한 사진 세 묶음(건배샷·떡볶이·마라샹궈)은 실제로 연달아 찍은 4장씩 (파일 이름은 예전 dessert·pasta 를 그대로 쓴다)
const SIM_FILE = ["toast", "dessert", "pasta"];
const simSrc = (g, f) => `${GN}/similar/${SIM_FILE[g]}-${f + 1}.jpg`;

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
    `<span class="chk"></span><span class="upl">${who}</span><span class="hrt">${icon("heart")}<b>${heart}</b></span>`,
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
const UPLOADER = ["서연", "나", "민지"];
const setP = GRID.map((it, k) =>
  $("setP").appendChild(
    tile({
      pic: picEl(it.p, it.r, it.v),
      who: UPLOADER[(k + it.p) % 3],
      heart: (k * 3) % 7,
    }),
  ),
);
const cards = GRID.map((it) => {
  const el = document.createElement("div");
  el.className = "pc";
  el.appendChild(picEl(it.p, it.r, it.v));
  $("cards").appendChild(el);
  return { el, ...it };
});

// 비슷한 사진용: 묶음마다 연달아 찍은 4장 (similar/toast·dessert·pasta-1~4). [묶음, 컷]
const SIM_GROUPS = [1, 5, 3];
const SIM = [
  [0, 0],
  [1, 0],
  [2, 0],
  [0, 1],
  [2, 1],
  [1, 1],
  [1, 2],
  [0, 2],
  [2, 2],
  [0, 3],
  [2, 3],
  [1, 3],
];
const setS = SIM.map(([g, f], k) => {
  const el = $("setS").appendChild(
    tile({
      img: simSrc(g, f),
      who: UPLOADER[k % 3],
      heart: (k * 2) % 5,
    }),
  );
  return el;
});

// 폰 밖으로 나와 세 더미로 쌓이는 비슷한 사진 카드
const simCards = SIM.map(([g, f]) => {
  const el = document.createElement("div");
  el.className = "pc";
  const img = document.createElement("img");
  img.src = simSrc(g, f);
  img.alt = "";
  Object.assign(img.style, {
    width: "100%",
    height: "100%",
    objectFit: "cover",
  });
  el.appendChild(img);
  $("cards").appendChild(el);
  return { el, g, f };
});

// 검색·업로드·좋아요·다운로드용 [사진, 올린 사람, 처음 좋아요, 나중 좋아요, 검색에 맞음]
const PHOTOS = [
  [2, "서연", 1, 3, false],
  [1, "민지", 2, 9, true],
  [4, "나", 0, 2, false],
  [3, "서연", 1, 5, true],
  [6, "나", 0, 1, false],
  [5, "민지", 3, 7, true],
  [9, "나", 0, 2, false],
  [7, "서연", 1, 4, false],
];
// 새로 올라오는 사진은 밀려난 첫 두 줄(2·1·4·3번)과 겹치지 않는 사진으로 고른다
const NEW = [
  [7, "민지"],
  [9, "서연"],
  [6, "민지"],
  [8, "나"],
];
const setA = PHOTOS.map(([n, who, h]) =>
  $("setA").appendChild(tile({ img: photoSrc(n), who, heart: h })),
);
const setNew = NEW.map(([n, who]) => $("setA").appendChild(tile({ img: photoSrc(n), who })));
// 검색으로 모인 네 장(맨 앞 네 칸)을 고른다
const PICKED = PHOTOS.map((p, i) => (p[4] ? i : -1)).filter((i) => i >= 0);

// ── 검색 화면 (폰 밖, 크게) ──
// 유사도 막대는 보여 주기용 값이다. 숫자는 쓰지 않고 길이만 보여 준다
const SP_QUERY = "우리가 먹은 메뉴";
const SP_SIM = PHOTOS.map(([, , , , match], i) =>
  match ? 0.82 + ((i * 0.37) % 0.14) : 0.18 + ((i * 0.53) % 0.3),
);
// 정렬 뒤 자리: 맞는 사진 먼저, 그다음 유사도 높은 순
const SP_RANK = PHOTOS.map((_, i) => i).sort((a, b) => SP_SIM[b] - SP_SIM[a]);
const spItems = PHOTOS.map(([n, , , , match]) => {
  const el = document.createElement("div");
  el.className = "sp-it";
  el.innerHTML = `<img src="${photoSrc(n)}" alt="" /><div class="sim"><b></b></div>`;
  el.dataset.hit = match ? 1 : 0;
  return $("spGrid").appendChild(el);
});
// 장면 시각 L 기준: 7.7 폰 대신 검색 화면 → 7.9 입력 → 8.95 유사도 → 9.35 정렬 → (건너뜀) → 13.7 폰으로 돌아감
function renderSearch(L) {
  const o = life(L, 7.78, 8.0, 13.68, 13.8) * (L < 7.78 ? 0 : 1);
  show($("sp"), o, (1 - o) * 24, M.mobile ? "" : "translateY(-50%)");
  if (o <= 0.001) return;
  const typed = SP_QUERY.slice(0, Math.round(seg(L, 7.9, 8.4) * SP_QUERY.length));
  $("spQ").textContent = typed || "";
  const sort = inOut(seg(L, 9.35, 9.75));
  spItems.forEach((el, i) => {
    const from = i;
    const to = SP_RANK.indexOf(i);
    const col = lerp(from % 4, to % 4, sort);
    const row = lerp(Math.floor(from / 4), Math.floor(to / 4), sort);
    el.style.left = `calc(${col * 25}% + ${col * 3}px)`;
    el.style.top = `${row * 50}%`;
    const fill = ease(seg(L, 8.95 + i * 0.04, 9.3 + i * 0.04));
    el.querySelector("b").style.width = `${SP_SIM[i] * 100 * fill}%`;
    const hit = el.dataset.hit === "1";
    el.classList.toggle("hit", hit && sort > 0.5);
    el.style.opacity = hit ? 1 : 1 - 0.6 * sort;
  });
}

// 탭: 전체만 있을 때와 사람 폴더가 생긴 뒤
const TABS_A = [["전체", 86]];
const TABS_B = [
  ["전체", 86],
  ["민지", 34, "new"],
  ["서연", 28, "new"],
  ["나", 24, "new"],
];
// 비슷한 사진 폴더 셋이 생긴 뒤: 새 폴더가 '전체' 바로 옆에 붙는다
const TABS_C = [
  ["전체", 86],
  ["건배샷", 4, "new"],
  ["떡볶이", 4, "new"],
  ["마라샹궈", 4, "new"],
  ["민지", 34],
  ["서연", 28],
  ["나", 24],
];
let tabsKey = "";
let tabsLen = 0;
function renderTabs(list, active, total) {
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
  // 고른 탭이 '폴더 추가' 버튼 밑에 가리면, 앱처럼 탭 줄을 왼쪽으로 밀어 다 보이게 한다
  const tabs = [...$("gTabs").children];
  const add = tabs.pop();
  const on = tabs[active];
  const over = active > 0 ? on.offsetLeft + on.offsetWidth + 12 - add.offsetLeft : 0;
  tabs.forEach((t) => (t.style.transform = over > 0 ? `translateX(${-over}px)` : ""));
}

// 정리 방식 시트의 '비슷한 사진' 그림 (실제 시트와 같은 그림)
[
  ["left", -1],
  ["right", 1],
  ["center", 0],
].forEach(([side, off]) => {
  const s = document.createElement("span");
  s.className = "scene";
  s.style.transform = `translateX(${off * 1.3}em) rotate(${off * 8}deg)`;
  s.innerHTML = `<img src="/landing/assets/app/similar-card.svg" alt="" /><img src="/landing/assets/app/similar-scene-${side}.svg" alt="" />`;
  $("simThumb").appendChild(s);
});
$("simThumb").insertAdjacentHTML(
  "beforeend",
  `<span class="kcheck" style="z-index:2"><img src="/landing/assets/app/check.svg" alt="" /></span>`,
);

const rowLabs = PEOPLE.map((p, i) => {
  const el = document.createElement("div");
  el.className = "rowlab";
  el.innerHTML = `<span class="ava"></span>${p.name} <b>0장</b>`;
  el.querySelector(".ava").appendChild(picEl(i, 0, 1));
  return $("rowLabs").appendChild(el);
});

// 폰 폭 사람별 장면: 세로 칸마다 깔리는 카드. 세 사람으로 나뉜 게 한눈에 보이게 한다
const sortCols = PEOPLE.map(() => {
  const el = document.createElement("div");
  el.className = "scol";
  return $("sortCols").appendChild(el);
});

const simLabs = ["건배샷", "떡볶이", "마라샹궈"].map((n) => {
  const el = document.createElement("div");
  el.className = "rowlab";
  el.innerHTML = `${n} <b>4장</b>`;
  return $("rowLabs").appendChild(el);
});

// 검토 화면 카드: 썸네일 줄 + 이름 고르기
const chipsOf = (picked) =>
  ["나", "민지", "서연"]
    .map((c) => `<span class="chip ${c === picked ? "on" : ""}">${c}</span>`)
    .join("") + `<span class="chip type">+ 직접 입력</span>`;
function reviewCard(parent, thumbs, who, chips) {
  const card = document.createElement("div");
  // 이름(who)이 없으면 문구 줄 없이 칩만 크게 보인다 (비슷한 사진 묶음)
  card.className = who ? "rcard" : "rcard chip-only";
  card.innerHTML = `<div class="rthumbs"></div>${who ? `<div class="rwho">${who}</div>` : ""}<div class="chips">${chips}</div>`;
  const row = card.querySelector(".rthumbs");
  thumbs.forEach((t) => row.appendChild(t));
  row.insertAdjacentHTML("beforeend", `<svg class="ico chev"><use href="#i-chev" /></svg>`);
  parent.appendChild(card);
}
const thumb = (child, more) => {
  const t = document.createElement("div");
  t.className = "rthumb";
  t.appendChild(child);
  if (more) t.insertAdjacentHTML("beforeend", `<span class="more">+${more}</span>`);
  return t;
};
PEOPLE.forEach((p, i) =>
  reviewCard(
    $("rvPCards"),
    SHOTS.slice(0, 4).map(([r, v], j) => thumb(picEl(i, r, v), j === 3 ? p.total - 4 : 0)),
    p.name,
    chipsOf(p.name),
  ),
);
const SIM_NAMES = ["건배샷", "떡볶이", "마라샹궈"];
SIM_GROUPS.forEach((n, g) =>
  reviewCard(
    $("rvSCards"),
    [0, 1, 2, 3].map((f) => {
      const img = document.createElement("img");
      img.src = simSrc(g, f);
      img.alt = "";
      return thumb(img);
    }),
    "",
    `<span class="chip on">${SIM_NAMES[g]}</span>`,
  ),
);

// ── 첫 장면 '모으기': 글 두 개와 친구들 폰 세 대 (시각은 스크롤 시각 T) ──
const INTRO_TEXT = [
  {
    a: -1,
    c: 0.05,
    hero: true,
    title: "사진 보내달라는 말,<br />이제 <em>그만!</em>",
    cta: true,
  },
  {
    a: 0.95,
    c: INTRO - 0.05,
    tag: "1 · 링크로 모으기",
    // 첫 화면의 문제("사진 보내달라는 말")에 대한 답을 먼저 말하고, 이어서 실제 쏙 갤러리가 열린다
    title: "이제 사진은<br /><em>한곳에</em> 모아서!",
    desc: "링크 하나만 보내면<br />친구들이 찍은 사진이 다 모여요.",
    // 47% = 후처리 워커 2→4개일 때 사진 300장 준비 시간(288초→153초) 실측값
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
    // 무슨 서비스인지 한 문장으로 푼다
    (t.hero ? `<p class="hero-lead">다 같이 올리고, <b>내 사진만</b> 쏙 골라가요</p>` : "") +
    (t.cta
      ? `<div class="hero-ctas">` +
        `<a class="cta hero-cta" href="/rooms/create?utm_source=landing&amp;utm_medium=web&amp;utm_content=hero">사진 모을 링크 만들기</a>` +
        // 스토어가 둘이라 바로 이동하지 않고, 머리글처럼 iPhone·Android 를 고르는 메뉴를 연다
        `<div class="start hero-app"><button type="button" class="cta store hero-cta" id="heroAppBtn" aria-haspopup="true" aria-expanded="false" aria-controls="heroAppMenu">앱 설치하기</button>` +
        `<div class="start-menu" id="heroAppMenu" hidden></div></div>` +
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
//  스크롤 전: 단톡방에 "사진 보내줘" 메시지가 계속 올라온다 (페이지를 연 뒤 흐른 시간으로 그린다)
//  스크롤하면 (스크롤 시각 T): 단톡방이 빠지고 친구들 폰 세 대가 올라온 뒤,
//  각 폰의 사진이 쏙 아이콘으로 빨려 들어가고 → 아이콘이 쏙 폰으로 들어가 사진이 격자로 펼쳐진다
// 단톡방 6명: 나·민지·서연·지수·하린·예나. 민지·서연은 사진 속 얼굴(PEOPLE 순서)을 프로필로 쓴다
// [보낸 사람, 말]. 보낸 사람이 "나"면 오른쪽 노란 말풍선
// 마지막은 나도 어쩔 줄 몰라 하는 말로 끝난다 → 다음 장면에서 쏙이 해결한다
// 모임이 끝난 뒤 단톡방: 처음부터 깔린 평범한 대화(CHAT_PRE 줄) 아래로 "사진 보내줘" 요청 4개가 하나씩 올라온다.
// 스크롤하면 말풍선이 모두 사진으로 바뀌어 사진방에 들어간다 (세 번째 값 true = 요청 메시지 표시용)
// 문제가 글이 아니라 눈으로 보이게: 각자 사진을 낱장·묶음으로 흩어 보내고, 받은 사진은 화질이 깨져 있고,
// 찾는 사진(마라샹궈)은 아무도 안 보냈다. 세 번째 값 { pics, blur } = 사진 말풍선 (blur = 화질 깨진 사진)
const CHAT = [
  ["민지", "오늘 완전 재밌었다!! ㅋㅋ"],
  ["민지", "다들 사진 올려줘~"],
  [
    "서연",
    "",
    {
      // 9장 묶음 (카톡처럼 3칸 격자). 마라샹궈 사진은 일부러 없다 → "마라샹궈 사진 어디갔노"
      pics: [
        "group-1",
        "people/minji-2",
        "cheers",
        "people/seoyeon-3",
        "similar/dessert-2",
        "people/me-4",
        "group-2",
        "similar/toast-1",
        "people/minji-5",
      ],
    },
  ],
  ["서연", "일단 내 폰에 있는 거 여깄음!"],
  ["하린", "", { pics: ["people/me-2"], blur: true }],
  ["하린", "이거 사진 쫌 깨져있는데?? ㅠ"],
  // 지수는 찾는 마라샹궈 대신 엉뚱한 떡볶이 사진만 있다
  [
    "지수",
    "",
    {
      pics: ["similar/dessert-3", "similar/dessert-4", "similar/toast-2"],
    },
  ],
  ["지수", "아 마라샹궈 사진 어디갔노 스토리 올려야 하는데"],
  // 마지막은 내가 보낸 메시지 (노란 말풍선)
  ["나", "잠시 나 찍은 사진 왜케 많아 ㅠㅠ"],
  ["나", "어떻게 다 보내 ㅠㅠ"],
];
const CHAT_PRE = 3;
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
// 링크로 들어오는 세 사람 (나는 카드를 눌러 먼저 들어간다)
const joinEls = [
  ["나", "링크로 바로 입장"],
  ["민지", "님이 들어왔어요"],
  ["서연", "님이 들어왔어요"],
].map(([who, text]) => {
  const el = document.createElement("div");
  el.className = "join";
  el.style.setProperty("--av", AV_COLOR[who] ?? "#f7a77f");
  el.innerHTML = `<span class="av">${SILHOUETTE}</span><span><b>${who}</b> ${text}</span>`;
  return $("joins").appendChild(el);
});
const introT0 = performance.now();
// 첫 장면 '모으기': 스크롤하면 단톡방 말풍선이 그 자리에서 하나씩 사진으로 바뀌고, 단톡방 틀이 흰 폰 화면으로
// 바뀌는 동안 말풍선이던 사진들이 각자 가까운 갤러리 칸으로 한꺼번에 쏙 들어가 앉는다(위아래 순서 그대로라 엇갈리지 않는다).
// 폰 테두리가 둘러진 뒤엔 나머지 사진이 실시간 업로드처럼 한 장씩 맨 위에 올라오고, 먼저 온 사진은 아래로 밀린다.
// 시계(U, 초)로 흐르고, 스크롤하면 스크롤만큼 앞당겨진다(그때만 빨라진다)
const PH_AT = 0.05; // 첫 말풍선이 사진으로 바뀌기 시작하는 시각
const PH_STAG = 0.06; // 말풍선 사이 간격 (위 말풍선부터)
const PH_LEN = 0.45; // 말풍선 하나가 사진으로 바뀌는 시간
const MS = 0.6; // 단톡방 틀이 폰 화면으로 바뀌기 시작
const MORPH_END = MS + 0.7; // 단톡방 틀이 폰 화면 모양으로 다 바뀌는 시각
const SWAP = [MS + 0.6, MS + 0.85]; // 폰 테두리가 둘러지고 단톡방이 걷혀 갤러리가 드러나는 구간
const B_LAND = SWAP[1] + 0.05; // 말풍선이던 사진들이 갤러리 사진과 바뀌는 시각
const UP_GAP = 0.3; // 나머지 사진 사이 간격 (가만히 둘 땐 끝까지 같은 빠르기)
const UP_DROP = 0.4; // 나머지 사진이 맨 위에 내려앉는 시간
const UP_PUSH = 0.22; // 앞 사진들이 한 줄 내려가는 시간
// 말풍선이던 사진 수 (말풍선을 잰 뒤 정해진다)
const ghostCount = () => ghostFrom?.length ?? CHAT.length;
// n 번째(1부터) 사진이 갤러리에 앉는 시각: 앞 G 장(말풍선)은 함께, 그 뒤는 한 장씩
const landAt = (n) => {
  const G = ghostCount();
  return n <= G ? B_LAND : B_LAND + 0.35 + (n - G - 1) * UP_GAP;
};
const ghosts = Array.from({ length: CHAT.length }, () =>
  $("ghosts").appendChild(Object.assign(document.createElement("div"), { className: "ghost" })),
);
let upT0 = null;
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
  const H = stage.clientHeight;
  const mobile = W <= 760;
  const sr = stage.getBoundingClientRect();
  const base = mobile ? "translateX(-50%)" : "translate(-50%, -50%)";
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
  [...setP, ...setS, ...setA, ...setNew].forEach((el) =>
    Object.assign(el.style, { width: `${tw}px`, height: `${th}px` }),
  );

  // 카드의 네 자리: 폰 속 칸 → 흩어진 자리 → 사람별 줄 → 검토 화면 썸네일
  const bottom = inner.y + inner.h - th * 0.6;
  const inPhone = GRID.map((_, k) => {
    const c = cell(k);
    return {
      x: inner.x + c.x,
      y: Math.min(inner.y + gridTop + c.y, bottom),
      s: tw,
    };
  });
  let rows;
  let labs;
  let cols = null;
  if (mobile) {
    // 폰 폭: 가로 줄로 두면 사진이 작고 아래가 텅 비니, 사람마다 세로 한 줄(민지·서연·나 세 칸)로 세운다.
    // 이름표는 칸 위 가운데, 사진 5장은 그 아래로 쌓인다
    const top = Math.max(H * 0.2, 150);
    const labH = 40;
    // 칸 사이는 카드 여백(양쪽 pad)보다 넓게 둬서 카드끼리 맞닿지 않게 한다
    const gc = W * 0.06;
    const s = Math.min((W * 0.9 - gc * 2) / 3, (H - top - labH - 16) / 5.32);
    const g = s * 0.08;
    const left = (W - (s * 3 + gc * 2)) / 2;
    rows = PEOPLE.map((_, p) =>
      Array.from({ length: 5 }, (_, j) => ({
        x: left + p * (s + gc),
        y: top + labH + j * (s + g),
        s,
      })),
    );
    labs = PEOPLE.map((_, p) => ({
      x: left + p * (s + gc) + s / 2,
      y: top,
      mid: false,
      center: true,
    }));
    // 칸 카드: 이름표와 사진 5장을 감싼다
    const pad = s * 0.07;
    cols = PEOPLE.map((_, p) => ({
      x: left + p * (s + gc) - pad,
      y: top - pad,
      w: s + pad * 2,
      h: labH + s * 5 + g * 4 + pad * 2,
    }));
  } else {
    const gapR = H * 0.035;
    const s = Math.min((H * 0.62 - gapR * 2) / 3, (W * 0.6) / 5.3);
    const g = s * 0.08;
    const labW = Math.max(150, W * 0.13);
    const total = labW + s * 5 + g * 4;
    const left = (W - total) / 2 + labW;
    const top = H * 0.29;
    rows = PEOPLE.map((_, p) =>
      Array.from({ length: 5 }, (_, j) => ({
        x: left + j * (s + g),
        y: top + p * (s + gapR),
        s,
      })),
    );
    labs = PEOPLE.map((_, p) => ({
      x: left - labW,
      y: top + p * (s + gapR) + s / 2,
      mid: true,
    }));
  }

  const thumbs = [...$("rvPCards").querySelectorAll(".rcard")].map((card) =>
    [...card.querySelectorAll(".rthumb")].map((t) => {
      const r = spot(t);
      return { x: inner.x + r.x, y: inner.y + r.y, s: r.w };
    }),
  );

  const taps = {
    kindGo: spot($("kindGo")),
    fab: spot($("fab")),
    selDl: spot($("selDl")),
    rvPGo: spot($("rvPGo")),
    rvSGo: spot($("rvSGo")),
    kindS: spot($("simThumb")),
  };
  // 첫 장면: 쏙 폰 자리 둘레에 친구들 폰 세 대
  const pw = inner.w / 0.94;
  const cx = inner.x + inner.w / 2;
  const cy = inner.y + inner.h / 2;
  const cw = mobile ? Math.min(pw * 0.42, (W - 40) / 3.1) : pw * 0.46;
  const ch = cw / 0.462;
  // 모바일은 화면 밖으로 나가지 않게 가로 자리를 화면 안으로 묶는다
  const camPos = [
    { dx: -0.68, dy: -0.04, r: -9 },
    { dx: 0, dy: 0.06, r: 2 },
    { dx: 0.68, dy: -0.06, r: 8 },
  ].map(({ dx, dy, r }) => ({
    x: clamp(cx + dx * pw - cw / 2, 14, W - 14 - cw),
    // 폰 폭은 위 글이 길어 폴더와 겹치지 않게, 아래 빈 자리로 조금 내린다
    y: cy + dy * inner.h - ch / 2 + (mobile ? H * 0.055 : 0),
    w: cw,
    h: ch,
    r,
  }));
  cams.forEach((el, c) => {
    Object.assign(el.style, {
      width: `${cw}px`,
      height: `${ch}px`,
      borderWidth: `${cw * 0.04}px`,
      borderRadius: `${cw * 0.15}px`,
      fontSize: `${cw / 10}px`,
    });
  });
  // 비슷한 사진: 폰 속 칸 → 세 더미 → 검토 화면 썸네일
  const simFrom = SIM.map((_, i) => {
    const c = cell(i);
    return {
      x: inner.x + c.x,
      y: Math.min(inner.y + gridTop + c.y, bottom),
      s: tw,
    };
  });
  // 폰 폭: 가로 세 줄로 두면 더미가 작아 아래가 텅 비니, 세로로 한 줄씩 크게 쌓는다.
  // 높이 = 맨 위 더미가 부채꼴로 솟는 몫(0.15) + 더미·이름표 간격(1.48) 두 번 + 마지막 더미와 이름표(1.3).
  // 간격엔 아래 더미가 부채꼴로 솟는 몫까지 넣어, 위 이름표와 겹치지 않게 한다
  const pTopM = Math.max(H * 0.2, 150);
  const PM = Math.min(W * 0.42, (H - pTopM - 16) / 4.41);
  const P = mobile ? PM : Math.min(W * 0.16, H * 0.3);
  const pg = P * 0.45;
  const pLeft = (W - P * 3 - pg * 2) / 2;
  const pTop = H * 0.37;
  const piles = [0, 1, 2].map((g) =>
    mobile
      ? { x: (W - P) / 2, y: pTopM + P * 0.15 + g * P * 1.48, s: P }
      : { x: pLeft + g * (P + pg), y: pTop, s: P },
  );
  const simThumbs = [...$("rvSCards").querySelectorAll(".rcard")].map((card) =>
    [...card.querySelectorAll(".rthumb")].map((t) => {
      const r = spot(t);
      return { x: inner.x + r.x, y: inner.y + r.y, s: r.w };
    }),
  );
  M = {
    W,
    H,
    mobile,
    base,
    inner,
    em,
    tw,
    th,
    gridTop,
    cell,
    place,
    inPhone,
    rows,
    labs,
    cols,
    thumbs,
    taps,
    camPos,
    simFrom,
    piles,
    simThumbs,
  };
}

// 탭 표시: 회색 원이 눌렸다가 주황 고리가 퍼진다
const TAPS = [
  [1.0, "kindGo"],
  [5.75, "rvPGo"],
  [10.75, "rvSGo"],
  [11.5, "simTab"],
  [15.0, "fab"],
  [19.1, "tile0"],
  [19.25, "tile1"],
  [19.4, "tile2"],
  [19.8, "selDl"],
];
function tapPoint(key) {
  if (key.startsWith("tile")) {
    const c = M.cell(+key.slice(4));
    return { x: c.x + M.tw / 2, y: M.gridTop + c.y + M.th / 2 };
  }
  // 새로 생긴 '건배샷' 폴더 탭 (탭 줄은 그때그때 다시 그려지니 누를 때 잰다)
  if (key === "simTab" || key === "meTab") {
    const t = $("gTabs").children[key === "simTab" ? 1 : 3];
    if (!t) return { x: -99, y: -99 };
    const r = spot(t);
    return { x: r.cx, y: r.cy };
  }
  if (key === "sbAct") {
    const act = $("sb").querySelector(".act");
    if (!act) return { x: -99, y: -99 };
    const r = spot(act);
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

// ── 갤러리 화면 ──
function renderGallery(T) {
  const L = T - SHIFT;
  // 탭: 사람 폴더는 검토 화면에서 '폴더 만들기'를 누른 뒤(6.05)부터. 잠깐 '나' 폴더를 연다
  const total = within(L, 9.6, 11.6) ? 86 + NEW.filter((_, j) => L >= 10.1 + j * 0.2).length : 86;
  // 비슷한 사진 폴더는 '3개 폴더 만들기'를 누른 뒤(11.0)부터. 잠깐 '건배샷' 폴더를 연다
  if (T < 6.05) renderTabs(TABS_A, 0, total);
  else if (T < 11.0) renderTabs(TABS_B, within(T, 6.2, 6.6) ? 3 : 0, total);
  else renderTabs(TABS_C, within(T, 11.6, 12.6) ? 1 : 0, total);

  // 검색창: 검색 장면에서만 글자가 쳐진다
  const searching = within(L, 7.7, 15.6);
  const QUERY = "우리가 먹은 메뉴";
  const typed = QUERY.slice(0, Math.round(seg(L, 7.8, 8.4) * QUERY.length));
  $("gSearch").classList.toggle("on", searching);
  $("gQuery").innerHTML = searching
    ? `${typed}<i class="caret"></i>`
    : "어떤 사진을 찾거나 정리할까요?";
  $("gQuery").style.color = searching ? "var(--strong)" : "";

  $("gSort").innerHTML = `업로드 순 ${icon("down")}`;

  // 사진 묶음 셋 중 하나만 보인다
  $("setP").style.opacity = T < 6.6 ? 1 - seg(T, 6.5, 6.6) : 0;
  $("setS").style.opacity = within(T, 6.5, 12.8) ? seg(T, 6.5, 6.65) * (1 - seg(T, 12.7, 12.8)) : 0;
  $("setA").style.opacity = T >= 12.7 ? seg(T, 12.7, 12.85) : 0;

  // 사람별 사진: 쏟아진 뒤엔 비우고, '나' 폴더를 열면 내 사진만 보인다
  setP.forEach((el, k) => {
    const mine = MINE.indexOf(GRID[k]);
    if (T < 6.05) {
      M.place(el, k);
      el.style.visibility = T < 2.3 ? "visible" : "hidden";
      el.style.opacity = 1;
    } else if (T < 6.2) {
      // 폴더를 막 만든 때: 전체 탭 그대로 모든 사진
      M.place(el, k);
      el.style.visibility = "visible";
      el.style.opacity = seg(T, 6.05, 6.2);
    } else {
      M.place(el, Math.max(mine, 0));
      el.style.visibility = mine >= 0 ? "visible" : "hidden";
      el.style.opacity = 1;
    }
  });

  renderSimilar(T);
  renderPhotos(L);
  renderStatusBar(T);

  // 정리 방식 시트: 스크롤을 시작하면 바로 올라온다
  const kindUp = ease(seg(T, 0.3, 0.6)) - ease(seg(T, 1.15, 1.3));
  $("kindSheet").style.transform = `translateY(${(1 - kindUp) * 110}%)`;
  $("ovl").style.opacity = kindUp;
  $("ovl").style.visibility = kindUp <= 0.001 ? "hidden" : "visible";

  // 선택 막대 → 다운로드 → 저장 알림
  const selUp = ease(seg(L, 13.95, 14.15)) - ease(seg(L, 14.75, 14.95));
  $("selbar").style.transform = `translateY(${(1 - selUp) * 180}%)`;
  $("selN").textContent = PICKED.filter((_, i) => L >= 13.9 + i * 0.15 + 0.1).length;
  $("saveToast").style.opacity = life(L, 14.85, 15.0, 15.35, 15.5);
  // 폴더 만들기를 누르고 검토 화면이 빠지면 알림이 뜬다
  // 사람별 폴더(5.75 에 누름 → 6.1) 와 비슷한 사진 폴더(11.1) 둘 다 같은 알림을 쓴다
  $("folderToast").style.opacity = Math.max(
    // 사람별 폴더 알림은 멈춤 구간(renderMeFolder)에서 그린다
    life(T, 6.1, 6.2, 6.2, 6.201),
    life(T, 11.1, 11.25, 12.25, 12.4),
  );
  // 진행 막대·선택 막대·알림이 떠 있는 동안 올리기 버튼은 숨는다
  $("fab").style.opacity =
    within(T, 1.2, 2.35) ||
    within(T, 6.7, 8.0) ||
    within(T, 6.05, 6.65) ||
    within(T, 11.1, 12.4) ||
    within(L, 13.95, 15.5)
      ? 0
      : 1;
}

// 정리 진행 막대: 진행률이 차오르다가 "찾았어요 / 묶었어요 [확인하기]" 로 바뀐다
// 진행률이 차오르는 상태는 보여 주지 않고, 완료 막대만 아래에서 올라온다
const BARS = [
  { a: 1.4, out: 2.2, done: "3명을 찾았어요" },
  { a: 7.0, out: 7.85, done: "비슷한 사진을 3개로 묶었어요" },
];
let sbKey = "";
// 완료 막대("3명을 찾았어요 [확인하기]", "비슷한 사진을 3개로 묶었어요 [확인하기]")와 확인하기 탭은
// 보여 주지 않는다: 정리 방식을 고르면 바로 결과(사진이 쏟아짐)로 넘어간다
const SHOW_BARS = false;
function renderStatusBar(T) {
  const sb = $("sb");
  if (!SHOW_BARS) {
    sb.style.opacity = 0;
    sb.style.visibility = "hidden";
    return;
  }
  const bar = BARS.find((b) => T >= b.a - 0.1 && T < b.out + 0.2) ?? BARS[0];
  const o = life(T, bar.a, bar.a + 0.1, bar.out, bar.out + 0.1);
  sb.style.opacity = o;
  sb.style.visibility = o <= 0.001 ? "hidden" : "visible";
  sb.style.transform = `translateY(${(1 - ease(seg(T, bar.a, bar.a + 0.12))) * 24}px)`;
  const key = String(BARS.indexOf(bar));
  if (key === sbKey) return;
  sbKey = key;
  // 문구 칸 이름을 .msg 로 쓰면 단톡방 말풍선(.msg, 절대 위치) 스타일이 덮어써 줄이 틀어진다
  sb.innerHTML =
    `<svg class="ok" viewBox="0 0 22 22" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="#F66D3B" /><path d="M8 12.3l2.8 2.8L16 9.5" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>` +
    `<span class="sb-msg">${bar.done}</span><span class="act">확인하기</span>` +
    `<span class="x"><svg viewBox="0 0 18 18" aria-hidden="true"><path d="M4 4l10 10M14 4L4 14" fill="none" stroke="#8B8781" stroke-width="1.8" stroke-linecap="round" /></svg></span>`;
}

// 비슷한 사진: 묶는 동안엔 격자 그대로 있다가, 확인하기를 누르면 폰 밖으로 빠져나간다
// 폴더를 만든 뒤(11.0)엔 다시 격자로 돌아오고, '건배샷' 폴더를 열면(11.6) 건배샷 4장만 앞으로 모인다
function renderSimilar(T) {
  let k = 0;
  setS.forEach((el, i) => {
    if (T < 11.0) {
      M.place(el, i);
      el.style.visibility = T < 7.8 + i * 0.02 ? "visible" : "hidden";
      el.style.opacity = 1;
      return;
    }
    const inFolder = within(T, 11.6, 12.6);
    const mine = SIM[i][0] === 0;
    M.place(el, inFolder && mine ? k++ : i);
    el.style.visibility = !inFolder || mine ? "visible" : "hidden";
    el.style.opacity = inFolder
      ? seg(T, 11.6, 11.75)
      : T >= 12.6
        ? seg(T, 12.6, 12.75)
        : seg(T, 11.0, 11.15);
  });
}

// 검색·업로드·좋아요·다운로드 사진 움직임
function renderPhotos(T) {
  const { cell } = M;
  const gather = inOut(seg(T, 8.5, 8.9)) * (1 - inOut(seg(T, 15.55, 15.75)));
  const push = inOut(seg(T, 10.0, 10.9)) * (1 - inOut(seg(T, 11.3, 11.6)));
  let k = 0;
  setA.forEach((el, i) => {
    const [, , h0, , match] = PHOTOS[i];
    let c = cell(i);
    let o = 1;
    if (match) {
      const b = cell(k++);
      c = { x: lerp(c.x, b.x, gather), y: lerp(c.y, b.y, gather) };
    } else {
      o = 1 - gather;
    }
    const p = cell(i + 4);
    c = { x: lerp(c.x, p.x, push), y: lerp(c.y, p.y, push) };
    // 좋아요 수는 건너뛴 구간을 사이에 두고 튀지 않게 그대로 둔다
    el.querySelector(".hrt b").textContent = h0;
    const pk = PICKED.indexOf(i);
    const on = pk < 0 ? 0 : seg(T, 13.9 + pk * 0.15, 13.98 + pk * 0.15) * (1 - seg(T, 15.35, 15.5));
    const chk = el.querySelector(".chk");
    chk.style.setProperty("--on", on);
    chk.textContent = on > 0.5 ? "✓" : "";
    el.classList.toggle("sel", on > 0.5);
    el.style.transform = `translate(${c.x}px, ${c.y}px)`;
    el.style.opacity = o;
  });
  setNew.forEach((el, j) => {
    const a = ease(seg(T, 10.1 + j * 0.2, 10.35 + j * 0.2));
    const c = cell(j);
    el.style.opacity = within(T, 9.6, 11.6) ? a * (1 - seg(T, 11.3, 11.5)) : 0;
    el.style.transform = `translate(${c.x}px, ${c.y}px) scale(${0.85 + a * 0.15})`;
    el.style.transformOrigin = "50% 50%";
  });
}

// ── 사람별: 쏟아짐 → 세 줄 → 검토 화면 썸네일로 ──
function renderSort(T) {
  cards.forEach((c, k) => {
    const A = M.inPhone[k];
    const C = M.rows[c.p][c.j];
    const D = M.thumbs[c.p][Math.min(c.j, 3)];
    // 화면 가득 흩어지는 단계 없이, 폰 칸에서 바로 사람별 줄로 정돈된다
    const e2 = inOut(seg(T, 2.5 + (c.p * 5 + c.j) * 0.035, 3.3 + (c.p * 5 + c.j) * 0.035));
    const e3 = inOut(seg(T, 4.85 + c.p * 0.08 + c.j * 0.025, 5.45 + c.p * 0.08 + c.j * 0.025));
    const x = lerp(lerp(A.x, C.x, e2), D.x, e3);
    let y = lerp(lerp(A.y, C.y, e2), D.y, e3);
    const s = lerp(lerp(A.s, C.s, e2), D.s, e3);
    // 처음 보는 사람이 눈으로 따라갈 수 있게 포물선·기울기를 작게 둔다
    y -= Math.sin(e2 * Math.PI) * M.H * 0.03;
    const r = Math.sin(e3 * Math.PI) * (c.j % 2 ? -3 : 3);
    const o = T < 2.3 ? 0 : 1 - seg(T, 5.6, 5.7);
    c.el.style.opacity = o;
    c.el.style.visibility = o <= 0.001 ? "hidden" : "visible";
    c.el.style.transform = `translate(${x}px, ${y}px) rotate(${r}deg) scale(${s / 100})`;
  });

  // 폰 폭은 숫자가 0부터 오르다 멈춘 어중간한 값('2장')이 보이지 않게 처음부터 다 센 수로 띄운다
  const cnt = M.cols ? 1 : ease(seg(T, 3.85, 4.55));
  const colOn = M.cols ? life(T, 3.3, 3.6, 4.6, 4.85) : 0;
  sortCols.forEach((el, p) => {
    const C = M.cols?.[p];
    el.style.visibility = C && colOn > 0.001 ? "visible" : "hidden";
    if (!C) return;
    el.style.left = `${C.x}px`;
    el.style.top = `${C.y}px`;
    el.style.width = `${C.w}px`;
    el.style.height = `${C.h}px`;
    el.style.opacity = colOn;
  });
  rowLabs.forEach((el, p) => {
    const L = M.labs[p];
    el.style.left = `${L.x}px`;
    el.style.top = `${L.y}px`;
    el.style.opacity = M.cols ? colOn : life(T, 3.75 + p * 0.08, 4.05 + p * 0.08, 4.6, 4.85);
    el.style.transform = L.mid ? "translateY(-50%)" : L.center ? "translateX(-50%)" : "none";
    el.querySelector("b").textContent = `${Math.round(PEOPLE[p].total * cnt)}장`;
  });
  show($("sortHead"), life(T, 3.4, 3.75, 4.6, 4.85), (1 - ease(seg(T, 3.4, 3.75))) * 20);
}

// ── 비슷한 사진: 폰 칸에서 바로 세 더미로 모임 → 검토 화면 썸네일로 ──
function renderSimBurst(T) {
  simCards.forEach(({ el, g, f }, i) => {
    const A = M.simFrom[i];
    const P = M.piles[g];
    // 더미: 맨 위 컷은 반듯하고, 뒤 컷은 오른쪽 위로 비껴 부채꼴로 깔린다
    const C = {
      x: P.x + f * P.s * 0.07,
      y: P.y - f * P.s * 0.05,
      s: P.s,
    };
    const D = M.simThumbs[g][f];
    const e2 = inOut(seg(T, 7.85 + g * 0.08 + f * 0.04, 8.65 + g * 0.08 + f * 0.04));
    const e3 = inOut(seg(T, 9.85 + g * 0.08 + f * 0.025, 10.45 + g * 0.08 + f * 0.025));
    const x = lerp(lerp(A.x, C.x, e2), D.x, e3);
    let y = lerp(lerp(A.y, C.y, e2), D.y, e3);
    const s = lerp(lerp(A.s, C.s, e2), D.s, e3);
    y -= Math.sin(e2 * Math.PI) * M.H * 0.03;
    const r = f * 2 * e2 * (1 - e3);
    const o = T < 7.8 + i * 0.02 ? 0 : 1 - seg(T, 10.55, 10.65);
    el.style.opacity = o;
    el.style.visibility = o <= 0.001 ? "hidden" : "visible";
    el.style.zIndex = 10 - f;
    el.style.transform = `translate(${x}px, ${y}px) rotate(${r}deg) scale(${s / 100})`;
  });
  simLabs.forEach((el, g) => {
    const P = M.piles[g];
    el.style.left = `${P.x + P.s / 2}px`;
    el.style.top = `${P.y + P.s * 1.12}px`;
    el.style.transform = "translateX(-50%)";
    el.style.opacity = life(T, 8.55 + g * 0.08, 8.85 + g * 0.08, 9.6, 9.8);
  });
  show($("simHead"), life(T, 8.4, 8.75, 9.6, 9.85), (1 - ease(seg(T, 8.4, 8.75))) * 20);
}

// ── 전체 그리기 ──
function renderScene(T) {
  if (!M) measure();

  TEXT.forEach((t, i) => {
    const o = life(T, t.a, t.a + 0.3, t.c, t.c + 0.3);
    show(
      textEls[i],
      o,
      (1 - ease(seg(T, t.a, t.a + 0.3))) * 20,
      M.mobile ? "" : "translateY(-50%)",
    );
  });

  const L = T - SHIFT;
  // 폰: 사진이 쏟아질 때 물러났다가, 사람별 줄 제목·이름이 빠진 뒤(4.85) 검토 화면과 함께 돌아온다. 마지막에 위로 빠진다
  const away = Math.max(
    seg(T, 2.3, 2.85) * (1 - seg(T, 4.85, 5.1)),
    seg(T, 7.8, 8.3) * (1 - seg(T, 9.7, 10.05)),
    seg(L, 7.6, 7.75) * (1 - seg(L, 13.82, 13.97)),
  );
  const exit = inOut(seg(L, 15.6, 16.1));
  phone.style.opacity = (1 - away) * (1 - exit);
  phone.style.visibility = +phone.style.opacity <= 0.001 ? "hidden" : "visible";
  phone.style.transform = `${M.base} translateY(${-exit * M.H * 0.12}px) scale(${1 - away * 0.1 - exit * 0.1})`;

  // 검토 화면 두 개. 사람별 썸네일은 날아온 카드가 닿은 뒤 채워진다
  [
    ["rvP", life(T, 4.85, 5.05, 5.9, 6.1)],
    ["rvS", life(T, 9.7, 9.9, 10.95, 11.15)],
  ].forEach(([id, o]) => {
    // 겹쳐 비치지 않게 오른쪽에서 밀고 들어왔다가 오른쪽으로 빠진다
    const k = o;
    $(id).style.opacity = o > 0.001 ? 1 : 0;
    $(id).style.transform = `translateX(${(1 - k) * 100}%)`;
    $(id).style.boxShadow = k < 1 ? "-1em 0 2em rgba(26,22,18,.12)" : "";
    $(id).style.visibility = o <= 0.001 ? "hidden" : "visible";
  });
  $("rvPCards")
    .querySelectorAll(".rthumb")
    .forEach((t) => (t.style.opacity = seg(T, 5.5, 5.6)));
  $("rvSCards")
    .querySelectorAll(".rthumb")
    .forEach((t) => (t.style.opacity = seg(T, 10.45, 10.55)));

  renderSort(T);
  renderSimBurst(T);
  renderGallery(T);
  renderSearch(L);
  renderTap(T);

  // 마지막
  show($("end"), L >= 15.9 ? 1 : 0, 0, "translateY(-50%)");
  const pop = (el, a, b, from = 0.7) => {
    const p = ease(seg(L, a, b));
    el.style.opacity = p;
    el.style.transform = `translateY(${(1 - p) * 16}px) scale(${from + p * (1 - from)})`;
  };
  pop($("endLogo"), 15.9, 16.2, 0.6);
  pop($("endKicker"), 16.1, 16.4, 1);
  pop($("endTitle"), 16.25, 16.55, 1);
  pop($("endDesc"), 16.4, 16.7, 1);
  pop($("endCta"), 16.55, 16.85, 0.5);
  pop($("endApps"), 16.7, 17.0, 1);
  ["endCta", "endApps"].forEach((id) => {
    $(id).style.pointerEvents = L > 16.8 ? "auto" : "none";
    $(id).tabIndex = L > 16.8 ? 0 : -1;
  });

  tocItems.forEach((btn, i) => {
    const from = +btn.dataset.from;
    const next = tocItems[i + 1] ? +tocItems[i + 1].dataset.from : Infinity;
    if (T >= from && T < next) btn.setAttribute("aria-current", "step");
    else btn.removeAttribute("aria-current");
    btn.classList.toggle("done", T >= next);
  });
}

// ── 첫 장면 '모으기': 찰칵 → 사진이 흩어짐 → 쏙 폰으로 모여 갤러리에 쌓임 (T 는 스크롤 시각) ──
// 첫 장면 상태: 시계 U, 말풍선 조각이 출발한 자리(처음 한 번 잰다)
let introU = 0;
let ghostFrom = null;
function renderIntro(T) {
  const N = setP.length;
  const UP_END = landAt(N) + UP_DROP;
  const UP_FULL = UP_END + 0.2;
  // 시계: 스크롤을 시작하면(T 0.05) 저절로 흐른다. 맨 위로 되돌리면(T < 0.03) 처음부터.
  // 스크롤로 넘기는 사람에게도(T 0.5~INTRO-0.3) 연출이 스크롤만큼 이어지게 한다 (저절로 흐른 쪽이 앞서면 그쪽을 따른다)
  // 스크롤을 시작해야(T 0.05) 시작한다 (가만히 있으면 첫 화면 그대로). 시작한 뒤엔 멈춰도 저절로 이어지고,
  // 맨 위로 되돌리면(T < 0.03) 첫 화면(단톡방)으로 돌아간다
  if (T < 0.03) {
    upT0 = null;
    ghostFrom = null;
  } else if (upT0 === null && T >= 0.05) upT0 = performance.now();
  const U = reduceMotion
    ? UP_FULL
    : Math.max(
        upT0 === null ? 0 : (performance.now() - upT0) / 1000,
        seg(T, 0.5, INTRO - 0.3) * UP_FULL,
      );
  introU = U;

  INTRO_TEXT.forEach((t, i) => {
    // '1 · 링크로 모으기' 글은 단톡방이 사진방으로 바뀌기 시작할 때(MS) 먼저 뜨고, 그다음 갤러리가 드러난다
    const inA = i === 1 ? ease(seg(U, MS, MS + 0.35)) : 0;
    // 첫 화면 헤드라인은 스크롤을 조금만 해도(시계가 흐르면) 빠진다
    const o =
      i === 1
        ? Math.min(inA, 1 - seg(T, t.c, t.c + 0.3))
        : Math.min(life(T, t.a, t.a + 0.3, t.c, t.c + 0.3), 1 - seg(U, 0.05, 0.4));
    show(
      introEls[i],
      o,
      (1 - (i === 1 ? inA : ease(seg(T, t.a, t.a + 0.3)))) * 20,
      M.mobile ? "" : "translateY(-50%)",
    );
  });
  // 첫 화면 연출 도중에 스크롤하면 바로 마지막 상태로 넘긴다
  if (T > 0.02) introSkip = true;
  const E = introE();
  show(
    $("hint"),
    Math.min(ease(seg(E, 1.0, 1.3)), 1 - seg(chatS + T, 0.05, 0.3)),
    0,
    "translateX(-50%)",
  );

  renderChat(E, T, chatStep);

  // 예전 '친구들 사진첩 세 대 → 쏙 아이콘', 초대 카드, 입장 말풍선 연출은 쓰지 않는다
  cams.forEach((el) => (el.style.visibility = "hidden"));
  $("share").style.visibility = "hidden";
  show($("invite"), 0);
  joinEls.forEach((el) => show(el, 0));

  // 쏙 폰: 바뀐 단톡방(이제 갤러리 화면) 둘레에 테두리가 둘러진다. 화면은 그 위의 단톡방이 걷히며 드러난다
  const appear = inOut(seg(U, SWAP[0], SWAP[1]));
  phone.style.opacity = appear;
  phone.style.visibility = appear <= 0.001 ? "hidden" : "visible";
  phone.style.transform = `${M.base} scale(${1.03 - appear * 0.03})`;

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
    show(chat, o, (1 - ease(seg(E, 0.15, 0.6))) * 30, M.mobile ? "" : "translate(-50%, -50%)");
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
    const A = M.chatRest;
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
      if (opt?.pics)
        bub.innerHTML = opt.pics.map((f) => `<img src="${GN}/${f}.jpg" alt="" />`).join("");
      else bub.textContent = text;
      el.querySelector(".ur").textContent = k < 5 ? 5 - k : "";
      el.querySelector("time").style.display = last ? "" : "none";
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
    const isReq = g >= 0;
    el.dataset.req = isReq ? "1" : "";
    const bub = el.querySelector("p");
    bub.style.visibility = U > 0 && isReq ? "hidden" : "";
    // 위 끝에 닿은 메시지는 반쯤 잘린 채 머리줄 밑에 남지 않게, 닿는 순간 짧게 흐려져 사라진다
    const edge = clamp((y + 8) / 14);
    const mo = (g < 0 ? 0 : k === 0 ? p : 1) * msgFade * edge;
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
      .sort((a, b) => a.top - b.top)
      .slice(0, setP.length);
    const N = setP.length;
    const G = ghostFrom.length;
    // 앉을 칸: 위 말풍선부터 차례로 ('G 장 올라온 뒤' 배치에서 차 있는 칸들).
    // 같은 줄 두 칸에서는 말풍선이 있던 쪽 칸으로 간다 (내 말풍선=오른쪽 → 오른쪽 칸). 서로 길이 엇갈리지 않게
    const S_G = Math.floor((N - G) / 2);
    const off = N - G - 2 * S_G;
    ghostFrom.forEach((F, i) => (F.cell = i + off));
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
}

// 머리글 '링크 만들기': 첫 화면 버튼이 사라지기 시작하면 바로 뜨고, 마지막 장면(버튼이 따로 있다)에선 숨는다
let headerOn = null;
function renderHeader(T) {
  // 첫 화면 '사진 모을 링크 만들기' 버튼이 사라지기 시작하면(헤드라인이 흐려지기 시작하면) 바로 뜬다
  const heroGone = T >= INTRO || +introEls[0].style.opacity < 0.95;
  const on = heroGone && toScene(T) - SHIFT < 15.85;
  if (on === headerOn) return;
  headerOn = on;
  $("siteHeader").classList.toggle("on", on);
  $("startBtn").tabIndex = on ? 0 : -1;
  if (!on) {
    $("headerMenu").hidden = true;
    $("startBtn").setAttribute("aria-expanded", "false");
  }
}

// 정리 방식 시트: 사람별 장면에선 '사람별로'가 골라진 채, 비슷한 사진 앞 멈춤 구간(h 0~0.9)에선
// 시트가 '비슷한 사진'이 골라진 채로 올라와 → '비슷한 사진 묶기'를 누르면 내려간다 (문구는 실제 앱 시트와 같다)
const SHEET_TAPS = [[0.36, "kindGo"]];
let sheetSim = null;
function renderKindSheet(h) {
  const sim = h >= 0;
  if (sim !== sheetSim) {
    sheetSim = sim;
    $("kindP").classList.toggle("on", !sim);
    $("kindS").classList.toggle("on", sim);
    $("kindGo").textContent = sim ? "비슷한 사진 묶기" : "얼굴 분석해서 정리하기";
  }
  if (h < 0) return;
  const up = ease(seg(h, 0, 0.2)) - ease(seg(h, 0.62, 0.76));
  $("kindSheet").style.transform = `translateY(${(1 - up) * 110}%)`;
  $("ovl").style.opacity = up;
  $("ovl").style.visibility = up <= 0.001 ? "hidden" : "visible";
  $("fab").style.opacity = 0;
  renderTap(h, SHEET_TAPS);
}

// 사람별 폴더를 만든 직후(멈춤 구간 0, h 0~1.2): 새 폴더 탭이 붙은 전체 탭에서 시작해
// '나' 탭을 누르면(0.5) 내 사진만 앞으로 모이고 나머지는 빠진다. 알림은 그동안 떠 있다가 끝 무렵 사라진다
function renderMeFolder(h) {
  if (h < 0) return;
  const me = h >= 0.58;
  renderTabs(TABS_B, me ? 3 : 0, 86);
  const go = inOut(seg(h, 0.6, 0.9));
  setP.forEach((el, k) => {
    const mine = MINE.indexOf(GRID[k]);
    const a = M.cell(k);
    const b = M.cell(Math.max(mine, 0));
    if (mine >= 0) {
      el.style.transform = `translate(${lerp(a.x, b.x, go)}px, ${lerp(a.y, b.y, go)}px)`;
      el.style.opacity = 1;
      el.style.visibility = "visible";
    } else {
      const o = 1 - seg(h, 0.58, 0.72);
      el.style.transform = `translate(${a.x}px, ${a.y}px) scale(${1 - 0.08 * (1 - o)})`;
      el.style.opacity = o;
      el.style.visibility = o <= 0.001 ? "hidden" : "visible";
    }
  });
  $("folderToast").style.opacity = 1 - seg(h, 0.95, 1.1);
  $("fab").style.opacity = 0;
  renderTap(h, [[0.42, "meTab"]]);
}

function render(T) {
  if (!M) measure();
  renderScene(toScene(T));
  renderMeFolder(T >= INTRO ? holdT(toRaw(T), 0) : -1);
  renderKindSheet(T >= INTRO ? holdT(toRaw(T), 1) : -1);
  if (T < INTRO) renderIntro(T);
  else {
    // 첫 장면이 끝나면 스크롤 안내와 장면 글, 친구들 폰은 다시 나오지 않는다
    show($("hint"), 0, 0, "translateX(-50%)");
    introEls.forEach((el) => show(el, 0));
    cams.forEach((el) => (el.style.visibility = "hidden"));
    introSkip = true;
    show($("chat"), 0);
    $("share").style.visibility = "hidden";
    show($("invite"), 0);
    joinEls.forEach((el) => show(el, 0));
    ghosts.forEach((el) => {
      el.style.opacity = 0;
      el.style.visibility = "hidden";
    });
  }
  // 머리글은 첫 화면 헤드라인을 그린 뒤에 정한다 (헤드라인이 흐려지는 순간에 맞춰 뜨도록)
  renderHeader(T);
  // 옆 버튼 껍데기는 폰의 위치·크기·투명도를 그대로 따른다
  const shell = $("phoneShell");
  shell.style.transform = phone.style.transform;
  shell.style.opacity = phone.style.opacity;
  shell.style.visibility = phone.style.visibility;
}

// ── 옆 목차: 누르면 그 장면 시각의 스크롤 위치로 간다 ──
const tocItems = [...document.querySelectorAll("#toc button")];
tocItems.forEach((btn) => {
  btn.addEventListener("click", () => {
    const travel = film.offsetHeight - innerHeight;
    const go = Math.max(0, toScroll(+btn.dataset.go));
    scrollTo({
      top: film.offsetTop + (travel * (go + chatLen())) / FILM_END,
      behavior: reduceMotion ? "auto" : "smooth",
    });
  });
});

// ── 첫 화면 '쏙 앱 알아보기' 메뉴: 시작 방법 목록의 앱 항목(iPhone·Android)을 복사해 쓴다 ──
$("heroAppMenu").append(
  ...[...$("startMenu").content.children].slice(1).map((n) => n.cloneNode(true)),
);
$("headerMenu").append(...[...$("startMenu").content.children].map((n) => n.cloneNode(true)));
[
  [$("startBtn"), $("headerMenu")],
  [$("heroAppBtn"), $("heroAppMenu")],
].forEach(([btn, menu]) => {
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
function scrollT() {
  const travel = film.offsetHeight - innerHeight;
  if (travel <= 0) return 0;
  return clamp(-film.getBoundingClientRect().top / travel) * FILM_END;
}

let lastT = -1;
function frame() {
  // 앞 단톡방 구간은 장면 시각을 0 에 묶어 두고, 그 안의 스크롤은 채팅에만 쓴다
  const raw = scrollT();
  if (chatBase === null && raw > 0.01) chatBase = autoChatStep();
  const len = chatLen();
  chatS = Math.min(raw, len);
  chatStep =
    chatBase === null
      ? autoChatStep()
      : chatBase + clamp(chatS / Math.max(len - 0.25, 1e-6)) * (CHAT_STEPS - chatBase);
  const T = Math.max(0, raw - len);
  // 첫 화면 연출이 재생되는 동안은 스크롤이 없어도 매 프레임 그린다
  const playing = (!introSkip && introE() < INTRO_LEN + 0.2) || (T < INTRO && !reduceMotion);
  if (raw !== lastT || playing) {
    render(T);
    lastT = raw;
  }
  requestAnimationFrame(frame);
}

addEventListener("resize", () => {
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
