"""Copy and verify existing experiment assets; does not download or publish model weights."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile


def prepare_models(source_root, output, manifest):
    output.mkdir(parents=True, exist_ok=True)
    for name, entry in manifest.items():
        # Same directory keeps replacement atomic and preserves the old model on failure.
        with tempfile.NamedTemporaryFile(dir=output, prefix=f'.{name}.', delete=False) as temporary:
            temporary_path = Path(temporary.name)
        try:
            shutil.copyfile(source_root / entry['experimentPath'], temporary_path)
            with temporary_path.open('rb') as copied:
                digest = hashlib.file_digest(copied, 'sha256').hexdigest()
            if digest != entry['sha256']:
                raise SystemExit(f'Model checksum mismatch: {name}')
            temporary_path.chmod(0o644)  # The non-root analysis container must read model assets.
            temporary_path.replace(output / name)
        finally:
            temporary_path.unlink(missing_ok=True)
        print(f'Verified {name}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((Path(__file__).resolve().parents[1] / 'model-checksums.json').read_text())
    prepare_models(args.source, args.output, manifest)


if __name__ == '__main__':
    main()
