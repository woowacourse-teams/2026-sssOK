// GitHub Issue 하나를 Notion Ticket DB 페이지 하나로 동기화한다.
//
// 멱등성은 Ticket DB의 "Issue Number" 프로퍼티로 잡는다 — 같은 이슈 번호를 가진 페이지가
// 이미 있으면 업데이트하고, 없을 때만 만든다. 라벨을 연달아 바꿔서 워크플로가 여러 번
// 돌아도 페이지가 중복 생성되지 않는 이유가 이것이다.
//
// 의존성 없이 Node 20의 global fetch만 쓴다. npm install 단계를 없애 워크플로를 짧게 유지한다.

import { readFileSync } from "node:fs";

const NOTION_API = "https://api.notion.com/v1";
const NOTION_VERSION = "2022-06-28";

// 난이도 라벨 → Difficulty / EXP. 이슈 #181 표를 그대로 옮긴 것이다.
const DIFFICULTY_BY_LABEL = {
  "exp: easy": { difficulty: "Easy", exp: 10 },
  "exp: normal": { difficulty: "Normal", exp: 20 },
  "exp: hard": { difficulty: "Hard", exp: 30 },
  "exp: very-hard": { difficulty: "Very Hard", exp: 50 },
  "exp: boss": { difficulty: "Boss", exp: 100 },
};

// 작업 범위 라벨. Ticket DB의 Scope(multi-select)에 그대로 들어간다.
const SCOPE_LABELS = ["frontend", "backend", "fullstack"];

// 이슈 종류 라벨. Ticket DB의 Type(select)에 들어간다. 여러 개면 첫 번째만 쓴다.
const TYPE_LABELS = ["feature", "bug", "refactor", "chore", "test"];

const STATUS_WAITING = "대기";
const STATUS_ISSUED = "발급됨";
const STATUS_CLEAR = "Clear";

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    // 토큰이나 DB ID가 비면 조용히 넘어가지 않고 워크플로를 실패시킨다 (완료 조건 "실패 처리").
    throw new Error(`환경변수 ${name} 가 비어 있다. 저장소 Secrets/Variables 설정을 확인하라.`);
  }
  return value;
}

