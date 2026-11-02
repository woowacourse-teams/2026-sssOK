// hero headline stagger
document.querySelectorAll(".hero h1 .w").forEach((w, i) => {
  w.animate(
    [
      { opacity: 0, transform: "translateY(26px)" },
      { opacity: 1, transform: "none" },
    ],
    {
      duration: 620,
      delay: 120 * i,
      easing: "cubic-bezier(.2,.7,.3,1)",
      fill: "forwards",
    },
  );
});

// scroll reveal — .rv(페이드+슬라이드) / .rv-fade(페이드만, 자체 transform 보존용) / .rv-pop(톡 튀는 등장)
const io = new IntersectionObserver(
  (es) => {
    es.forEach((e, i) => {
      if (e.isIntersecting) {
        setTimeout(() => e.target.classList.add("in"), i * 90);
        io.unobserve(e.target);
      }
    });
  },
  { threshold: 0.15, rootMargin: "0px 0px -60px 0px" },
);
document.querySelectorAll(".rv, .rv-fade, .rv-pop").forEach((el) => {
  // .rv-hero(히어로 안 요소)는 아래 별도 처리 — 뷰포트가 낮으면 위 rootMargin
  // 컷오프 안쪽이라 IO가 "교차"로 안 잡아줘서 스크롤 안 해도 영영 안 뜨는 문제가 있었다
  if (el.classList.contains("rv-hero")) return;
  // 스크롤을 내린 채로 새로고침하면 이미 화면에 있는 요소들이 한 IO 콜백에
  // 무더기로 잡혀서 i*90ms 지연이 그대로 다 쌓인다(수십 개면 몇 초씩 밀림).
  // 이미 보이는 건 스크롤을 기다린 게 아니니 바로 노출하고, 아직 화면 밖인
  // 것만 관찰해서 스크롤할 때 한 줄씩 나타나는 원래 효과를 유지한다
  const r = el.getBoundingClientRect();
  if (r.top < innerHeight && r.bottom > 0) el.classList.add("in");
  else io.observe(el);
});

// 히어로 요소는 스크롤과 무관하게 페이지 로드와 함께 바로 재생한다.
// 순서는 CSS 의 animation-delay(각 요소 규칙 옆 주석 참고)가 맡는다
document.querySelectorAll(".rv-hero").forEach((el) => el.classList.add("in"));

// 네비 CTA — 히어로 버튼이 네비 뒤로 완전히 지나가면 나타난다.
// 위쪽 rootMargin 을 네비 높이만큼 깎아야 "네비 뒤에 가려진" 순간을 사라진 걸로 친다.
// 모바일은 히어로 버튼이 처음부터 화면 아래에 있을 수 있어서, 안 보인다는 것만으론
// 부족하고 "위로 지나갔다"(버튼 바닥이 관찰 영역 위)일 때만 켠다
const navCta = document.getElementById("navCta");
new IntersectionObserver(
  ([e]) =>
    navCta.classList.toggle(
      "on",
      !e.isIntersecting && e.boundingClientRect.bottom <= e.rootBounds.top,
    ),
  { rootMargin: `-${document.getElementById("nav").offsetHeight}px 0px 0px 0px` },
).observe(document.getElementById("heroCta"));

// hero parallax — 뒤에 깔린 두 화면만 살짝 따라 움직인다 (--px/--py 는 styles.css 참고)
const art = document.getElementById("art");
addEventListener("mousemove", (e) => {
  if (innerWidth < 900) return;
  const x = e.clientX / innerWidth - 0.5,
    y = e.clientY / innerHeight - 0.5;
  art.querySelectorAll(".ph-left,.ph-right").forEach((el, i) => {
    const d = i ? -12 : 12;
    el.style.setProperty("--px", x * d + "px");
    el.style.setProperty("--py", y * d + "px");
  });
});

// 기능 데모 — 화면에 보이는 동안만 반복 재생한다.
// 모션 줄이기 설정이면 움직이지 않고 "결과" 상태(검색 완료·처음 하트 숫자·전체 폴더)로 고정
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

const demoSearch = async (el, alive) => {
  const q = el.querySelector(".d-q");
  const text = q.dataset.q;
  el.classList.remove("found");
  q.textContent = "";
  el.classList.add("typing");
  for (const ch of text) {
    if (!alive()) return;
    q.textContent += ch;
    await sleep(65);
  }
  await sleep(200);
  el.classList.add("found");
  await sleep(1600);
  el.classList.remove("typing", "found");
  q.textContent = "";
  await sleep(400);
};

const demoLike = async (el, alive) => {
  const hearts = [...el.querySelectorAll(".d-heart")];
  hearts.forEach((h) => {
    h.textContent = h.dataset.n;
    h.classList.remove("liked");
  });
  await sleep(300);
  // 다른 사람들이 하나씩 누르는 흐름
  for (const i of [0, 3, 0, 2, 0]) {
    if (!alive()) return;
    const h = hearts[i];
    h.textContent = +h.textContent + 1;
    h.classList.add("liked", "bump");
    setTimeout(() => h.classList.remove("bump"), 300);
    await sleep(300);
  }
  await sleep(1500);
};

const demoFolder = async (el, alive) => {
  const chips = [...el.querySelectorAll(".d-chips span")];
  const imgs = el.querySelectorAll(".d-grid img");
  for (const chip of chips) {
    if (!alive()) return;
    chips.forEach((c) => c.classList.toggle("on", c === chip));
    const f = chip.dataset.f;
    imgs.forEach((img) => img.classList.toggle("off", f !== "all" && img.dataset.f !== f));
    await sleep(1100);
  }
};

