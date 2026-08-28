import { crc32Of } from "./crc32";
import { buildStoreZip, ZipAbortedError } from "./storeZip";

/** 시각을 고정해야 헤더 바이트가 매번 같다. */
const MODIFIED_AT = new Date(2026, 7, 27, 13, 45, 30);

const bytesOf = async (blob: Blob) => new Uint8Array(await blob.arrayBuffer());

const readUint32 = (bytes: Uint8Array, offset: number) =>
  new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(offset, true);

const readUint16 = (bytes: Uint8Array, offset: number) =>
  new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint16(offset, true);

const LOCAL_HEADER_SIGNATURE = 0x04034b50;
const CENTRAL_HEADER_SIGNATURE = 0x02014b50;
const END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

describe("crc32Of", () => {
  it("규격이 정한 값을 낸다", () => {
    // 널리 쓰이는 검증값이다. 이 둘이 맞으면 표와 초기값·최종 뒤집기가 모두 맞는 것이다.
    expect(crc32Of(new TextEncoder().encode("hello"))).toBe(0x3610a686);
    expect(crc32Of(new TextEncoder().encode("123456789"))).toBe(0xcbf43926);
  });

  it("빈 입력은 0 이다", () => {
    expect(crc32Of(new Uint8Array(0))).toBe(0);
  });
});

describe("buildStoreZip", () => {
  const entries = [
    { name: "a.txt", blob: new Blob([new TextEncoder().encode("hello")]) },
    { name: "한글.txt", blob: new Blob([new TextEncoder().encode("123456789")]) },
  ];

  it("local header 로 시작하고 EOCD 로 끝난다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));

    expect(readUint32(zip, 0)).toBe(LOCAL_HEADER_SIGNATURE);
    expect(readUint32(zip, zip.length - 22)).toBe(END_OF_CENTRAL_DIRECTORY_SIGNATURE);
  });

  it("항목 수를 EOCD 에 적는다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));
    const eocd = zip.length - 22;

    expect(readUint16(zip, eocd + 8)).toBe(2);
    expect(readUint16(zip, eocd + 10)).toBe(2);
  });

  it("EOCD 가 가리키는 자리에 central directory 가 있다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));
    const directoryOffset = readUint32(zip, zip.length - 22 + 16);

    expect(readUint32(zip, directoryOffset)).toBe(CENTRAL_HEADER_SIGNATURE);
  });

  it("central directory 의 오프셋이 각 항목의 local header 를 가리킨다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));
    const directoryOffset = readUint32(zip, zip.length - 22 + 16);
    const firstNameLength = readUint16(zip, directoryOffset + 28);
    const secondHeader = directoryOffset + 46 + firstNameLength;

    expect(readUint32(zip, readUint32(zip, directoryOffset + 42))).toBe(LOCAL_HEADER_SIGNATURE);
    expect(readUint32(zip, readUint32(zip, secondHeader + 42))).toBe(LOCAL_HEADER_SIGNATURE);
  });

  it("항목마다 제 체크섬과 크기를 적는다 — 0 이면 압축 도구가 손상으로 본다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));

    expect(readUint32(zip, 14)).toBe(0x3610a686);
    // store 라 압축 크기와 원본 크기가 같다.
    expect(readUint32(zip, 18)).toBe(5);
    expect(readUint32(zip, 22)).toBe(5);
  });

  it("이름이 UTF-8 이라고 표시한다 — 안 켜면 윈도우에서 한글이 깨진다", async () => {
    const zip = await bytesOf(await buildStoreZip(entries, { modifiedAt: MODIFIED_AT }));

    expect(readUint16(zip, 6) & 0x0800).toBe(0x0800);
  });

  it("원본 바이트를 그대로 담는다", async () => {
    const zip = await buildStoreZip(entries, { modifiedAt: MODIFIED_AT });
    const bytes = await bytesOf(zip);
    const nameLength = readUint16(bytes, 26);
    const data = bytes.slice(30 + nameLength, 30 + nameLength + 5);

    expect(new TextDecoder().decode(data)).toBe("hello");
  });

  it("항목이 없어도 EOCD 만 있는 빈 zip 을 만든다", async () => {
    const zip = await bytesOf(await buildStoreZip([], { modifiedAt: MODIFIED_AT }));

    expect(zip.length).toBe(22);
    expect(readUint32(zip, 0)).toBe(END_OF_CENTRAL_DIRECTORY_SIGNATURE);
  });

  it("중단되면 만들다 만 zip 을 내놓지 않고 던진다", async () => {
    const controller = new AbortController();

    controller.abort();

    await expect(buildStoreZip(entries, { signal: controller.signal })).rejects.toThrow(
      ZipAbortedError,
    );
  });
});
