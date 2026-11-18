/** 입력창에 쓴 글을 읽은 결과. 아는 이름이 들어 있으면 name 에 담는다. */
export interface SearchRequest {
  query: string;
  name?: string;
}

// 입력창에서 '나' 대신 쓰는 말. 이름 목록의 '나' 와 같은 사람으로 본다.
const SELF_ALIASES = ["나", "내"];

// 이름 뒤에 올 수 있는 조사. "나온" 의 '나' 처럼 다른 낱말 속 글자는 이름으로 보지 않는다.
const NAME_END = "(?:이랑|하고|랑|과|와|이|가|의|만|도)?(?=\\s|$|나온|찍힌|올린|사진)";

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
 * 입력한 글을 검색어로 읽고, 글 안에 아는 이름이 있으면 함께 돌려준다.
 * 이름이 있어도 검색어는 그대로 두어 사진 내용으로도 찾을 수 있게 한다.
 */
export const parseRequest = (text: string, names: string[]): SearchRequest | null => {
  const query = text.trim();
  if (query.length === 0) return null;

  const name = findName(query, names);
  return name ? { query, name } : { query };
};
