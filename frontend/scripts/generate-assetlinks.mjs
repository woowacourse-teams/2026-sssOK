// Play Console의 앱 서명 키 인증서 SHA-256을 인자로 받아 표준 출력으로 내보낸다.
const fingerprint = process.argv[2]?.toUpperCase();
if (!/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(fingerprint ?? "")) {
  throw new Error("Play 앱 서명 키 인증서의 SHA-256 지문(콜론으로 구분한 32바이트)이 필요합니다.");
}

const statements = [
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: "com.ssssok.app",
      sha256_cert_fingerprints: [fingerprint],
    },
  },
];
console.log(JSON.stringify(statements, null, 2));
