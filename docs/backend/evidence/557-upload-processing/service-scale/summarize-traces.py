"""Summarize downloaded Tempo JSON without treating missing spans as zero cost."""
import argparse
import collections
import json
import math
from pathlib import Path
import statistics

parser = argparse.ArgumentParser()
parser.add_argument('directory', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()


def stats(values):
    if not values:
        return None
    values = sorted(values)
    return {'count': len(values), 'mean_ms': statistics.mean(values),
            'median_ms': statistics.median(values),
            'p95_ms': values[math.ceil(len(values) * .95) - 1],
            'max_ms': max(values)}


results = []
for path in sorted(args.directory.glob('*.json')):
    data = json.loads(path.read_text())
    if not isinstance(data, dict):
        continue
    spans = [s for b in data.get('batches', data.get('resourceSpans', []))
             for scope in b.get('scopeSpans', b.get('instrumentationLibrarySpans', []))
             for s in scope.get('spans', [])]
    registration = [s for s in spans if s.get('name') == 'upload.registration.total']
    if not registration:
        continue
    durations, queue, outcomes = collections.defaultdict(list), [], collections.Counter()
    for span in spans:
        name = span.get('name', '')
        if name.startswith('upload.'):
            durations[name].append((int(span['endTimeUnixNano']) - int(span['startTimeUnixNano'])) / 1e6)
        if name == 'upload.processing':
            attributes = {a['key']: next(iter(a['value'].values())) for a in span.get('attributes', [])}
            if 'queue.wait.ms' in attributes:
                queue.append(float(attributes['queue.wait.ms']))
            outcomes[attributes.get('processing.result', 'unknown')] += 1
    ready_spans = [s for s in spans if s.get('name') == 'upload.processing.ready_publish']
    reg_start = min(int(s['startTimeUnixNano']) for s in registration)
    last_ready_ms = ((max(int(s['endTimeUnixNano']) for s in ready_spans) - reg_start) / 1e6
                     if ready_spans else None)
    results.append({'trace_id': path.stem, 'span_count': len(spans),
                    'registration_to_last_ready_publish_ms': last_ready_ms,
                    'ready_publish_count': len(ready_spans),
                    'registration_epoch': int(registration[0]['startTimeUnixNano']) / 1e9,
                    'registration_count': len(registration),
                    'processing_outcomes': dict(outcomes), 'queue_wait': stats(queue),
                    'stages': {name: stats(values) for name, values in sorted(durations.items())}})
args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
print(f'Summarized {len(results)} registration traces')
