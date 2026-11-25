// 사진 업로드 시나리오 공용 부분. 전용 방 만들기, 10장 묶음 업로드, 끝난 뒤 방째 정리를 한 곳에 둔다.
//
//   업로드 (10장 묶음 하나):
//     POST /media/upload-urls  10장 발급 — 거절 0장이어야 한다
//     PUT  <서명 URL>           스토리지 직접 업로드. 사용자별 동시 3개
//     POST /media               완료 등록 — PUT 에 성공한 장이 모두 등록되어야 한다 (부분 실패는 실패)
//
//   업로드는 실행마다 새로 만든 전용 방에 한다. k6 는 fixtures 에 쓸 수 없어 올린 사진을 기록할 수 없으므로,
//   방 안의 사진은 모두 이 실행이 올린 것이 되게 하고 끝날 때(teardown) 방째 정리한다.
import http from 'k6/http';
import exec from 'k6/execution';

import { env, fromCwd } from './config.js';
import { businessFailures, businessOk } from './http.js';

// 스토리지 PUT 은 우리 서버가 아니라 R2 로 가는 요청이라 API 와 따로 본다(storage_put).
export const UPLOAD_APIS = ['upload_issue', 'storage_put', 'upload_complete'];
export const BATCH_SIZE = 10;
export const PUT_CONCURRENCY = 3;
const API_PREFIX = '/api/v1';
// 서버 RoomName.MAX_LENGTH
const MAX_ROOM_NAME = 12;
const DELETE_CHUNK = 500;

export function loadImages(dir) {
  let manifest;
  try {
    manifest = JSON.parse(open(fromCwd(`${dir}/manifest.json`)));
  } catch {
    throw new Error(`${dir}/manifest.json 이 없습니다. 먼저 준비한다: node seed/upload-images.mjs`);
  }
  // 가상 사용자마다 이미지를 메모리에 올린다. 장 수 × 가상 사용자 수만큼 쓰므로 큰 실제 사진은 장 수를 줄인다.
  return manifest.files.map((file) => ({ ...file, bytes: open(fromCwd(`${dir}/${file.name}`), 'b') }));
}

// 검색 분석이 켜진 서버는 올린 장마다 외부 AI 호출이 생긴다. 시드의 --allow-ai-cost 와 같은 확인이다.
export function guardAiCost(baseUrl, { roomId, token }) {
  const probe = http.get(`${baseUrl}${API_PREFIX}/rooms/${roomId}/media/all?query=loadtest%20probe`, {
    headers: { Authorization: `Bearer ${token}` }, tags: { api: 'setup' }, responseCallback: http.expectedStatuses(200, 503),
  });
  if (probe.status === 200 && env('ALLOW_AI_COST') !== '1') {
    exec.test.abort('대상 서버의 이미지 검색이 켜져 있어 올린 장마다 외부 AI 호출이 생깁니다. 비용 한도를 확인했으면 -e ALLOW_AI_COST=1');
  }
  return probe.status === 200;
}

// 방장이 전용 방을 만들고 members 를 모두 입장시킨다. 하나라도 실패하면 만든 방을 지우고 멈춘다.
export function createDedicatedRoom(client, { host, members, nameTag, label }) {
  const name = `${nameTag}${Date.now().toString(36)}`.slice(0, MAX_ROOM_NAME);
  const created = client.post('setup', '/rooms', { token: host.accessToken, body: { name, uploadPolicy: 'everyone' }, expectedStatus: 201 });
  if (!created.ok) exec.test.abort(`${label}을 만들지 못했습니다: ${created.reason}`);
  const room = created.data;
  for (const member of members) {
    const joined = client.post('setup', `/rooms/${room.roomId}/members`, { token: member.token, body: { code: room.code }, expectedStatus: 201 });
    if (!joined.ok) {
      deleteRoomWithMedia(client, { roomId: room.roomId, hostToken: host.accessToken, label });
      exec.test.abort(`userId=${member.userId} 이 ${label}에 입장하지 못했습니다: ${joined.reason}`);
    }
  }
  console.log(`${label} roomId=${room.roomId} code=${room.code} (끝나면 사진과 함께 지운다)`);
  return room;
}

