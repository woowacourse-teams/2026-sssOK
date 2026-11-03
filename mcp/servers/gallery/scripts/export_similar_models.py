"""One-time offline export. Serving never downloads or executes a remote Hub repository.

Requires the reviewed local facebookresearch/dinov2 source tree, its ViT-S/14
checkpoint and torchvision's ResNet50 IMAGENET1K_V2 checkpoint. Run using the
same torch/torchvision versions as Dockerfile. Outputs are private model assets,
not source files to commit. New export hashes require review before deployment.
"""
import argparse
import hashlib
import json
from pathlib import Path

import torch
import torchvision


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dino-source', type=Path, required=True)
    parser.add_argument('--dino-weights', type=Path, required=True)
    parser.add_argument('--resnet-weights', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    torch.set_num_threads(2)
    manifest = {}
    for name in ('dinov2', 'resnet50'):
        if name == 'dinov2':
            model = torch.hub.load(str(args.dino_source), 'dinov2_vits14', source='local', pretrained=False)
            model.load_state_dict(torch.load(args.dino_weights, map_location='cpu', weights_only=True))
        else:
            model = torchvision.models.resnet50(weights=None)
            model.load_state_dict(torch.load(args.resnet_weights, map_location='cpu', weights_only=True))
            model.fc = torch.nn.Identity()
        model.eval()
        torch.manual_seed(661)
        with torch.inference_mode():
            # Fixed 1x3x224x224 inference only, matching the production preprocessing.
            artifact = torch.jit.trace(model, torch.randn(1, 3, 224, 224))
            target = args.output / f'{name}.pt'
            artifact.save(str(target))
            for seed in (661, 662):
                torch.manual_seed(seed)
                sample = torch.randn(1, 3, 224, 224)
                torch.testing.assert_close(torch.jit.load(str(target))(sample), model(sample), atol=1e-4, rtol=1e-4)
        manifest[target.name] = {'experimentPath': 'exported/' + target.name,
                                 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()}
        del model, artifact
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
