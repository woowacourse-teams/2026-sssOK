"""Input validation tests independent of the MCP server and analysis worker."""
from dataclasses import replace
from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import patch

import httpx
from pydantic import ValidationError

from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo, fingerprint, source_kind, stage_photos


class InputTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'input').mkdir()
        (self.root / 'input/p.jpg').write_bytes(b'test')
        self.settings = Settings(data=self.root / 'data', assets=self.root / 'models',
                                 token='t' * 32, input_root=self.root / 'input')
        self.photo = Photo(mediaId=1, source='p.jpg', extension='jpg')

    def test_source_paths_and_symlinks_cannot_escape(self):
        outside=self.root/'secret.jpg';outside.write_bytes(b'secret')
        (self.root/'input/link.jpg').symlink_to(outside)
        for source in ['../secret.jpg',str(outside),'link.jpg']:
            with self.assertRaises(ValueError):source_kind(self.photo.model_copy(update={'source':source}),self.settings)

    def test_local_input_disabled_by_default(self):
        with self.assertRaises(ValueError):source_kind(self.photo,replace(self.settings,input_root=None))

    def test_https_exact_host_no_credentials_or_other_ports(self):
        settings=replace(self.settings,download_hosts=('bucket.s3.amazonaws.com',))
        for source in ['http://bucket.s3.amazonaws.com/p','https://evil.test/p',
                       'https://bucket.s3.amazonaws.com.evil.test/p','https://u:p@bucket.s3.amazonaws.com/p',
                       'https://bucket.s3.amazonaws.com:8080/p','https://bucket.s3.amazonaws.com/p#x']:
            with self.assertRaises(ValueError):source_kind(self.photo.model_copy(update={'source':source}),settings)
        self.assertEqual(source_kind(self.photo.model_copy(update={'source':'https://bucket.s3.amazonaws.com/p?signature=x'}),settings),'https')

    def test_signed_url_query_refresh_keeps_fingerprint(self):
        p=self.photo.model_copy(update={'source':'https://bucket.test/p?a=1'})
        self.assertEqual(fingerprint([p]),fingerprint([p.model_copy(update={'source':'https://bucket.test/p?a=2'})]))

    def test_snapshot_copy_and_byte_limit(self):
        target=self.root/'copy';stage_photos([self.photo],target,self.settings)
        (self.root/'input/p.jpg').write_bytes(b'changed')
        self.assertEqual((target/self.photo.filename).read_bytes(),b'test')
        with self.assertRaises(ValueError):stage_photos([self.photo],self.root/'oversized',replace(self.settings,max_photo_bytes=1))

    def test_file_or_parent_replaced_after_validation_cannot_escape(self):
        outside = self.root / 'outside'
        outside.mkdir()
        (outside / 'p.jpg').write_bytes(b'secret')
        for parent in (False, True):
            with self.subTest(parent=parent):
                folder = self.root / 'input' / f'case-{parent}'
                folder.mkdir()
                (folder / 'p.jpg').write_bytes(b'photo')
                photo = self.photo.model_copy(update={'source': f'{folder.name}/p.jpg'})
                def swap_after_validation(photo, settings):
                    kind = source_kind(photo, settings)
                    if parent:
                        folder.rename(folder.with_name('original'))
                        folder.symlink_to(outside, target_is_directory=True)
                    else:
                        (folder / 'p.jpg').unlink()
                        (folder / 'p.jpg').symlink_to(outside / 'p.jpg')
                    return kind
                target = self.root / f'copy-{parent}'
                with patch('gallery_mcp.inputs.source_kind', side_effect=swap_after_validation):
                    with self.assertRaises(ValueError):
                        stage_photos([photo], target, self.settings)
                self.assertFalse((target / photo.filename).exists())

    def test_open_directory_is_pinned_during_parent_replacement(self):
        folder = self.root / 'input' / 'nested'
        folder.mkdir()
        (folder / 'p.jpg').write_bytes(b'photo')
        outside = self.root / 'outside'
        outside.mkdir()
        (outside / 'p.jpg').write_bytes(b'secret')
        photo = self.photo.model_copy(update={'source': 'nested/p.jpg'})
        original_open = os.open
        def swap_after_open(path, flags, **kwargs):
            descriptor = original_open(path, flags, **kwargs)
            if path == 'nested' and not folder.is_symlink():
                folder.rename(folder.with_name('original'))
                folder.symlink_to(outside, target_is_directory=True)
            return descriptor
        target = self.root / 'copy'
        with patch('gallery_mcp.inputs.source_kind', return_value='local'), \
                patch('gallery_mcp.inputs.os.open', side_effect=swap_after_open):
            stage_photos([photo], target, self.settings)
        self.assertEqual((target / photo.filename).read_bytes(), b'photo')

    def test_local_symlinks_and_special_files_rejected(self):
        (self.root / 'input/link.jpg').symlink_to('p.jpg')
        os.mkfifo(self.root / 'input/pipe.jpg')
        for source in ('link.jpg', 'pipe.jpg', '.', 'nested/../p.jpg'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                source_kind(self.photo.model_copy(update={'source': source}), self.settings)

    def test_storage_stream_copy_redirect_and_size_limit(self):
        settings=replace(self.settings,download_hosts=('bucket.test',))
        photo=self.photo.model_copy(update={'source':'https://bucket.test/p?signature=private'})
        original=httpx.Client
        for index,(status,body,limit,expected) in enumerate([
            (200,b'photo',100,False),(302,b'',100,True),(200,b'oversized',1,True),(403,b'',100,True)
        ]):
            handler=lambda request: httpx.Response(status,content=body,headers={'Location':'https://evil.test/p'})
            factory=lambda **kwargs: original(transport=httpx.MockTransport(handler),**kwargs)
            with patch('gallery_mcp.inputs.httpx.Client',side_effect=factory):
                target=self.root/f'download-{index}'
                if expected:
                    with self.assertRaises((ValueError,httpx.HTTPStatusError)):
                        stage_photos([photo],target,replace(settings,max_photo_bytes=limit))
                else:
                    stage_photos([photo],target,settings)
                    self.assertEqual((target/photo.filename).read_bytes(),body)

    def test_unexpected_fields_and_noninteger_media_rejected(self):
        with self.assertRaises(ValidationError):Photo(mediaId='1',source='p.jpg',extension='jpg')
        with self.assertRaises(ValidationError):Photo(mediaId=1,source='p.jpg',extension='jpg',command='x')
