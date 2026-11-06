"""The similar pipeline announces a stage before its first slow step, not after it.

torch/cv2/PIL are not needed to check this ordering, so they are replaced with stubs.
"""
import contextlib
import importlib
import sys
import tempfile
import unittest
from pathlib import Path
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

import numpy as np


def stubs(events):
    def module(name, **attrs):
        mod = ModuleType(name)
        mod.__dict__.update(attrs)
        return mod

    class Opened:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def convert(self, mode):
            return self

    class Tensor:
        def unsqueeze(self, dim):
            return self

        def numpy(self):
            return np.array([[1., 0.]])

    torch = module('torch', inference_mode=contextlib.nullcontext,
                   nn=SimpleNamespace(functional=SimpleNamespace(normalize=lambda tensor, dim: tensor)))
    cv2 = module('cv2', setRNGSeed=lambda seed: None, ORB_create=lambda nfeatures: SimpleNamespace(
        detectAndCompute=lambda image, mask: ([], None)),
        imread=lambda path, flags: events.append('imread') or np.zeros((4, 4), np.uint8),
        resize=lambda image, size: image, IMREAD_GRAYSCALE=0, NORM_HAMMING=6,
        BFMatcher=lambda norm: SimpleNamespace())
    image = module('PIL.Image', open=lambda path: Opened())
    return {'torch': torch, 'cv2': cv2, 'PIL': module('PIL', Image=image), 'PIL.Image': image,
            'torchvision': module('torchvision', transforms=module('torchvision.transforms')),
            'torchvision.transforms': module('torchvision.transforms'),
            'pipeline.image_features': module('pipeline.image_features', configure_runtime=None,
                                              prepare_photos=None),
            'tensor': Tensor}


class SimilarProgressTests(unittest.TestCase):
    def load(self, events):
        modules = stubs(events)
        tensor = modules.pop('tensor')
        patcher = patch.dict(sys.modules, modules)
        patcher.start()
        self.addCleanup(patcher.stop)
        sys.modules.pop('pipeline.similar_analysis', None)
        self.addCleanup(sys.modules.pop, 'pipeline.similar_analysis', None)
        return importlib.import_module('pipeline.similar_analysis'), tensor

    def test_feature_stage_starts_before_the_first_inference(self):
        events = []
        similar, tensor = self.load(events)
        photos = [{'prepared_path': f'{i}.jpg'} for i in range(3)]
        job = SimpleNamespace(update=lambda stage, **details: events.append((stage, details)))
        transform = lambda image: tensor()

        def model(batch):
            events.append('inference')
            return tensor()

        with tempfile.TemporaryDirectory() as directory:
            similar.feature_matrix(photos, Path(directory), model, transform, job, 'extracting_scene_features')

        self.assertEqual(events[0], ('extracting_scene_features', {'processed': 0, 'total': 3}))
        self.assertEqual(events[1], 'inference')
        self.assertEqual(events[-1], ('extracting_scene_features', {'processed': 3, 'total': 3}))

    def test_matching_stage_starts_before_descriptors_are_prepared(self):
        events = []
        similar, _ = self.load(events)
        photos = [{'prepared_path': f'{i}.jpg'} for i in range(2)]
        job = SimpleNamespace(update=lambda stage, **details: events.append((stage, details)))

        with tempfile.TemporaryDirectory() as directory:
            similar.orb_scores(photos, Path(directory), job)

        self.assertEqual(events[0], ('matching_similar_photos', {'processed': 0, 'total': 2}))
        self.assertEqual(events[1], 'imread')


if __name__ == '__main__':
    unittest.main()
