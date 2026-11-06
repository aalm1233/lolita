#!/usr/bin/env python3
"""Private SQLite writer and read-only Square API. No Taobao/network collection.

uv run --with pillow python scripts/square/lan_service.py ingest --package file.zip
uv run --with pillow python scripts/square/lan_service.py serve --host 0.0.0.0
"""
from __future__ import annotations
import argparse
from contextlib import closing
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import re
import sqlite3
import sys
import tempfile
from urllib.parse import parse_qs, urlsplit
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'skills/taobao-shop-updates/scripts'))
from square_package import baseline, image_meta, encoded, require

DEFAULT_DATA = Path.home() / 'Library/Application Support/LolitaSquare'
ASSET = re.compile(r'images/([a-f0-9]{64})\.(jpg|jpeg|png|webp)')
IDENTITY = re.compile(r'[A-Za-z0-9_-]{1,100}')

def connection(data, readonly=False):
    path = Path(data) / 'square.sqlite3'
    db = sqlite3.connect(path.as_uri() + '?mode=ro' if readonly else str(path), uri=readonly, timeout=5)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    if readonly:
        db.execute('PRAGMA query_only=ON')
    return db

def initialize(data):
    data = Path(data).resolve()
    data.mkdir(parents=True, exist_ok=True)
    with closing(connection(data)) as db, db:
        db.execute('PRAGMA journal_mode=WAL')
        db.executescript('''
        CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS shops(id TEXT PRIMARY KEY, revision INTEGER NOT NULL, json TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS products(id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(id), revision INTEGER NOT NULL, json TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(id), revision INTEGER NOT NULL, json TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS event_products(event_id TEXT NOT NULL REFERENCES events(id), product_id TEXT NOT NULL REFERENCES products(id), PRIMARY KEY(event_id, product_id));
        CREATE TABLE IF NOT EXISTS assets(path TEXT PRIMARY KEY, json TEXT NOT NULL, thumbnail_json TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS changes(seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL, fingerprint TEXT NOT NULL, json TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS changes_event ON changes(event_id, seq);
        PRAGMA user_version=1;
        ''')
        db.execute("INSERT OR IGNORE INTO meta VALUES('serverId',?)", (str(uuid.uuid4()),))
    return data

def encode(value):
    return encoded(value).decode('utf-8')

def blob(data, relative):
    require(ASSET.fullmatch(relative) is not None, 'invalid asset id')
    path = Path(data) / 'blobs' / relative
    require(not path.is_symlink() and path.resolve().is_relative_to((Path(data) / 'blobs').resolve()), 'invalid asset path')
    return path

def atomic_bytes(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.pending-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name): os.unlink(name)

def put(db, table, row, shop_id=None):
    old = db.execute(f'SELECT revision,json FROM {table} WHERE id=?', (row['id'],)).fetchone()
    if old:
        if row['revision'] < old['revision']: return
        require(row['revision'] != old['revision'] or json.loads(old['json']) == row, 'same revision has conflicting content')
        previous = json.loads(old['json'])
        if table == 'products' and previous.get('listing') and row['revision'] > old['revision']:
            from datetime import datetime
            require(row.get('listing') is not None, 'updated product must preserve listing evidence')
            require(datetime.fromisoformat(row['listing']['observedAt']) >= datetime.fromisoformat(previous['listing']['observedAt']), 'stale listing evidence')
    if table == 'shops':
        db.execute('INSERT INTO shops VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,json=excluded.json', (row['id'], row['revision'], encode(row)))
    else:
        db.execute(f'INSERT INTO {table} VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET shop_id=excluded.shop_id,revision=excluded.revision,json=excluded.json',
                   (row['id'], shop_id, row['revision'], encode(row)))

def event_catalog(db, event_id):
    event = json.loads(db.execute('SELECT json FROM events WHERE id=?', (event_id,)).fetchone()[0])
    products = [json.loads(db.execute('SELECT json FROM products WHERE id=?', (pid,)).fetchone()[0]) for pid in event['productIds']]
    paths = sorted({i['path'] for p in products for i in p['images']})
    return dict(format='lolita.square', schemaVersion=1, packageId='square-service', revision=1,
                title='广场商品资料', capturedAt=max(p['capturedAt'] for p in products),
                shops=[json.loads(db.execute('SELECT json FROM shops WHERE id=?', (event['shopId'],)).fetchone()[0])],
                events=[event], products=products,
                assets=[json.loads(db.execute('SELECT json FROM assets WHERE path=?', (path,)).fetchone()[0]) for path in paths])

