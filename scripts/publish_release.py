"""Publish a verified main-branch build with stable, mobile-friendly asset names."""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request


def request(url, method='GET', data=None, content_type='application/json', missing_ok=False):
    body = json.dumps(data).encode() if isinstance(data, dict) else data
    req = urllib.request.Request(url, data=body, method=method, headers={
        'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'Content-Type': content_type,
        'User-Agent': 'PocketMod-release',
    })
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        if error.code == 404 and missing_ok:
            return None
        # Never print headers or tokens in error logs.
        raise RuntimeError('GitHub request failed: HTTP %d (%s)' % (error.code, method)) from None


def publish():
    version = os.environ['RELEASE_VERSION']
    sha = os.environ['RELEASE_SHA']
    repo = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'\d+(?:\.\d+)+', version):
        raise ValueError('Invalid version.txt')
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Invalid commit SHA')
    base = 'https://api.github.com/repos/' + repo
    head = request(base + '/git/ref/heads/main')['object']['sha']
    if head != sha:
        print('A newer main commit exists; keeping the newer download.')
        return
    files = [Path('dist/pocketmod.jar'), Path('dist/pocketmod-source.zip'), Path('dist/SHA256SUMS')]
    for path in files:
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError('Missing build artifact: ' + path.name)
    # Check the actual payload before any public release is changed.
    expected = {line.split()[1]: line.split()[0] for line in files[2].read_text().splitlines()}
    for path in files[:2]:
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected.get(path.name):
            raise ValueError('Checksum mismatch: ' + path.name)
    tag = 'v' + version
    release = request(base + '/releases/tags/' + tag, missing_ok=True)
    if release is not None and not release['draft']:
        raise RuntimeError('Version %s is already published. Bump version.txt for a new build; published versions are never overwritten.' % version)
    if release is None:
        release = request(base + '/releases', 'POST', {
            'tag_name': tag, 'target_commitish': sha,
            'name': 'Pocket Mod ' + version,
            'body': 'Minecraft 1.12.2 / Forge.\n\nAutomatically built from commit ' + sha
                    + '.\n\nDownload pocketmod.jar directly; replace the previous Pocket Mod JAR in mods/.',
            'draft': True, 'prerelease': False,
        })
    # A failed upload leaves only a draft. Retrying can repair that same draft.
    assets = request(base + '/releases/%s/assets' % release['id'])
    for asset in assets:
        if asset['name'] in [path.name for path in files]:
            request(base + '/releases/assets/%s' % asset['id'], 'DELETE')
    upload_url = release['upload_url'].split('{', 1)[0]
    for path in files:
        request(upload_url + '?name=' + urllib.parse.quote(path.name), 'POST', path.read_bytes(), 'application/octet-stream')
    # Check again after uploads; a stale build must never become the latest release.
    if request(base + '/git/ref/heads/main')['object']['sha'] != sha:
        print('Main changed while uploading; release left as a draft.')
        return
    request(base + '/releases/%s' % release['id'], 'PATCH', {'draft': False, 'make_latest': 'true'})
    print('Download: https://github.com/' + repo + '/releases/latest/download/pocketmod.jar')


if __name__ == '__main__':
    publish()
