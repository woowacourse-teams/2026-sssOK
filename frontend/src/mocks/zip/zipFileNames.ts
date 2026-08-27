/**
 * zip 안에 넣을 이름을 정한다. **서버(`DownloadFileNames.deduplicate`)가 하는 일이라 목에 있다.**
 * 올릴 때 이름을 그대로 쓰되(003-selection-download),
 * 그대로 쓰면 깨지는 두 가지만 손본다.
 *
 * 1. **경로 문자** — 이름에 `/` 가 남아 있으면 zip 안에서 폴더가 된다. 방에 올라온
 *    사진이 압축을 풀었더니 남의 디렉터리 구조를 만드는 일은 없어야 한다.
 * 2. **중복** — 서로 다른 사람이 각자의 폰에서 올린 `IMG_0001.jpg` 는 흔하다.
 *    같은 이름이 두 번 들어간 zip 은 푸는 쪽에서 하나가 조용히 덮인다.
 */

/** 경로 구분자와 제어 문자만 막는다. 한글·이모지는 그대로 둔다 — zip 은 UTF-8 을 싣는다. */
// eslint-disable-next-line no-control-regex
const UNSAFE = /[\x00-\x1f/\\:*?"<>|]/g;

const FALLBACK_NAME = "media";

const sanitize = (name: string) => {
  const cleaned = name.replace(UNSAFE, "_").trim();

  // `..` 만 남으면 압축 해제 도구에 따라 상위 경로로 해석된다.
  return cleaned === "" || cleaned === "." || cleaned === ".." ? FALLBACK_NAME : cleaned;
};

/** `photo.jpg` → `["photo", ".jpg"]`. 확장자가 없거나 `.gitignore` 처럼 점으로 시작하면 통째로 몸통이다. */
const splitExtension = (name: string): [string, string] => {
  const dot = name.lastIndexOf(".");

  return dot <= 0 ? [name, ""] : [name.slice(0, dot), name.slice(dot)];
};

/**
 * 들어온 순서 그대로, 앞의 것이 이름을 먼저 차지한다.
 * 뒤에 온 같은 이름은 `IMG_0001 (1).jpg` 처럼 확장자 **앞에** 번호가 붙는다 —
 * 뒤에 붙이면 확장자가 사라져 미리보기가 안 된다.
 *
 * 대소문자를 무시하고 비교한다. `photo.JPG` 와 `photo.jpg` 는 윈도우·맥에서 같은 파일이라,
 * zip 안에서만 다르면 푸는 순간 하나가 사라진다.
 */
export const zipFileNames = (names: string[]) => {
  const taken = new Set<string>();

  return names.map((name) => {
    const safe = sanitize(name);
    const [body, extension] = splitExtension(safe);

    let candidate = safe;
    let ordinal = 1;

    while (taken.has(candidate.toLowerCase())) {
      candidate = `${body} (${ordinal})${extension}`;
      ordinal += 1;
    }

    taken.add(candidate.toLowerCase());

    return candidate;
  });
};
