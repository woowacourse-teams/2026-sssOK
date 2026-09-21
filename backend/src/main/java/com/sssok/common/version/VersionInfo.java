package com.sssok.common.version;

/**
 * 실행 중인 백엔드가 자기 자신을 식별하는 값.
 *
 * <p>세 값은 각각 기준 위치가 다르다.
 * <ul>
 *   <li>{@code backendVersion} — {@code backend/gradle.properties}, 빌드 시 JAR의 build-info 에 박힌다</li>
 *   <li>{@code releaseVersion} — 저장소 루트 {@code VERSION}, 배포 파이프라인이 환경변수로 주입한다</li>
 *   <li>{@code gitSha} — 배포 대상 커밋, 배포 파이프라인이 환경변수로 주입한다</li>
 * </ul>
 *
 * <p>주입이 없는 로컬·IDE 실행에서는 값이 {@link #UNKNOWN} 이 된다. 기동을 막지 않는다.
 */
public record VersionInfo(String releaseVersion, String backendVersion, String gitSha) {

    public static final String UNKNOWN = "unknown";
}
