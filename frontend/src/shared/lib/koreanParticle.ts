const HANGUL_START = 0xac00;
const HANGUL_END = 0xd7a3;
const JONGSEONG_COUNT = 28;
/** ㄹ 받침 뒤에는 '으로' 가 아니라 '로' 가 붙는다 (보류로). */
const RIEUL = 8;

export const withDirectionParticle = (word: string) => {
  const code = word.charCodeAt(word.length - 1);

  if (Number.isNaN(code) || code < HANGUL_START || code > HANGUL_END) return `${word}(으)로`;

  const jongseong = (code - HANGUL_START) % JONGSEONG_COUNT;

  return jongseong === 0 || jongseong === RIEUL ? `${word}로` : `${word}으로`;
};
