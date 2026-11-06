import copy
import hashlib
from http.server import ThreadingHTTPServer
import io
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import zipfile
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import lan_service as service


class LanServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.data = self.root / 'data'
        image = io.BytesIO(); Image.new('RGB', (80, 120), 'red').save(image, format='PNG')
        self.image = image.getvalue(); sha = hashlib.sha256(self.image).hexdigest()
        self.path = f'images/{sha}.png'
        self.asset = dict(path=self.path, sha256=sha, bytes=len(self.image), width=80, height=120)
        self.catalog = dict(format='lolita.square', schemaVersion=1, packageId='test', revision=1, title='测试', capturedAt='2026-11-06',
            shops=[dict(id='shop', name='测试店', revision=1)],
            products=[dict(id='p', shopId='shop', revision=1, title='测试裙', url='https://item.taobao.com/item.htm?id=1',
                capturedAt='2026-11-06', pricePhase='FULL', priceType='全款', skus=[], info={},
                images=[dict(path=self.path, role='main', order=0, url='https://example.com/public.png')])],
            events=[dict(id='event', shopId='shop', revision=1, designId='design', batchId='batch', title='测试上新',
                subtitle='', kind='上新', state='在售', date='日期未知', price='未知', priceNote='', note='', material='', detail='', captured='2026-11-06', productIds=['p'])],
            assets=[self.asset])

    def tearDown(self): self.temp.cleanup()

    def ingest(self, catalog=None, content=None):
        package = self.root / 'test.zip'
        with zipfile.ZipFile(package, 'w') as z:
            z.writestr('manifest.json', json.dumps(catalog or self.catalog, ensure_ascii=False))
            z.writestr(self.path, content or self.image)
        return service.ingest(self.data, package, check_android=False)

    def test_idempotent_and_updates_have_monotonic_cursor(self):
        self.assertEqual(1, self.ingest()['changed'])
        self.assertEqual(0, self.ingest()['changed'])
        changed = copy.deepcopy(self.catalog); changed['products'][0].update(revision=2, title='修正版')
        self.assertEqual(2, self.ingest(changed)['cursor'])
        self.assertEqual(2, self.ingest()['cursor'])
        with service.connection(self.data) as db:
            self.assertEqual('修正版', json.loads(db.execute('SELECT json FROM products').fetchone()[0])['title'])

    def test_conflicting_revision_rolls_back_and_old_snapshot_stays_readable(self):
        self.ingest()
        changed = copy.deepcopy(self.catalog); changed['products'][0]['title'] = '冲突'
        with self.assertRaises(Exception): self.ingest(changed)
        with service.connection(self.data) as db:
            self.assertEqual(1, db.execute('SELECT COUNT(*) FROM changes').fetchone()[0])
            self.assertEqual('测试裙', json.loads(db.execute('SELECT json FROM products').fetchone()[0])['title'])

    def test_corrupt_image_cannot_publish(self):
        with self.assertRaises(Exception): self.ingest(content=b'broken')
        self.assertFalse((self.data/'square.sqlite3').exists())

    def test_absent_event_is_retained_and_readers_do_not_block_writer(self):
        self.ingest()
        other = copy.deepcopy(self.catalog); other['events'][0]['id'] = 'other'
        reader = service.connection(self.data, True); reader.execute('BEGIN')
        self.assertEqual(1, reader.execute('SELECT COUNT(*) FROM events').fetchone()[0])
        self.ingest(other)
        self.assertEqual(1, reader.execute('SELECT COUNT(*) FROM events').fetchone()[0]); reader.close()
        with service.connection(self.data, True) as db:
            self.assertEqual(2, db.execute('SELECT COUNT(*) FROM events').fetchone()[0])
            with self.assertRaises(sqlite3.OperationalError): db.execute('DELETE FROM events')

    def test_archived_listing_cannot_be_resurrected_by_missing_or_older_evidence(self):
        archived=copy.deepcopy(self.catalog)
        archived['products'][0]['listing']=dict(status='OFF_SALE',source='公开页面',observedAt='2026-11-06T20:00:00+08:00')
        self.ingest(archived)
        missing=copy.deepcopy(self.catalog);missing['products'][0]['revision']=2
        with self.assertRaises(Exception):self.ingest(missing)
        stale=copy.deepcopy(archived);stale['products'][0]['revision']=2
        stale['products'][0]['listing'].update(status='ACTIVE',observedAt='2026-11-05T20:00:00+08:00')
        with self.assertRaises(Exception):self.ingest(stale)
        active=copy.deepcopy(archived);active['products'][0]['revision']=2
        active['products'][0]['listing'].update(status='ACTIVE',observedAt='2026-11-07T20:00:00+08:00')
        self.assertEqual(2,self.ingest(active)['cursor'])

    def test_only_business_routes_and_incremental_snapshot_pagination(self):
        self.ingest()
        self.httpd = ThreadingHTTPServer(('127.0.0.1', 0), service.handler(self.data))
        thread = threading.Thread(target=self.httpd.serve_forever, daemon=True); thread.start()
        base = f'http://127.0.0.1:{self.httpd.server_port}'
        def get(path):
            with urlopen(base+path, timeout=3) as r: return json.load(r)
        try:
            self.assertEqual('lolita.square.api', get('/v1/health')['format'])
            page = get('/v1/square/changes?after=0&limit=1')
            self.assertEqual(1, page['cursor']); self.assertFalse(page['hasMore'])
            updated = copy.deepcopy(self.catalog); updated['products'][0].update(revision=2, title='更新')
            self.ingest(updated)
            self.assertEqual('测试裙', get('/v1/square/changes?after=0&limit=1')['changes'][0]['catalog']['products'][0]['title'])
            self.assertEqual('更新', get('/v1/square/changes?after=1')['changes'][0]['catalog']['products'][0]['title'])
            self.assertEqual([], get('/v1/square/changes?after=2')['changes'])
            self.assertEqual('更新', get('/v1/products/p')['title'])
            self.assertEqual('shop', get('/v1/shops')['items'][0]['id'])
            for path in ('/', '/square.sqlite3', '/blobs/', '/v1/images/../../secret', '/v1/admin'):
                with self.assertRaises(HTTPError) as error: get(path)
                self.assertEqual(404, error.exception.code)
            for path in ('/v1/square/changes?after=999', '/v1/square/changes?after=-1', '/v1/events?limit=1000'):
                with self.assertRaises(HTTPError) as error: get(path)
                self.assertEqual(400, error.exception.code)
            with self.assertRaises(HTTPError) as error: urlopen(Request(base+'/v1/events', data=b'{}', method='POST'))
            self.assertEqual(405, error.exception.code)
            for suffix in ('', '?variant=thumbnail'):
                with urlopen(base+'/v1/'+self.path+suffix) as response:
                    content = response.read()
                    self.assertEqual(response.headers['X-Content-SHA256'], hashlib.sha256(content).hexdigest())
            with urlopen(base+'/v1/'+self.path) as response: self.assertEqual(self.image, response.read())
        finally:
            self.httpd.shutdown(); self.httpd.server_close(); thread.join(3)

if __name__ == '__main__': unittest.main()
