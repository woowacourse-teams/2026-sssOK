#!/usr/bin/env bash
# source monitoring/dev-monitoring-mode.sh /home/ubuntu/app
# 중앙 수신 확인 후 담당자가 monitoring-mode 파일을 central로 바꾼다. CD는 덮어쓰지 않는다.
DEV_MONITORING_MODE=legacy
if [ -f "${1:?배포 경로 필요}/monitoring-mode" ]; then
  DEV_MONITORING_MODE=$(cat "$1/monitoring-mode")
fi
case "$DEV_MONITORING_MODE" in
  legacy) COMPOSE_PROFILES=legacy; DEV_ALLOY_WRITER=dual ;;
  central) COMPOSE_PROFILES=; DEV_ALLOY_WRITER=central ;;
  *) echo 'monitoring-mode는 legacy 또는 central이어야 합니다' >&2; return 1 ;;
esac
export DEV_MONITORING_MODE COMPOSE_PROFILES DEV_ALLOY_WRITER
