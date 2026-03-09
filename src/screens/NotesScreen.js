import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, View, Text, StyleSheet, FlatList, TextInput, TouchableOpacity } from 'react-native';
import { theme } from '../styles/theme';
import { useStore } from '../store/useStore';
import NoteCard from '../components/NoteCard';
import { Ionicons } from '@expo/vector-icons';
import dayjs from 'dayjs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function NotesScreen({ navigation }) {
  const { notes, loadNotes, searchNotes, searchQuery, deleteNote, togglePin } = useStore();
  const insets = useSafeAreaInsets();
  const [viewMode, setViewMode] = useState('list'); // 'list' | 'timeline'

  useEffect(() => {
    loadNotes();
  }, []);

  const handleSearch = (text) => {
    searchNotes(text);
  };

  const groupNotesByDate = () => {
    const groups = {};
    notes.forEach(note => {
      const date = dayjs(note.updated_at).format('MMM D, YYYY');
      const label = date === dayjs().format('MMM D, YYYY') ? 'Today' : 
                    date === dayjs().subtract(1, 'day').format('MMM D, YYYY') ? 'Yesterday' : date;
      if (!groups[label]) groups[label] = [];
      groups[label].push(note);
    });
    return Object.entries(groups).map(([title, data]) => ({ title, data }));
  };

  const renderTimeline = () => {
    const grouped = groupNotesByDate();
    return (
      <FlatList
        data={grouped}
        keyExtractor={(item) => item.title}
        renderItem={({ item }) => (
          <View style={styles.timelineGroup}>
            <Text style={styles.timelineHeader}>{item.title}</Text>
            {item.data.map(note => (
              <NoteCard 
                key={note.id} 
                note={note} 
                onPress={() => navigation.navigate('NoteEditor', { note })}
                onPin={togglePin}
                onDelete={deleteNote}
              />
            ))}
          </View>
        )}
        contentContainerStyle={styles.listContainer}
      />
    );
  };

  const renderList = () => (
    <FlatList
      data={notes}
      keyExtractor={item => item.id.toString()}
      renderItem={({ item }) => (
        <NoteCard 
          note={item} 
          onPress={() => navigation.navigate('NoteEditor', { note: item })}
          onPin={togglePin}
          onDelete={deleteNote}
        />
      )}
      contentContainerStyle={styles.listContainer}
    />
  );

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <KeyboardAvoidingView 
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <Text style={styles.headerTitle}>Notes</Text>

        <View style={styles.header}>
          <View style={styles.searchContainer}>
            <Ionicons name="search" size={20} color={theme.colors.textSecondary} />
            <TextInput
              style={styles.searchInput}
              placeholder="Search notes..."
              value={searchQuery}
              onChangeText={handleSearch}
              placeholderTextColor={theme.colors.textSecondary}
            />
          </View>
          <TouchableOpacity 
            style={styles.viewToggle}
            onPress={() => setViewMode(prev => prev === 'list' ? 'timeline' : 'list')}
          >
            <Ionicons name={viewMode === 'list' ? "list" : "time"} size={24} color={theme.colors.text} />
          </TouchableOpacity>
        </View>

        {viewMode === 'list' ? renderList() : renderTimeline()}

      </KeyboardAvoidingView>

      <TouchableOpacity 
        style={styles.fab}
        onPress={() => navigation.navigate('NoteEditor')}
      >
        <Ionicons name="add" size={32} color="#FFF" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  header: {
    flexDirection: 'row',
    padding: theme.spacing.md,
    alignItems: 'center',
    gap: theme.spacing.md,
  },
  searchContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.round,
    paddingHorizontal: theme.spacing.md,
    height: 40,
    ...theme.shadows.paper,
  },
  searchInput: {
    flex: 1,
    marginLeft: theme.spacing.sm,
    ...theme.typography.body,
    color: theme.colors.text,
  },
  viewToggle: {
    padding: theme.spacing.xs,
  },
  listContainer: {
    padding: theme.spacing.md,
    paddingBottom: 80, // Space for FAB
  },
  timelineGroup: {
    marginBottom: theme.spacing.lg,
  },
  timelineHeader: {
    ...theme.typography.h2,
    fontSize: 16,
    color: theme.colors.textSecondary,
    marginBottom: theme.spacing.md,
    marginLeft: theme.spacing.sm,
  },
  fab: {
    position: 'absolute',
    bottom: theme.spacing.lg,
    right: theme.spacing.lg,
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: theme.colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    ...theme.shadows.float,
  },
  headerTitle: {
    ...theme.typography.h1,
    fontSize: 28,
    color: theme.colors.text,
    paddingHorizontal: theme.spacing.md,
    marginTop: theme.spacing.sm,
  }
});
