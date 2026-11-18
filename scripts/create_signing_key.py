"""Create a local release key once. Never commit .signing or lose its backup."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
directory = root / '.signing'
if directory.exists():
    raise SystemExit('Refusing to replace existing .signing directory. Preserve the original release key.')
keytool = shutil.which('keytool')
if not keytool:
    raise SystemExit('A JDK with keytool is required.')
directory.mkdir(mode=0o700)
password = secrets.token_urlsafe(32)
password_file = directory / 'password'
password_file.write_text(password)
password_file.chmod(0o600)
keystore = directory / 'roadwatch-release.p12'
try:
    subprocess.run([keytool, '-genkeypair', '-keystore', str(keystore), '-storetype', 'PKCS12',
                    '-storepass:file', str(password_file), '-keypass:file', str(password_file),
                    '-alias', 'roadwatch', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=RoadWatch AI'], check=True)
    keystore.chmod(0o600)
    properties = directory / 'release.properties'
    properties.write_text(f'storeFile=.signing/roadwatch-release.p12\nstorePassword={password}\nkeyAlias=roadwatch\nkeyPassword={password}\n')
    properties.chmod(0o600)
finally:
    password_file.unlink(missing_ok=True)
print('Release key created in .signing. Back up that directory privately; it is required for future updates.')