[
  ["demoSearch", demoSearch],
  ["demoLike", demoLike],
  ["demoFolder", demoFolder],
].forEach(([id, run]) => {
  const el = document.getElementById(id);
  if (!el) return;
  if (reduced) {
    if (id === "demoSearch") {
      el.querySelector(".d-q").textContent = el.querySelector(".d-q").dataset.q;
      el.classList.add("found");
    }
    return;
  }
  let visible = false;
  let running = false;
  new IntersectionObserver(
    ([e]) => {
      visible = e.isIntersecting;
      if (visible && !running) {
        running = true;
        (async () => {
          while (visible) await run(el, () => visible);
          running = false;
        })();
      }
    },
    { threshold: 0.4 },
  ).observe(el);
});

// 기능 데모 탭 — 폰 폭에서만 보인다. 누른 탭의 데모만 보여주고, 자동으로 넘기지 않는다.
// 데스크톱에선 탭이 숨어 있고 .on 과 상관없이 세 데모가 다 보인다
const demoTabs = [...document.querySelectorAll(".demo-tabs button")];
if (demoTabs.length) {
  const show = (btn) => {
    demoTabs.forEach((b) => {
      const on = b === btn;
      b.classList.toggle("on", on);
      b.setAttribute("aria-selected", on);
      document.getElementById(b.dataset.demo).classList.toggle("on", on);
    });
  };
  show(demoTabs[0]);
  demoTabs.forEach((b) => b.addEventListener("click", () => show(b)));
}

// STEP — 폰 폭에선 손으로 넘기는 카드다. 자동으로 넘기지도, 힌트로 흔들지도 않는다
// (읽어야 하는 글이라). 넘길 수 있다는 건 옆 카드가 살짝 보이는 것과 점으로 알린다.
// 점은 지금 화면 가운데에 있는 카드를 따라간다
const stepsEl = document.querySelector(".steps");
const stepDots = [...document.querySelectorAll(".step-dots span")];
if (stepsEl && stepDots.length) {
  const cards = [...stepsEl.querySelectorAll(".step")];

  stepsEl.addEventListener(
    "scroll",
    () => {
      const mid = stepsEl.scrollLeft + stepsEl.clientWidth / 2;
      let best = 0;
      let bestD = Infinity;
      cards.forEach((c, i) => {
        const d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid);
        if (d < bestD) {
          bestD = d;
          best = i;
        }
      });
      stepDots.forEach((d, j) => d.classList.toggle("on", j === best));
    },
    { passive: true },
  );
}

// 기술 소개 — 분류 결과 확인 화면 예시. AI 가 묶은 결과에서 사진이나 묶음을 빼고,
// (뺀 사진은 목록에서 사라지고, 묶음마다 "되돌리기"로 다시 넣을 수 있다)
// 버튼을 누르면 고른 묶음만 폴더로 만들어진 결과를 보여 준다. 실제로 폴더를 만들지는 않는다
const techReview = document.getElementById("techReview");
if (techReview) {
  const groups = [...techReview.querySelectorAll(".t-group")];
  const applyBtn = techReview.querySelector(".t-apply");
  const applyCount = applyBtn.querySelector("span");
  const result = techReview.querySelector(".t-result");
  const resultList = result.querySelector("ul");

  const read = (group) => {
    const tiles = [...group.querySelectorAll(".t-tiles button")];
    const out = tiles.filter((t) => t.getAttribute("aria-pressed") === "true").length;
    return {
      name: group.querySelector("b").textContent,
      on: group.querySelector("input").checked,
      tiles,
      out,
      kept: tiles.length - out,
    };
  };

  const sync = () => {
    let selected = 0;
    groups.forEach((group) => {
      const g = read(group);
      group.classList.toggle("off", !g.on);
      g.tiles.forEach((t) => (t.disabled = !g.on));
      group.querySelector(".t-count").textContent = `${g.kept}장`;
      // 내가 고친 흔적 — 사진을 뺀 것을 한 줄로 보여 주고 되돌리기를 붙인다. 묶음을 끈 건 체크박스로 충분히 보인다
      const edit = group.querySelector(".t-edit");
      if (g.on && g.out) {
        edit.replaceChildren(`✕ ${g.out}장은 다른 사람이라 뺐어요`, group.undo);
      } else {
        edit.replaceChildren();
      }
      if (g.on) selected += 1;
    });
    applyCount.textContent = selected;
    applyBtn.disabled = selected === 0;
  };

  groups.forEach((group) => {
    const tiles = group.querySelectorAll(".t-tiles button");
    group.querySelector("input").addEventListener("change", sync);
    tiles.forEach((tile) => {
      tile.addEventListener("click", () => {
        // 누른 사진이 사라지면 포커스도 같이 사라지므로, 키보드로 눌렀다면 되돌리기로 옮긴다
        const hadFocus = tile.matches(":focus-visible");
        tile.setAttribute("aria-pressed", "true");
        sync();
        if (hadFocus) group.undo.focus();
      });
    });

    group.undo = document.createElement("button");
    group.undo.type = "button";
    group.undo.className = "t-undo";
    group.undo.textContent = "되돌리기";
    group.undo.addEventListener("click", () => {
      tiles.forEach((tile) => tile.setAttribute("aria-pressed", "false"));
      sync();
    });
  });

  applyBtn.addEventListener("click", () => {
    resultList.replaceChildren(
      ...groups
        .map(read)
        .filter((g) => g.on)
        .map((g) => {
          const li = document.createElement("li");
          li.textContent = `📁 ${g.name} · ${g.kept}장`;
          return li;
        }),
    );
    techReview.classList.add("done");
    result.hidden = false;
  });

  result.querySelector(".t-redo").addEventListener("click", () => {
    techReview.classList.remove("done");
    result.hidden = true;
  });

  sync();
}
