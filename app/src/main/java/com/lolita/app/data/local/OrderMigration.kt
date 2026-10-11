package com.lolita.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object OrderMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        // Rebuild only items to add the self FK; keep all dependent rows during DROP.
        val children = MigrationDataGuard.snapshotChildren(db)
        run {
            db.execSQL("""CREATE TABLE items_new (
                brand_id INTEGER NOT NULL, category_id INTEGER NOT NULL, created_at INTEGER NOT NULL,
                description TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                image_urls TEXT NOT NULL DEFAULT '[]', coordinate_order INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL, priority TEXT NOT NULL DEFAULT 'MEDIUM', status TEXT NOT NULL,
                coordinate_id INTEGER, colors TEXT NOT NULL DEFAULT '[]', season TEXT, style TEXT,
                size TEXT, size_chart_image_url TEXT, location_id INTEGER, source TEXT, updated_at INTEGER NOT NULL,
                parent_item_id INTEGER, included_in_parent_price INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(coordinate_id) REFERENCES coordinates(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(brand_id) REFERENCES brands(id) ON DELETE RESTRICT,
                FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE RESTRICT,
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE SET NULL,
                FOREIGN KEY(parent_item_id) REFERENCES items(id) ON DELETE RESTRICT
            )""")
            db.execSQL("""INSERT INTO items_new (brand_id,category_id,created_at,description,id,image_urls,coordinate_order,name,priority,status,coordinate_id,colors,season,style,size,size_chart_image_url,location_id,source,updated_at)
                SELECT brand_id,category_id,created_at,description,id,image_urls,coordinate_order,name,priority,status,coordinate_id,colors,season,style,size,size_chart_image_url,location_id,source,updated_at FROM items""")
            db.execSQL("DROP TABLE items")
            db.execSQL("ALTER TABLE items_new RENAME TO items")
            listOf("name", "coordinate_id", "brand_id", "category_id", "status", "priority", "location_id", "parent_item_id").forEach {
                db.execSQL("CREATE INDEX index_items_$it ON items($it)")
            }
        }
        MigrationDataGuard.restoreChildren(db, children)
        db.execSQL("ALTER TABLE prices ADD COLUMN shipping REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN stage_id INTEGER")
        db.execSQL("""CREATE TABLE order_bindings(item_id INTEGER NOT NULL PRIMARY KEY, price_id INTEGER NOT NULL,
            FOREIGN KEY(item_id) REFERENCES items(id) ON DELETE CASCADE,
            FOREIGN KEY(price_id) REFERENCES prices(id) ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX index_order_bindings_price_id ON order_bindings(price_id)")
        db.execSQL("""CREATE TABLE payment_stages(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, price_id INTEGER NOT NULL,
            name TEXT NOT NULL, amount REAL NOT NULL, due_date INTEGER, reminder_set INTEGER NOT NULL DEFAULT 0,
            custom_reminder_days INTEGER, calendar_event_id INTEGER,
            FOREIGN KEY(price_id) REFERENCES prices(id) ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX index_payment_stages_price_id ON payment_stages(price_id)")
        db.execSQL("""INSERT INTO order_bindings SELECT item_id,MIN(id) FROM prices GROUP BY item_id
            HAVING COUNT(*)=1 AND MIN(total_price)>0 AND MAX(total_price)<90000000000000
            AND MIN(total_price)=ROUND(MIN(total_price),2)
            AND NOT EXISTS(SELECT 1 FROM payments p
                WHERE p.price_id IN (SELECT candidate.id FROM prices candidate WHERE candidate.item_id=prices.item_id)
                AND (p.amount<=0 OR p.amount>=90000000000000 OR p.amount<>ROUND(p.amount,2)))
            AND NOT EXISTS(SELECT 1 FROM items i WHERE i.id=prices.item_id AND i.status='WISHED'
                AND NOT EXISTS(SELECT 1 FROM payments p JOIN prices candidate ON candidate.id=p.price_id
                    WHERE candidate.item_id=i.id AND p.is_paid=1))""")
        db.execSQL("""INSERT INTO payment_stages(id,price_id,name,amount,due_date,reminder_set,custom_reminder_days)
            SELECT id,price_id,'历史付款 '||id,amount,due_date,reminder_set,custom_reminder_days FROM payments""")
        db.execSQL("UPDATE payments SET stage_id=id")
    }

    fun migratePurchaseRecordStatus(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE order_bindings ADD COLUMN purchase_record_status TEXT NOT NULL DEFAULT 'TRACKED'")
        // An explicit historical OWNED status survives even if old plans or incomplete
        // actual payment records remain. Never invent the missing payment to balance it.
        db.execSQL("""UPDATE order_bindings SET purchase_record_status='HISTORICAL_OWNED'
            WHERE (SELECT status FROM items WHERE id=order_bindings.item_id)='OWNED'
            AND COALESCE((SELECT SUM(ROUND(p.amount*100)) FROM payments p
                WHERE p.price_id=order_bindings.price_id AND p.is_paid=1),0)
                < (SELECT ROUND(total_price*100) FROM prices WHERE id=order_bindings.price_id)""")
        // Already-refreshed 21 records with no ledger cannot prove ownership or a debt.
        db.execSQL("""UPDATE order_bindings SET purchase_record_status='NEEDS_REVIEW'
            WHERE (SELECT status FROM items WHERE id=order_bindings.item_id)<>'OWNED'
            AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.price_id=order_bindings.price_id)
            AND NOT EXISTS(SELECT 1 FROM payment_stages st WHERE st.price_id=order_bindings.price_id)""")
    }
}
