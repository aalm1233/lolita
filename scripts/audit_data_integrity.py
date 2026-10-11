#!/usr/bin/env python3
"""Read-only audit of checked-in migration SQL using disposable SQLite databases.

This interprets literal SQL and preservation helper calls. Android/Room tests are authoritative.
Exit 1 means a data preservation check failed; exit 2 means the harness failed.
"""
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / 'app/schemas/com.lolita.app.data.local.LolitaDatabase'
SOURCE = ROOT / 'app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt'


def create_schema(version):
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    schema = json.loads((SCHEMAS / f'{version}.json').read_text())['database']
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db, {e['tableName']: e for e in schema['entities']}


def seed(db, entities, table):
    columns, values = [], []
    required = {row[1]: bool(row[3] or row[5]) for row in db.execute(f'PRAGMA table_info(`{table}`)')}
    for field in entities[table]['fields']:
        columns.append(field['columnName'])
        name = field['columnName']
        if not required[name]:
            value = None
        elif field['affinity'] in ('INTEGER', 'REAL'):
            value = 1
        else:
            value = {'status': 'OWNED', 'priority': 'MEDIUM', 'type': 'FULL',
                     'image_urls': '[]', 'colors': '[]', 'group': 'CLOTHING'}.get(name, '审计样本')
        values.append(value)
    quoted = ','.join(f'`{c}`' for c in columns)
    db.execute(f'INSERT INTO `{table}` ({quoted}) VALUES ({",".join("?" for _ in values)})', values)


def run_migration(db, start, end):
    source = SOURCE.read_text()
    block = re.split(rf'(?:private|internal) val MIGRATION_{start}_{end} =', source, maxsplit=1)[1]
    block = re.split(r'\n        (?:private|internal) val MIGRATION_|\n        fun getDatabase', block, maxsplit=1)[0]
    pattern = (r'db\.execSQL\(\s*(?:"""([\s\S]*?)"""|"([^"\n]*)")'
               r'|MigrationDataGuard\.(snapshotChildren|restoreChildren)\(db(?:, savedChildren)?\)'
               r'|MigrationDataGuard\.(snapshot|restore)\(db, "([^"]+)"\)'
               r'|LegacyCatalogMigration\.(createArchive)\(db\)'
               r'|LegacyCatalogMigration\.(archive)\(db, "([^"]+)", "([^"]+)"\)')
    saved = []
    def snapshot(table):
        db.execute(f'CREATE TEMP TABLE integrity_{table} AS SELECT * FROM `{table}`')
    def restore(table):
        db.execute(f'DELETE FROM `{table}`')
        db.execute(f'INSERT INTO `{table}` SELECT * FROM integrity_{table}')
        db.execute(f'DROP TABLE integrity_{table}')
    for match in re.finditer(pattern, block):
        triple, single, children, action, table, create, archive, old_table, key = match.groups()
        if triple or single:
            sql = triple or single
            if '$' in sql:
                raise ValueError('Unsupported SQL interpolation')
            db.execute(sql)
        elif children == 'snapshotChildren':
            saved = [t for t in ['prices', 'payments', 'outfit_item_cross_ref', 'catalog_entries']
                     if db.execute("SELECT 1 FROM sqlite_master WHERE name=?", (t,)).fetchone()]
            for t in saved: snapshot(t)
        elif children == 'restoreChildren':
            for t in reversed(saved): db.execute(f'DELETE FROM `{t}`')
            for t in saved: restore(t)
        elif action == 'snapshot': snapshot(table)
        elif action == 'restore': restore(table)
        elif create:
            db.execute('CREATE TABLE IF NOT EXISTS legacy_catalog_archive (source_table TEXT NOT NULL, record_key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(source_table, record_key))')
        elif archive:
            cursor = db.execute(f'SELECT * FROM `{old_table}`')
            names = [col[0] for col in cursor.description]
            for values in cursor.fetchall():
                row = dict(zip(names, values))
                db.execute('INSERT INTO legacy_catalog_archive VALUES (?,?,?)', (old_table, str(row[key]), json.dumps(row, ensure_ascii=False)))


def check_migration(start, end, tables):
    db, entities = create_schema(start)
    for table in tables:
        seed(db, entities, table)
    db.commit()
    before = {t: db.execute(f'SELECT COUNT(*) FROM `{t}`').fetchone()[0] for t in tables}
    error = None
    try:
        db.execute('BEGIN')
        run_migration(db, start, end)
        db.commit()
    except sqlite3.Error as exc:
        error = str(exc)
        db.rollback()
    after = {}
    for table in tables:
        exists = db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone()
        after[table] = db.execute(f'SELECT COUNT(*) FROM `{table}`').fetchone()[0] if exists else db.execute('SELECT COUNT(*) FROM legacy_catalog_archive WHERE source_table=?', (table,)).fetchone()[0]
    result = {'case': f'migration_{start}_{end}_foreign_keys_on', 'before': before,
              'after': after, 'error': error, 'preserved': error is None and before == after}
    db.close()
    return result


def check_wal_copy():
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / 'live.db'
        db = sqlite3.connect(path)
        db.execute('PRAGMA journal_mode=WAL')
        db.execute('PRAGMA wal_autocheckpoint=0')
        db.execute('CREATE TABLE evidence (id INTEGER PRIMARY KEY)')
        db.commit()
        db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
        db.execute('INSERT INTO evidence VALUES (1)')
        db.commit()
        copy = Path(directory) / 'copy.db'
        shutil.copyfile(path, copy)
        snapshot = sqlite3.connect(copy)
        actual = snapshot.execute('SELECT COUNT(*) FROM evidence').fetchone()[0]
        snapshot.close()
        db.close()
        return {'case': 'production_does_not_copy_live_main_file', 'unsafe_copy_before': 1, 'unsafe_copy_after': actual,
                'preserved': 'pre_import_bak' not in (ROOT / 'app/src/main/java/com/lolita/app/data/file/BackupManager.kt').read_text()}


def main():
    results = [check_migration(13, 14, ['brands', 'categories', 'items', 'prices', 'payments']),
               check_migration(17, 18, ['brands', 'categories', 'items', 'prices', 'payments',
                                        'outfit_logs', 'outfit_item_cross_ref', 'catalog_entries']),
               check_migration(18, 19, ['catalog_entries', 'remote_catalog_entries']),
               check_wal_copy()]
    print(json.dumps(results, ensure_ascii=False, indent=2))
    return int(any(not result['preserved'] for result in results))


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, IndexError, sqlite3.Error) as exc:
        print(f'Audit harness error: {exc}')
        raise SystemExit(2)
