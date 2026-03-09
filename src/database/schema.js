import db from './database';

export const setupDatabaseAsync = async () => {
  try {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS notes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        content TEXT NOT NULL,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        pinned INTEGER DEFAULT 0,
        tag TEXT
      );

      CREATE TABLE IF NOT EXISTS lists (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS list_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        list_id INTEGER NOT NULL,
        text TEXT NOT NULL,
        completed INTEGER DEFAULT 0,
        FOREIGN KEY(list_id) REFERENCES lists(id) ON DELETE CASCADE
      );

      CREATE TABLE IF NOT EXISTS reminders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        datetime TEXT NOT NULL,
        created_at TEXT NOT NULL
      );
    `);

    // Add missing columns if upgrading from an older schema where they didn't exist
    try { await db.execAsync('ALTER TABLE notes ADD COLUMN pinned INTEGER DEFAULT 0;'); } catch (e) {}
    try { await db.execAsync('ALTER TABLE notes ADD COLUMN tag TEXT;'); } catch (e) {}

    // Drop legacy FTS triggers if they exist to unlock database
    try {
      await db.execAsync(`
        DROP TRIGGER IF EXISTS notes_ai;
        DROP TRIGGER IF EXISTS notes_ad;
        DROP TRIGGER IF EXISTS notes_au;
        DROP TABLE IF EXISTS notes_fts;
      `);
    } catch (e) {
      console.log('Error dropping legacy tables:', e);
    }
  } catch (error) {
    console.error('Database locked or failed during setup:', error);
  }
};
