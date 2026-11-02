// PostHog 지표를 집계해 디스코드 채널에 요약을 보낸다.
//
// 매일: 어제 하루를 지난주 같은 요일과 비교한다.
// 월요일(또는 WEEKLY=true): 지난주 7일을 그 전 주와 비교한 요약을 함께 보낸다.
//
// 집계 기준은 PostHog 「[알림] 일별 업로드·다운로드」 인사이트와 같다. 팀원·테스트는
// is_internal 미설정 + $host = www.ssssok.com 으로 거른다. SQL 은 프로젝트의 테스트 계정
// 필터를 따르지 않으므로 쿼리에 직접 넣는다.
//
// 의존성 없이 Node 20의 global fetch만 쓴다.

const POSTHOG_HOST = "https://us.posthog.com";
const POSTHOG_PROJECT_ID = "620426";
const DASHBOARD_URL = `${POSTHOG_HOST}/project/${POSTHOG_PROJECT_ID}/dashboard/2151595`;

const DAY_MS = 24 * 60 * 60 * 1000;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    // 키나 웹후크가 비면 조용히 넘어가지 않고 워크플로를 실패시킨다.
    throw new Error(`환경변수 ${name} 가 비어 있다. 저장소 Secrets 설정을 확인하라.`);
  }
  return value;
}

// KST 자정을 UTC 필드로 들고 다닌다. getUTC* 로 읽으면 KST 날짜가 나온다.
function kstToday() {
  const now = new Date(Date.now() + KST_OFFSET_MS);
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
}

function addDays(date, days) {
  return new Date(date.getTime() + days * DAY_MS);
}

function toSqlDate(date) {
  return date.toISOString().slice(0, 10);
}

function label(date) {
  return `${date.getUTCMonth() + 1}/${date.getUTCDate()}(${WEEKDAYS[date.getUTCDay()]})`;
}

// [from, to) 구간의 지표를 한 행으로 받는다. 날짜는 KST 자정 기준.
function metricsSql(from, to) {
  return `
    SELECT
      sumIf(toInt(properties.photo_count), event = 'Photo Uploaded') AS up_photos,
      -- 일부 실패(Uploaded.failed_count) + 전부 실패(Upload Failed). 파일 선택 단계 거절(stage=selection)은 제외
      sumIf(toInt(properties.failed_count),
        event = 'Photo Uploaded' OR (event = 'Photo Upload Failed' AND properties.stage IS NULL)) AS up_failed,
      sumIf(toInt(properties.photo_count), event = 'Photo Downloaded') AS down_photos,
      sumIf(toInt(properties.failed_count), event IN ('Photo Downloaded', 'Photo Download Failed')) AS down_failed,
      uniq(person_id) AS visitors,
      -- 방 생성·입장 이벤트에는 role 이 없어 따로 더한다. 입장만 하고 나간 게스트도 활성으로 센다
      uniqIf(person_id, properties.role = 'host' OR event = 'Room Created') AS active_hosts,
      uniqIf(person_id, properties.role = 'guest' OR event = 'Room Joined') AS active_guests,
      countIf(event = 'Room Created') AS rooms_created,
      -- Room Joined 는 입장 화면을 거친 게스트만 남긴다 (방장은 방을 만들며 바로 들어간다)
      uniqIf(properties.room_code, event = 'Room Joined') AS rooms_with_guest,
      uniqIf(person_id, event = 'Room Joined') AS joined_guests,
      length(arrayIntersect(
        groupUniqArrayIf(person_id, event = 'Room Joined'),
        groupUniqArrayIf(person_id, event = 'Photo Uploaded' AND properties.role = 'guest')
      )) AS converted_guests
    FROM events
    WHERE timestamp >= toDateTime('${toSqlDate(from)} 00:00:00', 'Asia/Seoul')
      AND timestamp < toDateTime('${toSqlDate(to)} 00:00:00', 'Asia/Seoul')
      AND properties.is_internal IS NULL
      AND properties.$host = 'www.ssssok.com'
  `;
}

async function queryMetrics(from, to) {
  const res = await fetch(`${POSTHOG_HOST}/api/projects/${POSTHOG_PROJECT_ID}/query/`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${requireEnv("POSTHOG_PERSONAL_API_KEY")}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ query: { kind: "HogQLQuery", query: metricsSql(from, to) } }),
  });

  const text = await res.text();
  if (!res.ok) {
    throw new Error(`PostHog 쿼리 실패 (${res.status}): ${text}`);
  }
  const { columns, results } = JSON.parse(text);
  // 이벤트가 하나도 없으면 sum 이 null 로 온다. 0 으로 맞춘다.
  return Object.fromEntries(columns.map((column, i) => [column, Number(results[0][i] ?? 0)]));
}

function formatNumber(value) {
  return value.toLocaleString("ko-KR");
}

// 지난 기간 대비 증감. 지난 기간이 0 이면 비율을 낼 수 없어 "새로 발생"으로 보여 준다.
function percentChange(current, previous) {
  if (previous === 0) return current === 0 ? 0 : null;
  return Math.round(((current - previous) / previous) * 100);
}

function change(current, previous) {
  const percent = percentChange(current, previous);
  if (percent === null) return "새로 발생";
  if (percent === 0) return "변화 없음";
  return percent > 0 ? `▲${percent}%` : `▼${Math.abs(percent)}%`;
}

// "- 업로드 **59장** (▲20% · 지난주 화요일 49장)" — 증감 옆에 무엇과 비교했는지 값까지 붙인다
function countLine(name, unit, current, previous, compareLabel) {
  return `- ${name} **${formatNumber(current)}${unit}** (${change(current, previous)} · ${compareLabel} ${formatNumber(previous)}${unit})`;
}

