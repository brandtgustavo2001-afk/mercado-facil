"""Upload the local signing key as encrypted GitHub Actions Secrets.

Requires PyNaCl and a Git Credential Manager session authorized for the repository.
No credentials or secret values are printed or committed.
"""
import base64
import json
from pathlib import Path
import subprocess
import urllib.request
import urllib.error
from nacl.public import PublicKey, SealedBox

repository = 'brandtgustavo2001-afk/mercado-facil'
secret_dir = Path.home() / '.codex' / 'secrets' / 'mercado-facil'
config = json.loads((secret_dir / 'signing.json').read_text(encoding='utf-8'))
credential = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n', text=True, capture_output=True, check=True, timeout=60)
fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
token = fields['password']

def api(path, value=None):
    request = urllib.request.Request('https://api.github.com/repos/' + repository + path,
        data=json.dumps(value).encode() if value is not None else None,
        headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json', 'User-Agent': 'MercadoFacil-Setup', 'X-GitHub-Api-Version': '2022-11-28'},
        method='PUT' if value is not None else 'GET')
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raise SystemExit(f'GitHub HTTP {error.code}. Verifique a permissão da credencial para Actions Secrets.') from None

public_key = api('/actions/secrets/public-key')
box = SealedBox(PublicKey(base64.b64decode(public_key['key'])))
values = {'KEYSTORE_BASE64': base64.b64encode((secret_dir / 'release.jks').read_bytes()).decode(),
          'SIGNING_STORE_PASSWORD': config['storePassword'], 'SIGNING_KEY_ALIAS': config['keyAlias'], 'SIGNING_KEY_PASSWORD': config['keyPassword']}
for name, value in values.items():
    api('/actions/secrets/' + name, {'encrypted_value': base64.b64encode(box.encrypt(value.encode())).decode(), 'key_id': public_key['key_id']})
    print('Secret configurado: ' + name)
