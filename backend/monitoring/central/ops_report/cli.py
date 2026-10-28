"""명령: run(보고) · backfill(누락 집계 복구) · status(실행·발송 기록) · backup(DB 사본) · doctor(설치 점검)."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import date, datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys

from . import message
from .discord import Webhook, read_webhook
from .periods import today
from .prometheus import DEFAULT_URL, Prometheus
from .service import RECOVER_DAYS, Reporter, deliver, message_key
from .store import Store

DEPLOY = Path('/home/ubuntu/monitoring')
STATE = Path(os.environ.get('STATE_DIRECTORY', '/var/lib/sssok-ops-report'))
UNITS = ('sssok-ops-report.service', 'sssok-ops-report.timer')


def log(text: str) -> None:
    print(text, flush=True)


@contextmanager
def locked(db: str):
    if db == ':memory:':
        yield
        return
    path = Path(db).with_suffix('.lock')
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.open('a') as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit('다른 보고 작업이 실행 중입니다. 종료 후 다시 실행하세요.') from None
        yield


def settings(path: Path) -> dict:
    """알림 발송 선택(discord-selection.json)과 별도인 보고 발송 설정. 파일이 없으면 보내지 않는다."""
    if not path.exists():
        return {'send': False}
    try:
        value = json.loads(path.read_text())
    except (OSError, ValueError):
        raise SystemExit(f'보고 설정을 읽을 수 없습니다: {path}') from None
    if not isinstance(value, dict) or set(value) - {'send'} or not isinstance(value.get('send', False), bool):
        raise SystemExit('보고 설정 형식은 {"send": true|false} 입니다')
    return value


def parse_now(text: str | None) -> datetime:
    if not text:
        return datetime.now(timezone.utc)
    moment = datetime.fromisoformat(text)
    if moment.tzinfo is None:
        raise SystemExit('--now에는 시간대를 포함하세요 (예: 2026-10-27T09:00:00+09:00)')
    return moment


def prometheus(args) -> Prometheus:
    token = args.prometheus_token_file.read_text().strip() if args.prometheus_token_file else None
    return Prometheus(args.prometheus, token=token)


def command_run(args) -> int:
    now = parse_now(args.now)
    report_day = date.fromisoformat(args.date) if args.date else today(now) - timedelta(days=1)
    # 지난 날짜를 지정한 실행이 설정만으로 발송되지 않도록 --send를 따로 요구한다.
    send = not args.preview and (args.send if args.date else (args.send or settings(args.settings)['send']))
    # 조회를 마친 뒤 웹훅 설정 오류로 실패하지 않도록 먼저 읽는다.
    webhook = Webhook(read_webhook(args.env_file)) if send else None
    with locked(args.db):
        store = Store(args.db)
        run_id = store.start_run(args.trigger, report_day.isoformat())
        try:
            reporter = Reporter(store, prometheus(args), now, log)
            kind, report_ids, body, state = reporter.build(report_day, args.refresh)
            if not send:
                log(message.preview(body))
                reason = ('미리보기' if args.preview else '--send 없음' if args.date else '발송 설정 꺼짐')
                log(f'발송하지 않았습니다({reason}).')
                outcome = 'skipped'
            else:
                outcome = deliver(store, webhook, message_key(report_day, kind), report_day, kind,
                                  report_ids, run_id, body, args.force, log)
            failed = kind == 'failure' or outcome in ('failed', 'unknown')
            result = 'failed' if failed else ('partial' if state == 'partial' else 'succeeded')
            store.finish_run(run_id, result, None if not failed else f'report={kind}, delivery={outcome}')
            return 1 if failed else 0
        except BaseException as error:
            store.finish_run(run_id, 'failed', f'{type(error).__name__}: {error}'[:500])
            raise
        finally:
            store.close()


def command_backfill(args) -> int:
    now = parse_now(args.now)
    with locked(args.db):
        store = Store(args.db)
        run_id = store.start_run('backfill', None)
        try:
            reporter = Reporter(store, prometheus(args), now, log)
            last = today(now) - timedelta(days=1)
            if args.refresh:
                filled = [day for day in (last - timedelta(days=i) for i in range(args.days - 1, -1, -1))
                          if reporter.daily(day, refresh=True)]
            else:
                filled = reporter.recover(last, args.days)
            log(f'집계 복구 {len(filled)}일. 과거 보고는 발송하지 않습니다.')
            store.finish_run(run_id, 'succeeded')
            return 0
        except BaseException as error:
            store.finish_run(run_id, 'failed', f'{type(error).__name__}: {error}'[:500])
            raise
        finally:
            store.close()


def command_status(args) -> int:
    store = Store(args.db)
    runs, deliveries = store.recent(args.limit)
    log('최근 실행')
    for r in runs:
        log(f"  #{r['id']} {r['started_at']} {r['trigger']} {r['target_date'] or '-'} {r['status']}"
            + (f" · {r['error']}" if r['error'] else ''))
    log('최근 발송')
    for d in deliveries:
        log(f"  {d['message_key']} {d['status']} 시도 {d['attempts']}회 message={d['message_id'] or '-'}"
            + (f" · {d['last_error']}" if d['last_error'] else ''))
    store.close()
    return 0


def command_backup(args) -> int:
    """실행 중에도 일관된 SQLite 사본을 만든다."""
    if args.to.exists():
        raise SystemExit(f'이미 있는 파일입니다: {args.to}')
    args.to.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    source = Store(args.db)
    target = sqlite3.connect(str(args.to))
    try:
        source.db.backup(target)
    finally:
        target.close()
        source.close()
    args.to.chmod(0o600)
    log(f'DB 백업 완료: {args.to}')
    return 0


def command_doctor(args) -> int:
    problems = []
    if not prometheus(args).ready():
        problems.append('Prometheus 준비 상태 조회 실패')
    try:
        Store(args.db).close()
    except Exception as error:
        problems.append(f'DB 열기 실패: {error}')
    config = settings(args.settings)
    if config['send']:
        try:
            read_webhook(args.env_file)
        except (OSError, RuntimeError) as error:
            problems.append(str(error))
    source = Path(__file__).resolve().parent
    for unit in UNITS:
        installed = Path('/etc/systemd/system') / unit
        if not installed.is_file() or installed.read_bytes() != (source / unit).read_bytes():
            problems.append(f'{installed}가 저장소 파일과 다릅니다. 설치 절차를 다시 수행하세요.')
    if shutil.which('systemctl'):
        for check in ('is-enabled', 'is-active'):
            result = subprocess.run(['systemctl', check, UNITS[1]], capture_output=True, text=True)
            if result.returncode:
                problems.append(f'{UNITS[1]} {check}: {result.stdout.strip() or result.stderr.strip()}')
    log(f"발송 설정: {'켜짐' if config['send'] else '꺼짐(미리보기·집계만)'}")
    for problem in problems:
        log('문제: ' + problem)
    log('점검 통과' if not problems else f'점검 실패 {len(problems)}건')
    return 1 if problems else 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog='python3 -m ops_report', description=__doc__)
    parser.add_argument('--db', default=str(STATE / 'report.sqlite3'))
    parser.add_argument('--prometheus', default=DEFAULT_URL)
    parser.add_argument('--prometheus-token-file', type=Path,
                        help='Grafana 데이터소스 프록시 조회용 토큰 파일')
    parser.add_argument('--settings', type=Path, default=DEPLOY / 'ops-report.json')
    parser.add_argument('--env-file', type=Path, default=DEPLOY / '.env')
    parser.add_argument('--now', help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest='command', required=True)
    run = commands.add_parser('run', help='보고서 생성·발송')
    run.add_argument('--date', help='보고 대상 날짜(KST, YYYY-MM-DD). 생략하면 어제')
    run.add_argument('--preview', action='store_true', help='발송하지 않고 메시지만 출력')
    run.add_argument('--send', action='store_true', help='날짜 지정 실행에서도 발송')
    run.add_argument('--force', action='store_true', help='발송 완료·결과 불확실 기록이 있어도 다시 발송')
    run.add_argument('--refresh', action='store_true', help='완료된 대상일 집계도 다시 조회')
    run.add_argument('--trigger', default='manual', choices=['manual', 'timer'])
    backfill = commands.add_parser('backfill', help='누락 일간 집계 복구(발송 없음)')
    backfill.add_argument('--days', type=int, default=RECOVER_DAYS)
    backfill.add_argument('--refresh', action='store_true', help='기존 집계도 다시 조회')
    status = commands.add_parser('status', help='최근 실행·발송 기록')
    status.add_argument('--limit', type=int, default=10)
    backup = commands.add_parser('backup', help='DB 사본 생성')
    backup.add_argument('--to', type=Path, required=True)
    commands.add_parser('doctor', help='설치·설정 점검')
    args = parser.parse_args(argv)
    if args.command == 'backfill' and not 1 <= args.days <= 15:
        parser.error('--days는 Prometheus 보존 기간 안(1~15)이어야 합니다')
    return {'run': command_run, 'backfill': command_backfill, 'status': command_status,
            'backup': command_backup, 'doctor': command_doctor}[args.command](args)


if __name__ == '__main__':
    sys.exit(main())
