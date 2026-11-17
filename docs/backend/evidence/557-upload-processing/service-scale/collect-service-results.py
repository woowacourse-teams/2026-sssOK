"""Export only allowlisted k6 summary fields; never copy setup_data/tokens."""
import argparse
import csv
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('directory', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
rows = []
for row in csv.reader((args.directory / 'cases.csv').open()):
    if len(row) != 7 or not row[1].isdigit():
        continue
    tag, n, users, start, end, exit_code, health = row
    path = args.directory / 'logs' / f'{tag}-media-upload.json'
    if not path.exists():
        rows.append({'run_id': tag, 'error': 'summary_missing', 'case': row})
        continue
    data = json.loads(path.read_text())
    allowed = {k: data.get(k) for k in ['runId', 'appVersion', 'target', 'startedAtUtc',
               'finishedAtUtc', 'config', 'phases', 'extras', 'thresholds']}
    rows.append({'run_id': tag, 'images_per_uploader': int(n), 'uploaders': int(users),
                 'start_epoch': int(start), 'end_epoch': int(end), 'k6_exit': int(exit_code),
                 'health_after': int(health), 'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                 'summary': allowed})
args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
print(f'Exported {len(rows)} cases without credentials')
