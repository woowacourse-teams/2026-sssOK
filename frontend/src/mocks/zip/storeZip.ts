import { Crc32 } from "./crc32";
import { blobChunks } from "./blobChunks";

/**
 * 여러 파일을 zip 하나로 묶는다. **압축하지 않고 그대로 담는다**(store, method 0).
 *
 * JPEG·HEIC·MP4 는 이미 압축된 형식이라 deflate 를 돌려도 크기가 1% 남짓 줄 뿐인데,
 * 폰에서 수십 MB 를 압축하는 동안 메인 스레드가 통째로 멈춘다. 목적은 "작게"가 아니라
 * **"한 번에 받기"** 이므로, 봉투만 씌우고 바이트는 건드리지 않는 쪽이 맞다.
 *
 * 라이브러리를 쓰지 않는다. store 전용 zip 은 헤더 세 종류가 전부라 아래로 충분하고,
 * `runWithLimit` 과 같은 이유로 직접 둔다 — 우리가 쓰는 만큼만 있으면 된다.
 *
 * 규격: APPNOTE.TXT 4.3.6 (local header → data → central directory → EOCD)
 */

const LOCAL_HEADER_SIGNATURE = 0x04034b50;
const CENTRAL_HEADER_SIGNATURE = 0x02014b50;
const END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

/** 2.0. store 와 폴더만 쓰는 zip 이 요구하는 최소 버전이다. */
const VERSION = 20;

/**
 * 비트 11(EFS). 이름이 UTF-8 이라고 알리는 표시다.
 * 이걸 안 켜면 윈도우 탐색기가 이름을 CP949 로 읽어서 한글 파일명이 전부 깨진다.
 */
const UTF8_NAME_FLAG = 0x0800;

/** store 는 압축을 안 한다는 뜻의 method 값이다. */
const METHOD_STORE = 0;

/**
 * 4GB. 크기·오프셋 칸이 32비트라 이 위로는 ZIP64 확장이 필요하다.
 * 폰에서 4GB 를 메모리에 조립하는 시나리오는 어차피 성립하지 않아, 넘으면 만들지 않는다.
 */
const ZIP32_LIMIT = 0xffffffff;

export interface ZipEntry {
  /** 이미 `zipFileNames` 로 중복·경로 문자를 정리한 이름이어야 한다. */
  name: string;
  blob: Blob;
}

export interface BuildStoreZipOptions {
  signal?: AbortSignal;
  /** 지금까지 훑은 누적 바이트. 큰 영상에서 "묶는 중"이 멈춘 것처럼 보이지 않게 한다. */
  onHashed?: (bytes: number) => void;
  /** zip 안에 적을 수정 시각. 테스트가 결과를 고정하려고 넘긴다. */
  modifiedAt?: Date;
}

export class ZipTooLargeError extends Error {
  constructor() {
    super("zip 으로 묶기에 너무 큽니다.");
    this.name = "ZipTooLargeError";
  }
}

export class ZipAbortedError extends Error {
  constructor() {
    super("압축이 중단되었습니다.");
    this.name = "ZipAbortedError";
  }
}

/** DOS 형식 시각. 2초 단위라 홀수 초는 버려지는데, 규격이 그렇다. */
const dosTimeOf = (date: Date) =>
  (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1);

