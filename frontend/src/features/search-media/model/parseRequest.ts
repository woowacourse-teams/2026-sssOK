/** 요청 패널에서 입력한 글을 이 방에서 할 수 있는 요청으로 읽은 결과. */
export type SearchRequest =
  | { type: "organize" }
  | { type: "unsupported" }
  | { type: "person"; name: string }
  | { type: "uploader"; name: string }
  | { type: "keyword"; query: string };

// 입력창에서 '나' 대신 쓰는 말. 이름 목록의 '나' 와 같은 사람으로 본다.
const SELF_ALIASES = ["나", "내"];

// 이름 뒤에 올 수 있는 조사. "나온" 의 '나' 처럼 다른 낱말 속 글자는 이름으로 보지 않는다.
const NAME_END = "(?:이랑|하고|랑|과|와|이|가|의|만|도)?(?=\\s|$|나온|찍힌|올린|사진)";

const ORGANIZE =
  /사람별|인물별|자동\s*(으로|로)?\s*(나눠|나누|정리)|(사람|친구|얼굴|인물).*(나눠|나누|정리|모아|분류)/;
const UNSUPPORTED = /연속|잘\s*나온|베스트|흔들|눈\s*감|날짜별|장소별|지워|지우기|삭제|옮겨/;
const UPLOADED = /올린|올려/;

const escapeRegExp = (text: string): string => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/**
 * 글 안에서 아는 이름을 찾는다. 이름은 문장 어디에 있어도 되고 조사가 붙어도 된다.
 * 긴 이름부터 맞춰야 '하루나' 가 있을 때 '하루' 로 잘못 잡지 않는다.
 */
export const findName = (text: string, names: string[]): string | undefined => {
  const candidates = [...new Set([...names, ...(names.includes("나") ? SELF_ALIASES : [])])]
    .filter((name) => name.length > 0)
    .sort((a, b) => b.length - a.length);

  const found = candidates.find((name) =>
    new RegExp(`(?:^|\\s)${escapeRegExp(name)}${NAME_END}`).test(text),
  );

  return found && SELF_ALIASES.includes(found) ? "나" : found;
};

/**
 * 입력한 글을 요청으로 읽는다. 문장을 이해하는 게 아니라 자주 쓰는 말투만 잡는 규칙이다.
 * 아는 이름이 없고 정리·미지원 말투도 아니면 사진 내용 검색어로 본다.
 */
export const parseRequest = (text: string, names: string[]): SearchRequest | null => {
  const query = text.trim();
  if (query.length === 0) return null;

  if (ORGANIZE.test(query)) return { type: "organize" };
  if (UNSUPPORTED.test(query)) return { type: "unsupported" };

  const name = findName(query, names);
  if (name) return UPLOADED.test(query) ? { type: "uploader", name } : { type: "person", name };

  return { type: "keyword", query };
};
