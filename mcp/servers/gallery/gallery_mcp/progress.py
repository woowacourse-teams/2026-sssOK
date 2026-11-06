"""Latest analysis progress of one execution, shared by the worker subprocess and the MCP server.

The worker only records progress here; the delivery loop reads the latest value and decides when
to send it. Nothing in the analysis path performs HTTP, so recording a photo costs one small file write.
"""
import json
from pathlib import Path

# processed/total are counted per stage. Stages that cannot be measured carry no numbers and no unit.
STAGE_UNITS = {
    'downloading_photos': 'PHOTO',
    'preparing': 'PHOTO',
    'detecting_faces': 'PHOTO',
    'detecting_bodies': 'PHOTO',
    'extracting_body_features': 'BODY',
    'extracting_scene_features': 'PHOTO',
    'extracting_center_features': 'PHOTO',
    'matching_similar_photos': 'PHOTO',
}


def progress_path(data, execution_id):
    return Path(data) / 'runs' / execution_id / 'progress.json'


def snapshot(stage, processed=None, total=None):
    """Public progress shape. Numbers are dropped unless both are valid; the unit follows the stage."""
    measured = (type(processed) is int and type(total) is int and 0 <= processed <= total
                and stage in STAGE_UNITS)
    return {'stage': stage, 'processed': processed if measured else None,
            'total': total if measured else None, 'unit': STAGE_UNITS[stage] if measured else None}


class ProgressFile:
    """Single writer (the worker). Replaces the file atomically so readers never see a partial value."""

    def __init__(self, path):
        self.path = Path(path)
        self.last = None

    def write(self, stage, processed=None, total=None):
        value = snapshot(stage, processed, total)
        if value == self.last:
            return
        temporary = self.path.with_name(self.path.name + '.tmp')
        temporary.write_text(json.dumps(value), encoding='utf-8')
        temporary.replace(self.path)
        self.last = value


def read_progress(path):
    """Latest recorded progress, or None when nothing readable has been recorded yet."""
    try:
        value = json.loads(Path(path).read_text(encoding='utf-8'))
        return snapshot(value['stage'], value.get('processed'), value.get('total'))
    except (OSError, KeyError, TypeError, json.JSONDecodeError):
        return None