def ingest(data, package, check_android=True):
    # This adapter consumes the existing validated public contract. Collector evidence stays private.
    if check_android:
        from android_contract import check as android_check
        android_check(Path(package), ROOT)
    catalog = baseline(package)  # duplicate names, paths, SHA, compressed sizes and manifest bounds
    products = {p['id']: p for p in catalog['products']}
    shops = {s['id']: s for s in catalog['shops']}
    assets = {a['path']: a for a in catalog['assets']}
    require(len(products) == len(catalog['products']) and len(shops) == len(catalog['shops']), 'duplicate identity')
    data = initialize(data)
    # A local lock serializes writers, while API readers remain independent.
    import fcntl
    with (data / 'writer.lock').open('a') as lock, zipfile.ZipFile(package) as archive, closing(connection(data)) as db:
        fcntl.flock(lock, fcntl.LOCK_EX)
        from PIL import Image, ImageOps
        # Verify every source image before it can become a published reference.
        thumbs = {}
        for path, asset in assets.items():
            require(ASSET.fullmatch(path) is not None, 'invalid asset path')
            content = archive.read(path)
            dest = blob(data, path)
            with tempfile.TemporaryDirectory(prefix='square-image-') as temp:
                probe = Path(temp) / Path(path).name
                probe.write_bytes(content)
                require(image_meta(probe) == asset, 'image metadata mismatch')
            if not dest.is_file() or hashlib.sha256(dest.read_bytes()).hexdigest() != asset['sha256']:
                atomic_bytes(dest, content)
            with Image.open(io.BytesIO(content)) as original:
                image = ImageOps.exif_transpose(original).convert('RGBA')
                image.thumbnail((600, 800))
                buf = io.BytesIO(); image.save(buf, format='WEBP', quality=84)
                thumb = buf.getvalue()
                atomic_bytes(data / 'thumbnails' / (asset['sha256'] + '.webp'), thumb)
                thumbs[path] = dict(bytes=len(thumb), sha256=hashlib.sha256(thumb).hexdigest(), width=image.width, height=image.height)
        changed = 0
        for event in catalog['events']:
            require(IDENTITY.fullmatch(event['id']) is not None and event['shopId'] in shops, 'invalid event')
            require(event['productIds'] and all(pid in products for pid in event['productIds']), 'missing event products')
            with db:
                put(db, 'shops', shops[event['shopId']])
                affected = {event['id']}
                for pid in event['productIds']:
                    product = products[pid]
                    require(product['shopId'] == event['shopId'], 'shop mismatch')
                    put(db, 'products', product, product['shopId'])
                    affected.update(r[0] for r in db.execute('SELECT event_id FROM event_products WHERE product_id=?', (pid,)))
                    for img in product['images']:
                        db.execute('INSERT INTO assets VALUES(?,?,?) ON CONFLICT(path) DO UPDATE SET json=excluded.json,thumbnail_json=excluded.thumbnail_json',
                                   (img['path'], encode(assets[img['path']]), encode(thumbs[img['path']])))
                put(db, 'events', event, event['shopId'])
                # Use persisted references when incoming event is older.
                persisted = json.loads(db.execute('SELECT json FROM events WHERE id=?', (event['id'],)).fetchone()[0])
                db.execute('DELETE FROM event_products WHERE event_id=?', (event['id'],))
                db.executemany('INSERT INTO event_products VALUES(?,?)', [(event['id'], pid) for pid in persisted['productIds']])
                for eid in sorted(affected):
                    snapshot = event_catalog(db, eid)
                    fingerprint = hashlib.sha256(encoded(snapshot)).hexdigest()
                    last = db.execute('SELECT fingerprint FROM changes WHERE event_id=? ORDER BY seq DESC LIMIT 1', (eid,)).fetchone()
                    if not last or last[0] != fingerprint:
                        db.execute('INSERT INTO changes(event_id,fingerprint,json) VALUES(?,?,?)', (eid, fingerprint, encode(snapshot)))
                        changed += 1
        return dict(changed=changed, cursor=db.execute('SELECT COALESCE(MAX(seq),0) FROM changes').fetchone()[0],
                    products=db.execute('SELECT COUNT(*) FROM products').fetchone()[0], assets=db.execute('SELECT COUNT(*) FROM assets').fetchone()[0])

