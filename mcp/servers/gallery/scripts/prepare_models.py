"""Copy and verify existing experiment assets; does not download or publish model weights."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--source',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args = parser.parse_args()
manifest = json.loads((Path(__file__).resolve().parents[1]/'model-checksums.json').read_text())
args.output.mkdir(parents=True,exist_ok=True)
for name,entry in manifest.items():
    source = args.source / entry['experimentPath']
    if hashlib.sha256(source.read_bytes()).hexdigest() != entry['sha256']:
        raise SystemExit(f'Model checksum mismatch: {name}')
    shutil.copyfile(source,args.output/name)
    print(f'Verified {name}')
