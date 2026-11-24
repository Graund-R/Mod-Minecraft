import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).resolve().parents[2] / 'scripts/publish_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
SHA = 'a' * 40


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.old = os.getcwd()
        self.temp = tempfile.TemporaryDirectory()
        os.chdir(self.temp.name)
        Path('dist').mkdir()
        for name in ['pocketmod.jar', 'pocketmod-source.zip']:
            Path('dist', name).write_bytes(b'valid-test-payload')
        Path('dist/SHA256SUMS').write_text(''.join(hashlib.sha256(Path('dist', n).read_bytes()).hexdigest() + '  ' + n + '\n' for n in ['pocketmod.jar', 'pocketmod-source.zip']))
        self.env = patch.dict(os.environ, {'RELEASE_VERSION': '2.33', 'RELEASE_SHA': SHA, 'GITHUB_REPOSITORY': 'owner/repo'})
        self.env.start()

    def tearDown(self):
        self.env.stop()
        os.chdir(self.old)
        self.temp.cleanup()

    def fake(self, url, method='GET', data=None, *args, **kwargs):
        if '/git/ref/' in url:
            return {'object': {'sha': SHA}}
        if '/releases/tags/' in url:
            return None
        if method == 'GET' and url.endswith('/assets'):
            return []
        if method == 'POST' and url.endswith('/releases'):
            self.assertTrue(data['draft'])
            return {'id': 1, 'upload_url': 'https://uploads.github.com/assets{?name,label}'}
        return {}

    def test_publish_only_after_all_assets_uploaded(self):
        with patch.object(release, 'request', side_effect=self.fake) as req:
            release.publish()
        calls = req.call_args_list
        uploads = [c for c in calls if c.args[0].startswith('https://uploads.github.com/')]
        self.assertEqual(len(uploads), 3)
        self.assertEqual(calls[-1].args[1], 'PATCH')
        self.assertEqual(calls[-1].args[2], {'draft': False, 'make_latest': 'true'})

    def test_stale_commit_never_publishes(self):
        with patch.object(release, 'request', return_value={'object': {'sha': 'b' * 40}}) as req:
            release.publish()
        self.assertEqual(req.call_count, 1)

    def test_invalid_checksum_never_creates_release(self):
        Path('dist/pocketmod.jar').write_bytes(b'corrupt')
        with patch.object(release, 'request', side_effect=self.fake) as req:
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                release.publish()
        self.assertEqual(req.call_count, 1)

    def test_published_version_is_not_overwritten(self):
        def existing(url, *args, **kwargs):
            if '/releases/tags/' in url:
                return {'draft': False}
            return self.fake(url, *args, **kwargs)
        with patch.object(release, 'request', side_effect=existing) as req:
            with self.assertRaisesRegex(RuntimeError, 'already published'):
                release.publish()
        self.assertTrue(all(len(c.args) < 2 or c.args[1] == 'GET' for c in req.call_args_list))

    def test_failed_upload_leaves_draft(self):
        def fail(url, *args, **kwargs):
            if url.startswith('https://uploads.github.com/'):
                raise RuntimeError('upload unavailable')
            return self.fake(url, *args, **kwargs)
        with patch.object(release, 'request', side_effect=fail) as req:
            with self.assertRaisesRegex(RuntimeError, 'upload unavailable'):
                release.publish()
        self.assertFalse(any(len(c.args) > 1 and c.args[1] == 'PATCH' for c in req.call_args_list))
