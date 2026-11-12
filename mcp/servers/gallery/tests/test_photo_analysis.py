"""Check persisted analysis results with fixed model outputs and real grouping."""
import contextlib
import io
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
import cv2

from pipeline.photo_analysis import run


class PhotoAnalysisTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs = self.root / 'input'
        self.inputs.mkdir()
        self.output = self.root / 'output'
        self.model = self.root / 'model'
        self.model.write_bytes(b'fixed test model')

    def prepare(self, input_dir, output_dir, progress):
        output_dir.mkdir()
        photos = []
        for index in range(2):
            pid = f'image_{index:06d}'
            cv2.imwrite(str(output_dir / f'{pid}.jpg'), np.full((64, 64, 3), 50 + index * 100, np.uint8))
            photos.append({'image_id': pid, 'source_filename': f'{index}.jpg', 'prepared_path': f'images/{pid}.jpg'})
        return photos, []

    def faces(self, photos, output, assets, progress):
        faces = [{'face_id': i, 'image_id': p['image_id'],
                  'bbox': [0, 0, 64, 64],
                  'quality': {'seed_failures': [], 'match_failures': []}}
                 for i, p in enumerate(photos)]
        return faces, np.array([[1., 0.], [1., 0.]]), [np.zeros(63, bool), np.ones(63, bool)], []

    def analyze(self, progress=None):
        features = SimpleNamespace(
            configure_runtime=lambda threads: {'test': 'fixed outputs'},
            resolve_assets=lambda root: {'test': self.model},
            prepare_photos=self.prepare, extract_faces=self.faces,
            extract_bodies=lambda *args: ([], []),
            extract_body_features=lambda *args: np.empty((0, 512)))
        with patch.dict('sys.modules', {'pipeline.image_features': features}), contextlib.redirect_stdout(io.StringIO()):
            return run(self.inputs, self.output, self.root, 2, progress)

    def test_every_stage_is_recorded_for_the_server_without_a_terminal_status(self):
        recorded = []
        self.analyze(SimpleNamespace(write=lambda stage, processed=None, total=None: recorded.append(stage)))
        self.assertEqual(recorded, ['initializing', 'loading_models', 'detecting_faces', 'grouping_faces',
                                    'detecting_bodies', 'extracting_body_features', 'matching_bodies', 'exporting'])

    def test_server_result_has_groups_without_local_report(self):
        result = self.analyze()
        self.assertEqual(result['groups'][0]['image_ids'], ['image_000000', 'image_000001'])
        self.assertEqual(result['counts']['memberships'], 2)
        self.assertEqual(result['artifacts'], {'manifest': 'manifest.json', 'evidence': 'evidence.json'})
        self.assertEqual(json.loads((self.output / 'result.json').read_text()), result)
        self.assertFalse((self.output / 'report.html').exists())
        self.assertFalse((self.output / 'folders').exists())

    def test_manifest_evidence_and_completed_state_are_persisted(self):
        result = self.analyze()
        manifest = json.loads((self.output / 'manifest.json').read_text())
        evidence = json.loads((self.output / 'evidence.json').read_text())
        job = json.loads((self.output / 'job.json').read_text())
        self.assertEqual([p['source_filename'] for p in manifest['photos']], ['0.jpg', '1.jpg'])
        self.assertEqual(len(manifest['models']['test']['sha256']), 64)
        self.assertEqual(len(evidence['faces']), 2)
        self.assertEqual(job['status'], 'completed')
        self.assertEqual(job['progress']['counts'], result['counts'])
        self.assertFalse(list(self.output.glob('*.tmp')))

    def test_existing_result_is_not_overwritten(self):
        self.analyze()
        before = (self.output / 'result.json').read_bytes()
        with self.assertRaises(FileExistsError):
            self.analyze()
        self.assertEqual((self.output / 'result.json').read_bytes(), before)

    def test_analysis_failure_is_persisted(self):
        with patch.object(self, 'faces', side_effect=RuntimeError('inference failed')):
            with self.assertRaisesRegex(RuntimeError, 'inference failed'):
                self.analyze()
        job = json.loads((self.output / 'job.json').read_text())
        self.assertEqual(job['status'], 'failed')
        self.assertEqual(job['progress']['error'], 'inference failed')
        self.assertFalse((self.output / 'result.json').exists())
