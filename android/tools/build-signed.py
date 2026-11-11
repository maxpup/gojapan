#!/usr/bin/env python3
"""Build publishable APKs only with the pinned GoJapan signing identity."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', type=Path, help='Private JSON outside the repository; never print its contents')
parser.add_argument('tasks', nargs='+')
args = parser.parse_args()
env = dict(os.environ)
if args.config:
    config = json.loads(args.config.read_text())
    for name, field in [('STORE_FILE', 'storeFile'), ('STORE_PASSWORD', 'storePassword'),
                        ('KEY_ALIAS', 'keyAlias'), ('KEY_PASSWORD', 'keyPassword')]:
        env['GOJAPAN_SIGNING_' + name] = config[field]
required = ['STORE_FILE', 'STORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD']
if any(not env.get('GOJAPAN_SIGNING_' + name) for name in required):
    sys.exit('Missing fixed signing credentials. No temporary key will be generated.')
env['GOJAPAN_REQUIRE_FIXED_SIGNING'] = 'true'
expected = (root / 'signing-certificate.sha256').read_text().strip().lower()
keytool = str(Path(env['JAVA_HOME']) / 'bin/keytool') if env.get('JAVA_HOME') else 'keytool'
certificate = subprocess.run([
    keytool, '-exportcert', '-keystore', env['GOJAPAN_SIGNING_STORE_FILE'],
    '-storepass:env', 'GOJAPAN_SIGNING_STORE_PASSWORD', '-alias', env['GOJAPAN_SIGNING_KEY_ALIAS'],
], env=env, capture_output=True)
if certificate.returncode or hashlib.sha256(certificate.stdout).hexdigest() != expected:
    sys.exit('Signing certificate does not match the pinned GoJapan identity; build refused.')
print('Fixed signing certificate verified: ' + expected, flush=True)
result = subprocess.run([str(root / 'gradlew'), '-p', str(root), '--console=plain', *args.tasks], env=env)
if result.returncode:
    sys.exit(result.returncode)
sdk = Path(env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT') or '')
signers = sorted((sdk / 'build-tools').glob('*/apksigner'))
if not signers:
    sys.exit('apksigner not found; cannot verify publishable APK.')
for variant in ['debug', 'release']:
    if 'assemble' + variant.title() not in args.tasks:
        continue
    apk = root / f'app/build/outputs/apk/{variant}/app-{variant}.apk'
    result = subprocess.run([str(signers[-1]), 'verify', '--print-certs', str(apk)], env=env, capture_output=True, text=True)
    fingerprints = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', result.stdout)
    if result.returncode or fingerprints != [expected]:
        sys.exit(f'{variant} APK signature verification failed; do not publish.')
    print(f'{variant} APK signature matches the fixed GoJapan certificate.', flush=True)