function failLine(name, current, previous, compareLabel) {
  const line = countLine(name, "장", current, previous, compareLabel);
  return current > 0 ? line.replace("- ", "- ⚠️ ") : line;
}

function rate(converted, joined) {
  return joined === 0 ? null : Math.round((converted / joined) * 100);
}

function rateLine(current, previous, compareLabel) {
  const now = rate(current.converted_guests, current.joined_guests);
  const before = rate(previous.converted_guests, previous.joined_guests);
  const beforeText = before === null ? "-" : `${before}%`;
  if (now === null) return `- 게스트 업로드 전환율 **-** (입장한 게스트 없음 · ${compareLabel} ${beforeText})`;
  const diff =
    before === null ? "비교 없음" : now === before ? "변화 없음" : `${now > before ? "▲" : "▼"}${Math.abs(now - before)}%p`;
  return `- 게스트 업로드 전환율 **${now}%** (${diff} · ${compareLabel} ${beforeText}) · ${current.joined_guests}명 중 ${current.converted_guests}명`;
}

// 20% 이상 움직인 지표로 한 줄 요약을 만든다. 실패가 있으면 그게 먼저다.
const SUMMARY_THRESHOLD = 20;
const SUMMARY_METRICS = [
  ["업로드", "up_photos"],
  ["다운로드", "down_photos"],
  ["방문자", "visitors"],
  ["게스트", "active_guests"],
  ["새로 만든 방", "rooms_created"],
];

// 마지막 글자 받침에 따라 은/는을 붙인다 ("방은", "게스트는")
function topic(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code % 28 !== 0 ? `${word}은` : `${word}는`;
}

function summary(current, previous) {
  const up = [];
  const down = [];
  for (const [name, key] of SUMMARY_METRICS) {
    const percent = percentChange(current[key], previous[key]);
    if (percent === null || percent >= SUMMARY_THRESHOLD) up.push(name);
    else if (percent <= -SUMMARY_THRESHOLD) down.push(name);
  }

  const parts = [];
  if (up.length > 0) parts.push(`${topic(up.join("·"))} 늘었고`);
  if (down.length > 0) parts.push(`${topic(down.join("·"))} 줄었어요`);
  // "늘었고." 로 끝나지 않게 다듬는다
  const sentence = parts.length === 0 ? "지난번과 비슷했어요." : `${parts.join(", ")}.`.replace(/늘었고\.$/, "늘었어요.");

  if (current.up_failed + current.down_failed > 0) {
    return `⚠️ 실패가 있었어요 (업로드 ${formatNumber(current.up_failed)}장 · 다운로드 ${formatNumber(current.down_failed)}장). ${sentence}`;
  }
  return sentence;
}

// 카드 본문(description)에 전부 넣는다. 필드 값은 ### 제목·글머리 목록을 그려 주지 않지만 본문은 그려 준다.
function embed(title, current, previous, compareLabel, color) {
  return {
    title,
    url: DASHBOARD_URL,
    color,
    description: [
      summary(current, previous),
      "### 📤 업로드·다운로드",
      countLine("업로드", "장", current.up_photos, previous.up_photos, compareLabel),
      failLine("업로드 실패", current.up_failed, previous.up_failed, compareLabel),
      countLine("다운로드", "장", current.down_photos, previous.down_photos, compareLabel),
      failLine("다운로드 실패", current.down_failed, previous.down_failed, compareLabel),
      "### 👥 활성 사용자",
      countLine("방문자", "명", current.visitors, previous.visitors, compareLabel),
      countLine("방장", "명", current.active_hosts, previous.active_hosts, compareLabel),
      countLine("게스트", "명", current.active_guests, previous.active_guests, compareLabel),
      "### 🏠 방 생성·게스트 참여",
      countLine("새로 만든 방", "개", current.rooms_created, previous.rooms_created, compareLabel),
      countLine("게스트가 들어온 방", "개", current.rooms_with_guest, previous.rooms_with_guest, compareLabel),
      rateLine(current, previous, compareLabel),
      "",
      `[대시보드 열기](${DASHBOARD_URL})`,
    ].join("\n"),
  };
}
async function sendDiscord(embeds) {
  const res = await fetch(requireEnv("DISCORD_METRICS_WEBHOOK_URL"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ embeds }),
  });
  if (!res.ok) {
    throw new Error(`디스코드 전송 실패 (${res.status}): ${await res.text()}`);
  }
}

async function main() {
  const today = kstToday();
  const yesterday = addDays(today, -1);
  const weekly = today.getUTCDay() === 1 || process.env.WEEKLY === "true";

  const [daily, dailyBefore] = await Promise.all([
    queryMetrics(yesterday, today),
    queryMetrics(addDays(yesterday, -7), addDays(today, -7)),
  ]);
  const embeds = [
    embed(`📊 어제 ${label(yesterday)} 쏙 지표`, daily, dailyBefore, `지난주 ${WEEKDAYS[yesterday.getUTCDay()]}요일`, 0x3b82f6),
  ];

  if (weekly) {
    const weekStart = addDays(today, -7);
    const [week, weekBefore] = await Promise.all([
      queryMetrics(weekStart, today),
      queryMetrics(addDays(today, -14), weekStart),
    ]);
    embeds.push(embed(`🗓️ 지난주 ${label(weekStart)}~${label(yesterday)} 쏙 지표`, week, weekBefore, "그 전 주", 0x8b5cf6));
  }

  if (process.env.DRY_RUN === "true") {
    console.log(JSON.stringify({ embeds }, null, 2));
    return;
  }
  await sendDiscord(embeds);
  console.log(`디스코드로 ${embeds.length}개 요약을 보냈다.`);
}

await main();
