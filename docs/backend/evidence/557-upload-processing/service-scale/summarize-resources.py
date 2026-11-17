"""Summarize sampled container/host resources for explicit k6 run intervals."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser()
parser.add_argument('resources', type=Path)
parser.add_argument('--cases', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
data = [json.loads(line) for line in args.resources.read_text().splitlines() if line.strip()]


def mib(text):
    match = re.fullmatch(r'([0-9.]+)([KMGT]?i?B)', text.strip())
    units = {'B': 1 / 1048576, 'kB': 1000 / 1048576, 'KB': 1000 / 1048576,
             'MB': 1000000 / 1048576, 'GB': 1000000000 / 1048576,
             'KiB': 1 / 1024, 'MiB': 1, 'GiB': 1024, 'TiB': 1048576}
    if not match or match[2] not in units:
        raise ValueError(text)
    return float(match[1]) * units[match[2]]


result = []
for case in csv.reader(args.cases.open()):
    if len(case) != 7 or not case[1].isdigit():
        continue
    tag, count, users, start, end, code, health = case
    start, end = int(start), int(end)
    rows = [r for r in data if start <= r['epoch'] <= end]
    good = [r for r in rows if 'stats' in r and 'state' in r and 'error' not in r]
    stats = [json.loads(r['stats']) for r in good]
    result.append({'run_id': tag, 'samples': len(good), 'errors': len(rows) - len(good),
                   'first_sample_epoch': min((r['epoch'] for r in good), default=None),
                   'last_sample_epoch': max((r['epoch'] for r in good), default=None),
                   'initial_coverage_missing': not good or min(r['epoch'] for r in good) > start + 15,
                   'sampled_cpu_percent_max': max((float(r['CPUPerc'].rstrip('%')) for r in stats), default=None),
                   'sampled_memory_mib_max': max((mib(r['MemUsage'].split('/')[0]) for r in stats), default=None),
                   'host_available_mib_min': min((r['available_kib'] / 1024 for r in good), default=None),
                   'swap_free_kib_min': min((r['swap_free_kib'] for r in good), default=None),
                   'restart_oom_states': sorted(set(r['state'] for r in good))})
args.output.write_text(json.dumps({'source_sha256': hashlib.sha256(args.resources.read_bytes()).hexdigest(),
                                   'cases': result}, indent=2) + '\n')
print(f'Summarized {len(result)} run intervals')
