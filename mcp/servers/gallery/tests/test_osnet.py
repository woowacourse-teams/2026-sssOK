import unittest
from importlib.util import find_spec
from unittest.mock import patch

HAS_TORCH = find_spec('torch') is not None
if HAS_TORCH:
    from pipeline.vendor import osnet


@unittest.skipUnless(HAS_TORCH, 'OSNet tests require the analysis runtime (PyTorch)')
class OsnetTests(unittest.TestCase):
    def test_factories_default_to_local_weights_and_reject_automatic_weights(self):
        for name in osnet.__all__:
            with self.subTest(factory=name), patch.object(osnet, 'OSNet') as model:
                factory = getattr(osnet, name)
                self.assertIs(factory(), model.return_value)
                self.assertIs(factory(pretrained=False), model.return_value)
                with self.assertRaisesRegex(ValueError, 'checksum-verified local checkpoint'):
                    factory(pretrained=True)

    def test_direct_automatic_weight_loading_is_disabled(self):
        with self.assertRaisesRegex(ValueError, 'Automatic pretrained weights are disabled'):
            osnet.init_pretrained_weights(object(), 'osnet_x1_0')
