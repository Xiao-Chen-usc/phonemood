"""Create a private local upload key once; never overwrite existing credentials."""
from pathlib import Path
import os
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / '.signing'
folder.mkdir(mode=0o700, exist_ok=True)
folder.chmod(0o700)
password_file = folder / 'upload-password.txt'
key_file = folder / 'phonemood-upload.p12'
if not password_file.exists():
    if key_file.exists():
        raise SystemExit('Existing key has no password file; restore its password before continuing.')
    fd = os.open(password_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(secrets.token_urlsafe(36) + '\n')
if not key_file.exists():
    subprocess.run([
        'keytool', '-genkeypair', '-keystore', str(key_file),
        '-storetype', 'PKCS12', '-storepass:file', str(password_file),
        '-alias', 'phonemood-upload', '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '10000', '-dname', 'CN=PhoneMood Upload',
    ], check=True)
key_file.chmod(0o600)
print('Upload key ready in .signing; back up this folder securely.')
