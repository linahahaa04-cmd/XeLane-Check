"""One-shot: copy remaining kododake sources into com.zunguwu.XeLane, move tests, remove kododake."""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "kododake" / "aabrowser"
DST_PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "zunguwu" / "XeLane"


def main() -> None:
    DST_PKG.mkdir(parents=True, exist_ok=True)
    (DST_PKG / "settings").mkdir(parents=True, exist_ok=True)

    pairs = [
        (SRC_PKG / "MainActivity.kt", DST_PKG / "MainActivity.kt"),
        (SRC_PKG / "settings" / "SettingsViews.kt", DST_PKG / "settings" / "SettingsViews.kt"),
    ]
    for src, dst in pairs:
        if src.exists():
            shutil.copy2(src, dst)
            print(f"copied {src.relative_to(ROOT)} -> {dst.relative_to(ROOT)}")

    for folder in ("web", "data"):
        s = SRC_PKG / folder
        d = DST_PKG / folder
        if s.is_dir():
            shutil.copytree(s, d, dirs_exist_ok=True)
            print(f"merged {s.relative_to(ROOT)} -> {d.relative_to(ROOT)}")

    dst_test = ROOT / "app" / "src" / "test" / "java" / "com" / "zunguwu" / "XeLane"
    dst_android = ROOT / "app" / "src" / "androidTest" / "java" / "com" / "zunguwu" / "XeLane"
    dst_test.mkdir(parents=True, exist_ok=True)
    dst_android.mkdir(parents=True, exist_ok=True)

    src_test = ROOT / "app" / "src" / "test" / "java" / "com" / "kododake" / "aabrowser" / "ExampleUnitTest.kt"
    src_android = ROOT / "app" / "src" / "androidTest" / "java" / "com" / "kododake" / "aabrowser" / "ExampleInstrumentedTest.kt"
    if src_test.exists():
        shutil.copy2(src_test, dst_test / "ExampleUnitTest.kt")
        print(f"copied test {src_test.name}")
    if src_android.exists():
        shutil.copy2(src_android, dst_android / "ExampleInstrumentedTest.kt")
        print(f"copied androidTest {src_android.name}")

    # Remove kododake trees
    kododake_roots = [
        ROOT / "app" / "src" / "main" / "java" / "com" / "kododake",
        ROOT / "app" / "src" / "test" / "java" / "com" / "kododake",
        ROOT / "app" / "src" / "androidTest" / "java" / "com" / "kododake",
    ]
    for tree in kododake_roots:
        if tree.exists():
            shutil.rmtree(tree)
            print(f"removed {tree.relative_to(ROOT)}")

    print("done")


if __name__ == "__main__":
    main()