/** DOS 형식 날짜. 1980년이 0년이다. 그 이전 날짜는 표현할 수 없어 1980으로 눕힌다. */
const dosDateOf = (date: Date) =>
  (Math.max(date.getFullYear() - 1980, 0) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();

interface PlacedEntry {
  nameBytes: Uint8Array;
  crc: number;
  size: number;
  /** 이 항목의 local header 가 시작하는 위치. central directory 가 이걸 가리킨다. */
  offset: number;
}

const localHeaderOf = (entry: PlacedEntry, time: number, date: number) => {
  const header = new Uint8Array(30 + entry.nameBytes.length);
  const view = new DataView(header.buffer);

  view.setUint32(0, LOCAL_HEADER_SIGNATURE, true);
  view.setUint16(4, VERSION, true);
  view.setUint16(6, UTF8_NAME_FLAG, true);
  view.setUint16(8, METHOD_STORE, true);
  view.setUint16(10, time, true);
  view.setUint16(12, date, true);
  view.setUint32(14, entry.crc, true);
  // store 라서 압축 크기와 원본 크기가 같다.
  view.setUint32(18, entry.size, true);
  view.setUint32(22, entry.size, true);
  view.setUint16(26, entry.nameBytes.length, true);
  view.setUint16(28, 0, true);

  header.set(entry.nameBytes, 30);

  return header;
};

const centralDirectoryOf = (entries: PlacedEntry[], time: number, date: number) => {
  const size = entries.reduce((sum, entry) => sum + 46 + entry.nameBytes.length, 0);
  const directory = new Uint8Array(size);
  const view = new DataView(directory.buffer);
  let cursor = 0;

  for (const entry of entries) {
    view.setUint32(cursor, CENTRAL_HEADER_SIGNATURE, true);
    view.setUint16(cursor + 4, VERSION, true);
    view.setUint16(cursor + 6, VERSION, true);
    view.setUint16(cursor + 8, UTF8_NAME_FLAG, true);
    view.setUint16(cursor + 10, METHOD_STORE, true);
    view.setUint16(cursor + 12, time, true);
    view.setUint16(cursor + 14, date, true);
    view.setUint32(cursor + 16, entry.crc, true);
    view.setUint32(cursor + 20, entry.size, true);
    view.setUint32(cursor + 24, entry.size, true);
    view.setUint16(cursor + 28, entry.nameBytes.length, true);
    // extra·comment·disk·속성은 전부 0 이다. 우리는 평평한 파일 목록만 담는다.
    view.setUint32(cursor + 42, entry.offset, true);

    directory.set(entry.nameBytes, cursor + 46);
    cursor += 46 + entry.nameBytes.length;
  }

  return directory;
};

const endOfCentralDirectoryOf = (count: number, size: number, offset: number) => {
  const record = new Uint8Array(22);
  const view = new DataView(record.buffer);

  view.setUint32(0, END_OF_CENTRAL_DIRECTORY_SIGNATURE, true);
  // 디스크 분할은 쓰지 않는다 — 4~8번 바이트는 0 그대로 둔다.
  view.setUint16(8, count, true);
  view.setUint16(10, count, true);
  view.setUint32(12, size, true);
  view.setUint32(16, offset, true);

  return record;
};

/**
 * 항목들을 zip Blob 하나로 만든다.
 *
 * 파일 바이트는 **복사하지 않는다.** 헤더만 새로 만들고 원본 Blob 을 그대로 조각으로
 * 넘겨, 브라우저가 알아서 디스크에 받쳐두게 한다. 메모리에 한꺼번에 올리는 것은
 * 체크섬을 구하는 동안의 조각 하나뿐이다.
 */
export const buildStoreZip = async (
  entries: ZipEntry[],
  { signal, onHashed, modifiedAt = new Date() }: BuildStoreZipOptions = {},
): Promise<Blob> => {
  const time = dosTimeOf(modifiedAt);
  const date = dosDateOf(modifiedAt);
  const encoder = new TextEncoder();
  const parts: BlobPart[] = [];
  const placed: PlacedEntry[] = [];
  let offset = 0;
  let hashed = 0;

  for (const entry of entries) {
    const crc = new Crc32();

    for await (const chunk of blobChunks(entry.blob)) {
      if (signal?.aborted) {
        throw new ZipAbortedError();
      }

      crc.update(chunk);
      hashed += chunk.length;
      onHashed?.(hashed);
    }

    const nameBytes = encoder.encode(entry.name);
    const placedEntry: PlacedEntry = {
      nameBytes,
      crc: crc.value,
      size: entry.blob.size,
      offset,
    };

    const header = localHeaderOf(placedEntry, time, date);

    parts.push(header, entry.blob);
    placed.push(placedEntry);

    offset += header.length + entry.blob.size;

    // 다음 항목의 오프셋이 32비트를 넘으면 그 항목부터 위치를 적을 수 없다.
    if (offset > ZIP32_LIMIT) {
      throw new ZipTooLargeError();
    }
  }

  const directory = centralDirectoryOf(placed, time, date);

  parts.push(directory, endOfCentralDirectoryOf(placed.length, directory.length, offset));

  return new Blob(parts, { type: "application/zip" });
};
