import json
import os
from pathlib import Path

MANIFEST_FILE = "manifest.json"


def compute_manifest(data_dir: str) -> dict:
    """Scans the data directory and creates a snapshot of file sizes and modification times."""
    data_path = Path(data_dir).resolve()
    files = {}
    for f in sorted(data_path.rglob("*")):
        # Ignore hidden lock files (like those created by Excel)
        if f.is_file() and not f.name.startswith(".~lock."):
            rel = str(f.relative_to(data_path))
            stat = f.stat()
            files[rel] = {"size": stat.st_size, "mtime": stat.st_mtime}
    return {"files": files}


def load_manifest(persist_dir: str) -> dict | None:
    """Loads the previously saved manifest from the database folder."""
    path = os.path.join(persist_dir, MANIFEST_FILE)
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


def save_manifest(persist_dir: str, data_dir: str):
    """Computes the current state of the data folder and saves it to the database folder."""
    manifest = compute_manifest(data_dir)
    os.makedirs(persist_dir, exist_ok=True)
    with open(os.path.join(persist_dir, MANIFEST_FILE), "w") as f:
        json.dump(manifest, f, indent=2)


def needs_rebuild(persist_dir: str, data_dir: str) -> bool:
    """Compares the current folder state against the saved manifest. Returns True if anything changed."""
    current = compute_manifest(data_dir)
    saved = load_manifest(persist_dir)
    return saved != current