async function notion(path, { method = "GET", body } = {}) {
  const res = await fetch(`${NOTION_API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${requireEnv("NOTION_TOKEN")}`,
      "Notion-Version": NOTION_VERSION,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  if (!res.ok) {
    // 401/404 를 성공으로 삼키면 "동기화된 줄 알았는데 아니었다"가 되므로 그대로 터뜨린다.
    throw new Error(`Notion API ${method} ${path} 실패 (${res.status}): ${text}`);
  }
  return text ? JSON.parse(text) : {};
}

function readIssue() {
  const event = JSON.parse(readFileSync(requireEnv("GITHUB_EVENT_PATH"), "utf8"));
  return event.issue;
}

// GitHub 이슈 상태 → Notion Status.
// closed 면 무조건 Clear, open 이면 담당자 유무로 대기/발급됨이 갈린다.
function resolveStatus(issue) {
  if (issue.state === "closed") return STATUS_CLEAR;
  return issue.assignees?.length > 0 ? STATUS_ISSUED : STATUS_WAITING;
}

function resolveDifficulty(issue) {
  const names = issue.labels.map((label) => label.name);
  const matched = names.find((name) => name in DIFFICULTY_BY_LABEL);
  // 난이도 라벨이 없으면 Difficulty 는 비우고 EXP 는 0 으로 둔다 (완료 조건 "난이도 / EXP").
  return matched ? DIFFICULTY_BY_LABEL[matched] : { difficulty: null, exp: 0 };
}

// GitHub 담당자 login 으로 Player DB 행을 찾는다.
// Player DB 에는 GitHub login 을 담는 텍스트 프로퍼티 "GitHub" 가 있어야 한다.
// 못 찾아도 예외를 던지지 않는다 — Player 만 빈 채로 Ticket 은 정상 생성되어야 한다.
async function findPlayerPageIds(logins, playerDbId) {
  const ids = [];
  for (const login of logins) {
    const result = await notion(`/databases/${playerDbId}/query`, {
      method: "POST",
      body: {
        filter: { property: "GitHub", rich_text: { equals: login } },
        page_size: 1,
      },
    });
    if (result.results.length > 0) {
      ids.push(result.results[0].id);
    } else {
      console.warn(`Player DB 에 GitHub login "${login}" 에 해당하는 행이 없다. Player 를 비운 채로 진행한다.`);
    }
  }
  return ids;
}

async function findTicketPage(issueNumber, ticketDbId) {
  const result = await notion(`/databases/${ticketDbId}/query`, {
    method: "POST",
    body: {
      filter: { property: "Issue Number", number: { equals: issueNumber } },
      page_size: 1,
    },
  });
  return result.results[0] ?? null;
}

function buildProperties(issue, playerIds) {
  const status = resolveStatus(issue);
  const { difficulty, exp } = resolveDifficulty(issue);
  const labelNames = issue.labels.map((label) => label.name);

  const properties = {
    Ticket: { title: [{ text: { content: `#${issue.number} ${issue.title}` } }] },
    "Issue Number": { number: issue.number },
    "Issue URL": { url: issue.html_url },
    // Status 는 상태(status) 가 아니라 선택(select) 타입이다 — 상태 타입은 API 로 옵션을
    // 만들 수 없어서 대기/발급됨/Clear 를 직접 정의할 수 있는 select 를 골랐다.
    Status: { select: { name: status } },
    Difficulty: { select: difficulty ? { name: difficulty } : null },
    EXP: { number: exp },
    Player: { relation: playerIds.map((id) => ({ id })) },
    Scope: {
      multi_select: labelNames
        .filter((name) => SCOPE_LABELS.includes(name))
        .map((name) => ({ name })),
    },
    // Clear 된 티켓의 완료일. 다시 열리면 비워서 "완료 기록"이 남지 않게 한다.
    "Cleared At": {
      date: status === STATUS_CLEAR && issue.closed_at ? { start: issue.closed_at } : null,
    },
  };

  const type = labelNames.find((name) => TYPE_LABELS.includes(name));
  properties.Type = { select: type ? { name: type } : null };

  return properties;
}

async function main() {
  const ticketDbId = requireEnv("NOTION_TICKET_DB_ID");
  const playerDbId = requireEnv("NOTION_PLAYER_DB_ID");

  const issue = readIssue();

  if (issue.pull_request) {
    console.log("PR 이벤트이므로 건너뛴다.");
    return;
  }

  const logins = (issue.assignees ?? []).map((assignee) => assignee.login);
  const playerIds = await findPlayerPageIds(logins, playerDbId);
  const properties = buildProperties(issue, playerIds);
  const existing = await findTicketPage(issue.number, ticketDbId);

  // 이미 Clear 인 티켓의 Player 와 완료일은 건드리지 않는다. 완료 기록은 "그때 누가
  // 끝냈는지"가 핵심이라, 나중에 이슈 담당자를 바꿨다고 과거 기록까지 따라 바뀌면 안 된다.
  if (existing?.properties?.Status?.select?.name === STATUS_CLEAR) {
    delete properties.Player;
    delete properties["Cleared At"];
  }

  // DRY_RUN=1 이면 Notion 에 쓰지 않고 보낼 내용만 찍는다. main 에 머지하기 전에는
  // issues 이벤트가 워크플로를 띄우지 않아서, 로컬 확인 수단이 이것뿐이다.
  if (process.env.DRY_RUN === "1") {
    console.log(`대상 이슈: #${issue.number} ${issue.title}`);
    console.log(`담당자: ${logins.join(", ") || "(없음)"} → Player ${playerIds.length}건 매칭`);
    console.log(`기존 Ticket: ${existing ? `있음 (${existing.id}) → 갱신` : "없음 → 생성"}`);
    console.log(JSON.stringify(properties, null, 2));
    return;
  }

  if (existing) {
    await notion(`/pages/${existing.id}`, { method: "PATCH", body: { properties } });
    console.log(`Ticket 갱신: #${issue.number} → ${resolveStatus(issue)}`);
    return;
  }

  await notion("/pages", {
    method: "POST",
    body: { parent: { database_id: ticketDbId }, properties },
  });
  console.log(`Ticket 생성: #${issue.number} → ${resolveStatus(issue)}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
