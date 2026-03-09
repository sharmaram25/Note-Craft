import { create } from 'zustand';
import db from '../database/database';
import dayjs from 'dayjs';

export const useStore = create((set, get) => ({
  notes: [],
  lists: [],
  reminders: [],
  searchQuery: '',

  // Notes
  loadNotes: async () => {
    const notes = await db.getAllAsync('SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC');
    set({ notes });
  },

  searchNotes: async (query) => {
    set({ searchQuery: query });
    if (!query) {
      await get().loadNotes();
      return;
    }
    // Standard SQL Search
    const results = await db.getAllAsync(
      `SELECT * FROM notes 
       WHERE title LIKE '%' || ? || '%' 
       OR content LIKE '%' || ? || '%'
       ORDER BY pinned DESC, updated_at DESC`,
      [query, query]
    );
    set({ notes: results });
  },

  addNote: async (title, content, tag) => {
    const now = dayjs().toISOString();
    const result = await db.runAsync(
      'INSERT INTO notes (title, content, created_at, updated_at, pinned, tag) VALUES (?, ?, ?, ?, 0, ?)',
      [title, content, now, now, tag || null]
    );
    await get().loadNotes();
    return result.lastInsertRowId;
  },

  updateNote: async (id, title, content, tag) => {
    const now = dayjs().toISOString();
    await db.runAsync(
      'UPDATE notes SET title = ?, content = ?, updated_at = ?, tag = ? WHERE id = ?',
      [title, content, now, tag || null, id]
    );
    if (get().searchQuery) {
      await get().searchNotes(get().searchQuery);
    } else {
      await get().loadNotes();
    }
  },

  deleteNote: async (id) => {
    await db.runAsync('DELETE FROM notes WHERE id = ?', id);
    if (get().searchQuery) {
      await get().searchNotes(get().searchQuery);
    } else {
      await get().loadNotes();
    }
  },

  togglePin: async (id, currentPinned) => {
    await db.runAsync('UPDATE notes SET pinned = ? WHERE id = ?', [currentPinned ? 0 : 1, id]);
    if (get().searchQuery) {
      await get().searchNotes(get().searchQuery);
    } else {
      await get().loadNotes();
    }
  },

  // Lists
  loadLists: async () => {
    const lists = await db.getAllAsync('SELECT * FROM lists ORDER BY created_at DESC');
    set({ lists });
  },

  addList: async (title) => {
    const now = dayjs().toISOString();
    const result = await db.runAsync('INSERT INTO lists (title, created_at) VALUES (?, ?)', [title, now]);
    await get().loadLists();
    return result.lastInsertRowId;
  },

  deleteList: async (id) => {
    await db.runAsync('DELETE FROM lists WHERE id = ?', [id]);
    await get().loadLists();
  },

  // Reminders
  loadReminders: async () => {
    const reminders = await db.getAllAsync('SELECT * FROM reminders ORDER BY datetime ASC');
    set({ reminders });
  },

  addReminder: async (title, datetime) => {
    try {
      const now = dayjs().toISOString();
      const result = await db.runAsync(
        'INSERT INTO reminders (title, datetime, created_at) VALUES (?, ?, ?)',
        [title, datetime, now]
      );
      await get().loadReminders();
      return result.lastInsertRowId;
    } catch (error) {
      console.error('ADD REMINDER FATAL ERROR:', error);
      throw error;
    }
  },

  deleteReminder: async (id) => {
    await db.runAsync('DELETE FROM reminders WHERE id = ?', [id]);
    await get().loadReminders();
  }
}));