def handler(data):
    class Handler(BaseHTTPRequestHandler):
        server_version = 'LolitaSquare/1'
        def log_message(self, *_): pass  # URLs/queries and local paths do not enter access logs.
        def json(self, status, payload):
            content = encoded(payload)
            self.send_response(status); self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(content))); self.send_header('Cache-Control', 'no-store'); self.end_headers()
            if self.command != 'HEAD': self.wfile.write(content)
        def do_GET(self):
            try:
                u = urlsplit(self.path)
                query = parse_qs(u.query, strict_parsing=True)
                def integer(name, default):
                    value = query.get(name, [str(default)])
                    require(len(value) == 1 and re.fullmatch(r'[0-9]{1,18}', value[0]), 'invalid query')
                    return int(value[0])
                with closing(connection(data, True)) as db:
                    db.execute('BEGIN')
                    server_id = db.execute("SELECT value FROM meta WHERE key='serverId'").fetchone()[0]
                    if u.path == '/v1/health':
                        require(not query, 'invalid query')
                        return self.json(200, dict(format='lolita.square.api', schemaVersion=1, serverId=server_id))
                    if u.path == '/v1/square/changes':
                        require(set(query) <= {'after','limit'}, 'invalid query')
                        after, limit = integer('after', 0), integer('limit', 10)
                        require(1 <= limit <= 30, 'invalid limit')
                        high = db.execute('SELECT COALESCE(MAX(seq),0) FROM changes').fetchone()[0]
                        require(after <= high, 'cursor ahead of database')
                        rows = db.execute('SELECT seq,json FROM changes WHERE seq>? ORDER BY seq LIMIT ?', (after, limit)).fetchall()
                        cursor = rows[-1]['seq'] if rows else after
                        return self.json(200, dict(serverId=server_id, cursor=cursor, hasMore=cursor < high,
                                                   changes=[dict(seq=r['seq'], catalog=json.loads(r['json'])) for r in rows]))
                    if u.path in ('/v1/shops', '/v1/events'):
                        require(set(query) <= {'after','limit'}, 'invalid query')
                        limit = integer('limit', 30); require(1 <= limit <= 100, 'invalid limit')
                        after = query.get('after', [''])[0]
                        require(not after or IDENTITY.fullmatch(after), 'invalid identity')
                        table = 'shops' if u.path == '/v1/shops' else 'events'
                        rows = db.execute(f'SELECT id,json FROM {table} WHERE id>? ORDER BY id LIMIT ?', (after, limit+1)).fetchall()
                        return self.json(200, dict(items=[json.loads(r['json']) for r in rows[:limit]], next=rows[limit-1]['id'] if len(rows)>limit else None))
                    product = re.fullmatch(r'/v1/products/([A-Za-z0-9_-]{1,100})', u.path)
                    if product:
                        require(not query, 'invalid query')
                        row = db.execute('SELECT json FROM products WHERE id=?', (product[1],)).fetchone()
                        return self.json(200, json.loads(row[0])) if row else self.json(404, dict(error='not_found'))
                    image = re.fullmatch(r'/v1/images/([a-f0-9]{64})\.(jpg|jpeg|png|webp)', u.path)
                    if image:
                        require(set(query) <= {'variant'} and query.get('variant', ['original']) in (['original'], ['thumbnail']), 'invalid image variant')
                        path = f'images/{image[1]}.{image[2]}'
                        row = db.execute('SELECT json,thumbnail_json FROM assets WHERE path=?', (path,)).fetchone()
                        if not row: return self.json(404, dict(error='not_found'))
                        thumbnail = query.get('variant') == ['thumbnail']
                        meta = json.loads(row['thumbnail_json' if thumbnail else 'json'])
                        file = Path(data) / 'thumbnails' / (image[1]+'.webp') if thumbnail else blob(data, path)
                        require(file.is_file() and not file.is_symlink(), 'missing asset')
                        self.send_response(200); self.send_header('Content-Type', 'image/webp' if thumbnail else 'image/'+('jpeg' if image[2] in ('jpg','jpeg') else image[2]))
                        self.send_header('Content-Length', str(meta['bytes'])); self.send_header('X-Content-SHA256', meta['sha256'])
                        self.send_header('Cache-Control', 'public, max-age=86400'); self.end_headers()
                        if self.command != 'HEAD':
                            with file.open('rb') as f:
                                import shutil
                                shutil.copyfileobj(f, self.wfile, 65536)
                        return
                    self.json(404, dict(error='not_found'))
            except (ValueError, AssertionError, KeyError, TypeError):
                self.json(400, dict(error='invalid_request'))
            except (BrokenPipeError, ConnectionResetError): pass
            except Exception:
                self.json(503, dict(error='temporarily_unavailable'))
        do_HEAD = do_GET
        def reject(self): self.json(405, dict(error='read_only'))
        do_POST = do_PUT = do_PATCH = do_DELETE = reject
    return Handler

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--data', type=Path, default=DEFAULT_DATA)
    subs = p.add_subparsers(dest='command', required=True)
    writer = subs.add_parser('ingest'); writer.add_argument('--package', type=Path, required=True)
    server = subs.add_parser('serve'); server.add_argument('--host', default='127.0.0.1'); server.add_argument('--port', type=int, default=18765)
    args = p.parse_args()
    if args.command == 'ingest': print(json.dumps(ingest(args.data, args.package), ensure_ascii=False))
    else:
        data = initialize(args.data)
        httpd = ThreadingHTTPServer((args.host, args.port), handler(data))
        httpd.daemon_threads = True
        print(f'Lolita Square API listening on {args.host}:{args.port}', flush=True)
        try: httpd.serve_forever()
        except KeyboardInterrupt: pass
        finally: httpd.server_close()

if __name__ == '__main__': main()