// 10장 묶음 하나를 올리고 등록된 장 수를 돌려준다. 실패는 http.js·여기서 업무 실패로 센다.
// fileName 에 nameTag·key 를 넣어 서버 로그에서 어느 실행·반복의 사진인지 찾을 수 있게 한다.
export function uploadBatch(client, { roomId, token, images, key, nameTag }) {
  const base = `/rooms/${roomId}/media`;
  const files = Array.from({ length: BATCH_SIZE }, (_, k) => {
    const image = images[(key * BATCH_SIZE + k) % images.length];
    const ext = image.name.split('.').pop();
    return { image, request: { fileName: `${nameTag}-${key}-${k}.${ext}`, mimeType: image.mimeType, size: image.size } };
  });

  const issued = client.post('upload_issue', `${base}/upload-urls`, {
    token,
    body: { files: files.map((f) => f.request) },
    validate: (body) => {
      if (body.rejected.length > 0) return `발급 거절 ${body.rejected.length}장 (${body.rejected[0].code})`;
      if (body.issued.length !== files.length) return `발급 ${body.issued.length} ≠ ${files.length}`;
      return null;
    },
  });
  if (!issued.ok) return 0;

  const uploaded = putAll(issued.data.issued, files);
  if (uploaded.length === 0) return 0;

  const completed = client.post('upload_complete', base, {
    token,
    body: { mediaIds: uploaded },
    expectedStatus: 201,
    validate: (body) => {
      if (body.failed.length > 0) return `완료 등록 부분 실패 ${body.failed.length}장 (${body.failed[0].code})`;
      if (body.registered.length !== uploaded.length) return `등록 ${body.registered.length} ≠ PUT 성공 ${uploaded.length}`;
      return null;
    },
  });
  return completed.data && Array.isArray(completed.data.registered) ? completed.data.registered.length : 0;
}

// 사용자별 동시 3개씩 PUT 한다. 발급 응답의 method·headers 를 그대로 써야 서명이 맞는다.
function putAll(issuedList, files) {
  const uploaded = [];
  for (let i = 0; i < issuedList.length; i += PUT_CONCURRENCY) {
    const chunk = issuedList.slice(i, i + PUT_CONCURRENCY);
    const requests = chunk.map((item) => {
      const file = files.find((f) => f.request.fileName === item.fileName);
      return {
        method: item.method, url: item.uploadUrl, body: file.image.bytes,
        params: { headers: item.headers, tags: { api: 'storage_put' }, timeout: '60s', responseCallback: http.expectedStatuses(200) },
      };
    });
    const responses = http.batch(requests);
    responses.forEach((res, k) => {
      const ok = res.status === 200;
      businessOk.add(ok, { api: 'storage_put' });
      if (ok) {
        uploaded.push(chunk[k].mediaId);
      } else {
        businessFailures.add(1, { api: 'storage_put', reason: `status ${res.status}` });
        console.warn(`[storage_put] mediaId=${chunk[k].mediaId} 실패: status ${res.status}`);
      }
    });
  }
  return uploaded;
}

// 전용 방의 사진은 모두 이 실행이 올린 것이다. 사진을 먼저 지워 스토리지 원본까지 정리하고 방을 지운다.
// 지우기 전에 서버가 가진 사진 수를 돌려준다(목록 조회 실패면 null).
export function deleteRoomWithMedia(client, { roomId, hostToken, label }) {
  const res = client.get('teardown', `/rooms/${roomId}/media/all`, { token: hostToken });
  const ids = res.ok ? res.data.items.map((item) => item.mediaId) : [];
  let deleted = 0;
  for (let i = 0; i < ids.length; i += DELETE_CHUNK) {
    const del = client.del('teardown', `/rooms/${roomId}/media`, { token: hostToken, body: { mediaIds: ids.slice(i, i + DELETE_CHUNK) } });
    if (del.ok) deleted += del.data.deletedCount;
  }
  const room = client.del('teardown', `/rooms/${roomId}`, { token: hostToken });
  const message = `${label} roomId=${roomId} 정리: 사진 ${deleted}/${ids.length}장 삭제, 방 ${room.ok ? '삭제' : `삭제 실패(${room.reason})`}`;
  if (!res.ok || deleted !== ids.length || !room.ok) {
    console.error(`${message} — 남은 사진·방을 방장 토큰으로 직접 지운다`);
  } else {
    console.log(message);
  }
  return res.ok ? ids.length : null;
}
