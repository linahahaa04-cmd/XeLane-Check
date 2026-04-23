"""One-off: copy Kotlin sources from legacy folder to com/zunguwu/XeLane."""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/kododake/aabrowser"
DST = ROOT / "app/src/main/java/com/zunguwu/XeLane"
LOG = ROOT / "tools/copy_pkg_log.txt"


def main() -> None:
    lines: list[str] = []
    if not SRC.is_dir():
        lines.append(f"MISSING_SRC: {SRC}")
        LOG.write_text("\n".join(lines), encoding="utf-8")
        raise SystemExit(1)

    DST.mkdir(parents=True, exist_ok=True)
    kt = 0
    for path in SRC.rglob("*"):
        rel = path.relative_to(SRC)
        target = DST / rel
        if path.is_dir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        if path.suffix == ".kt":
            kt += 1

    lines.append(f"OK copied kotlin files: {kt}")
    lines.append(f"DEST: {DST}")
    LOG.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
