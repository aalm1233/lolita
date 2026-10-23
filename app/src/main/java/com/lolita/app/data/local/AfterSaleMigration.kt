package com.lolita.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AfterSaleMigration {
    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS item_reference_prices (item_id INTEGER NOT NULL PRIMARY KEY, amount REAL NOT NULL, note TEXT NOT NULL, revision INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS order_refunds (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, price_id INTEGER NOT NULL, amount REAL NOT NULL, kind TEXT NOT NULL, received_at INTEGER NOT NULL, note TEXT NOT NULL, operation_id TEXT NOT NULL, created_at INTEGER NOT NULL, voided_at INTEGER, FOREIGN KEY(price_id) REFERENCES prices(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_refunds_price_id ON order_refunds(price_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_order_refunds_operation_id ON order_refunds(operation_id)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS order_cancellations (price_id INTEGER NOT NULL PRIMARY KEY, expected_refund_total REAL, cancelled_at INTEGER NOT NULL, note TEXT NOT NULL, revision INTEGER NOT NULL, FOREIGN KEY(price_id) REFERENCES prices(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        }
    }
}
