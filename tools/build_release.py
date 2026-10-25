"""Build with an existing local signing key, or create the app's first signing key.

Secrets stay outside the checkout in ~/.codex/secrets/mercado-facil.
Never delete this directory: future APK updates need the same key.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--version', default='1.0.12')
parser.add_argument('--code', type=int, default=10012)
args = parser.parse_args()

root = Path(__file__).resolve().parents[1]
secret_dir = Path.home() / '.codex' / 'secrets' / 'mercado-facil'
secret_dir.mkdir(parents=True, exist_ok=True)
credentials = secret_dir / 'signing.json'
keystore = secret_dir / 'release.jks'
jdk = Path(os.environ.get('JAVA_HOME', ''))
if not (jdk / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool')).exists():
    sys.exit('Defina JAVA_HOME para um JDK 17 ou superior.')
if not credentials.exists():
    if keystore.exists():
        sys.exit('Chave já existe sem signing.json; restaure as credenciais. Nenhum arquivo foi substituído.')
    password = secrets.token_urlsafe(40)
    credentials.write_text(json.dumps({'storePassword': password, 'keyPassword': password, 'keyAlias': 'mercado-facil'}), encoding='utf-8')
    if os.name == 'nt':
        subprocess.run(['icacls', str(secret_dir), '/inheritance:r', '/grant:r', f'{os.environ["USERDOMAIN"]}\\{os.environ["USERNAME"]}:(OI)(CI)F'], check=True, capture_output=True)
    else:
        secret_dir.chmod(0o700)
        credentials.chmod(0o600)
    env = dict(os.environ, MF_KEY_PASSWORD=password)
    subprocess.run([str(jdk / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool')), '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS', '-storepass:env', 'MF_KEY_PASSWORD', '-keypass:env', 'MF_KEY_PASSWORD', '-alias', 'mercado-facil', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=Mercado Facil, O=Mercado Facil, C=BR'], env=env, check=True, capture_output=True)
config = json.loads(credentials.read_text(encoding='utf-8'))
env = dict(os.environ, SIGNING_STORE_FILE=str(keystore), SIGNING_STORE_PASSWORD=config['storePassword'], SIGNING_KEY_PASSWORD=config['keyPassword'], SIGNING_KEY_ALIAS=config['keyAlias'])
wrapper = str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
subprocess.run([wrapper, 'assembleRelease', f'-PversionName={args.version}', f'-PversionCode={args.code}', '--console=plain'], cwd=root, env=env, check=True)
print('APK: app/build/outputs/apk/release/app-release.apk')
print('Guarde uma cópia segura da chave e das credenciais em: ' + str(secret_dir))
