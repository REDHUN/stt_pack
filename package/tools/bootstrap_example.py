#!/usr/bin/env python3
"""Generate Flutter-version-matched Android host files without replacing demo code."""
from pathlib import Path
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
flutter = shutil.which('flutter')
if not flutter:
    sys.exit('Flutter is not on PATH. Install/configure Flutter, then run this again.')
example = root / 'example'
protected = [example / 'pubspec.yaml', *(example / 'lib').rglob('*.dart')]
originals = {path: path.read_bytes() for path in protected}
try:
    subprocess.run([
        flutter, 'create', '--platforms=android', '--org', 'com.redhun',
        '--project-name', 'my_stt_recorder_example', '--no-pub', str(example),
    ], check=True, cwd=root)
finally:
    for path, data in originals.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
# Generated default counter-app tests do not apply to this demo.
generated_test = example / 'test' / 'widget_test.dart'
if generated_test.exists() and 'counter' in generated_test.read_text().lower():
    generated_test.unlink()
for name in ('build.gradle.kts', 'build.gradle'):
    gradle = example / 'android' / 'app' / name
    if not gradle.exists():
        continue
    source = gradle.read_text()
    pattern = r'\bminSdk(?:Version)?\s*(?:=\s*)?(?:flutter\.minSdkVersion|\d+)'
    replacement = 'minSdk = 33' if name.endswith('.kts') else 'minSdkVersion 33'
    updated, count = re.subn(pattern, replacement, source)
    if count != 1:
        sys.exit(f'Please set minSdk to 33 manually in {gradle}; unexpected template.')
    gradle.write_text(updated)
print('Android host generated. Next: cd example, flutter pub get, flutter run')
