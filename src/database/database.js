import * as SQLite from 'expo-sqlite';

const db = SQLite.openDatabaseSync('notecraft.db');

export default db;
