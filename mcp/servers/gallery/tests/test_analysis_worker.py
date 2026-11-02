"""Exercise the actual worker's input/result conversion with a stub analysis engine."""
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4

from gallery_mcp.analysis_worker import execute
from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService


class AnalysisWorkerTests(unittest.TestCase):
    def test_worker_commits_result_without_parent_file_handoff(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = root / 'input'
            inputs.mkdir()
            (inputs / 'photo.jpg').write_bytes(b'local test photo')
            settings = Settings(data=root / 'data', assets=root / 'models', token='t' * 32, input_root=inputs)
            service = PersonGroupingService(settings)
            execution = str(uuid4())
            photo = Photo(mediaId=42, source='photo.jpg', extension='jpg')
            service.start(1, execution, [photo])
            service.store.claim()
            def analyze(source, output, *args):
                self.assertEqual((source / photo.filename).read_bytes(), b'local test photo')
                output.mkdir()
                (output / 'manifest.json').write_text(json.dumps({'photos': [
                    {'image_id': 'image_000001', 'source_filename': photo.filename}]}))
                return {'groups': [], 'counts': {'groups': 0}, 'pipeline_version': 'test',
                        'unassigned_image_ids': ['image_000001'], 'errors': []}
            with patch.dict('sys.modules', {'pipeline.photo_analysis': SimpleNamespace(run=analyze)}):
                execute(1, execution, settings)
            # No parent execute()/finish() call is needed for a complete, durable result.
            restored = PersonGroupingService(settings)
            with restored.boot():
                restored.cleanup_finished()
                result = restored.get(1, execution)
                self.assertEqual(result['state'], 'COMPLETED')
                self.assertEqual(result['result']['unassignedMediaIds'], [42])
                self.assertIsNone(restored.store.get(1, execution)['acknowledged_at'])
                self.assertFalse((settings.data / 'runs' / execution).exists())
