/**
 * `createdAt`(ISO 8601, UTC) 을 한국 시간 `MM-DD HH:mm` 으로 바꾼다.
 *
 * 관리자는 한국에서 본다. UTC 를 그대로 보여주면 "오전에 들어온 의견" 이 전날 밤으로 보인다.
 * 브라우저 시간대에 맡기지 않고 서울로 못박는 건, 관리자가 해외에서 열어도 팀이 이야기하는
 * 시각과 같아야 하기 때문이다.
 */
const formatter = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  // h23 으로 못박지 않으면 엔진에 따라 자정이 `24:00` 으로 나온다.
  hourCycle: "h23",
});

type ReadPart = (type: Intl.DateTimeFormatPartTypes) => string;

const readParts = (createdAt: string): ReadPart | null => {
  const time = Date.parse(createdAt);

  // 읽지 못한 시각은 비워 둔다. `Invalid Date` 를 그대로 찍는 것보다 낫다.
  if (Number.isNaN(time)) return null;

  const parts = formatter.formatToParts(new Date(time));

  return (type) => parts.find((part) => part.type === type)?.value ?? "";
};

export const formatFeedbackTime = (createdAt: string): string | null => {
  const read = readParts(createdAt);

  if (read === null) return null;

  return `${read("month")}-${read("day")} ${read("hour")}:${read("minute")}`;
};

/**
 * 상세 모달용 `YYYY-MM-DD HH:mm`.
 *
 * 목록은 최근 며칠 치를 훑는 자리라 연도를 뺐지만, 상세는 한 건을 기록으로 읽는 자리다.
 * 해를 넘긴 의견을 열었을 때 올해 것으로 오해하지 않게 연도까지 적는다.
 */
export const formatFeedbackDateTime = (createdAt: string): string | null => {
  const read = readParts(createdAt);

  if (read === null) return null;

  return `${read("year")}-${read("month")}-${read("day")} ${read("hour")}:${read("minute")}`;
};
