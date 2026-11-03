#!/usr/bin/env python3
"""Writes <dir>/manifest.json describing the CI screenshots in <dir>.

Files are named <screen>-<variant>.png (for example dashboard-dark.png); the landing page
reads the manifest to swap its placeholder previews for the real screenshots.

Usage: make-screenshot-manifest.py <screenshots-dir> [commit-sha]
"""
import datetime
import json
import pathlib
import re
import sys

PATTERN = re.compile(r"^(?P<screen>[a-z0-9]+)-(?P<variant>light|dark)\.png$")


def build(directory: pathlib.Path, commit):
    screens = {}
    for path in sorted(directory.glob("*.png")):
        match = PATTERN.match(path.name)
        if match:
            screens.setdefault(match["screen"], {})[match["variant"]] = path.name
    return {
        "generated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds") if screens else None,
        "commit": commit if screens else None,
        "screens": screens,
    }


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    directory = pathlib.Path(sys.argv[1])
    directory.mkdir(parents=True, exist_ok=True)
    manifest = build(directory, sys.argv[2] if len(sys.argv) > 2 else None)
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"{len(manifest['screens'])} screen(s) in {directory / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
