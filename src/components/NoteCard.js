import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { theme } from '../styles/theme';
import dayjs from 'dayjs';
import { Ionicons } from '@expo/vector-icons';
import { Swipeable } from 'react-native-gesture-handler';

const stripMarkdown = (text) => {
  if (!text) return '';
  return text
    .replace(/!\[.*?\]\(.*?\)/g, '') // Remove images
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // Extract text from links
    .replace(/<[^>]*>/g, '') // Remove HTML tags
    .replace(/[#*~_>|+=\-]/g, '') // Remove markdown special characters
    .replace(/`{1,3}[^`]*`{1,3}/g, '') // Remove inline and block code
    .replace(/^\s*\d+\.\s+/gm, '') // Remove numbered lists at start of line
    .replace(/\n+/g, ' ') // Replace newlines with single spaces
    .trim();
};

export default function NoteCard({ note, onPress, onPin, onDelete }) {
  const isPinned = note.pinned === 1;
  const tagColor = note.tag ? theme.colors.tags[note.tag.toLowerCase()] || theme.colors.primary : null;

  const renderRightActions = () => (
    <TouchableOpacity style={styles.deleteAction} onPress={() => onDelete(note.id)}>
      <Ionicons name="trash-outline" size={24} color="#FFF" />
    </TouchableOpacity>
  );

  const renderLeftActions = () => (
    <TouchableOpacity style={styles.pinAction} onPress={() => onPin(note.id, isPinned)}>
      <Ionicons name={isPinned ? "pin-outline" : "pin"} size={24} color="#FFF" />
    </TouchableOpacity>
  );

  return (
    <Swipeable renderRightActions={renderRightActions} renderLeftActions={renderLeftActions}>
      <TouchableOpacity style={styles.card} onPress={() => onPress(note)}>
        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={1}>{note.title}</Text>
          {isPinned && <Ionicons name="pin" size={16} color={theme.colors.primary} style={styles.pinIcon} />}
        </View>
        
        <Text style={styles.preview} numberOfLines={2}>
          {stripMarkdown(note.content)}
        </Text>
        
        <View style={styles.footer}>
          <Text style={styles.date}>{dayjs(note.updated_at).format('MMM D, YYYY')}</Text>
          {tagColor && (
            <View style={[styles.tag, { backgroundColor: tagColor }]} />
          )}
        </View>
      </TouchableOpacity>
    </Swipeable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    padding: theme.spacing.md,
    marginBottom: theme.spacing.md,
    ...theme.shadows.paper,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing.sm,
  },
  title: {
    ...theme.typography.h2,
    fontSize: 18,
    color: theme.colors.text,
    flex: 1,
  },
  pinIcon: {
    marginLeft: theme.spacing.sm,
  },
  preview: {
    ...theme.typography.body,
    fontSize: 14,
    color: theme.colors.textSecondary,
    marginBottom: theme.spacing.sm,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  date: {
    ...theme.typography.caption,
  },
  tag: {
    width: 12,
    height: 12,
    borderRadius: theme.borderRadius.round,
  },
  deleteAction: {
    backgroundColor: theme.colors.danger,
    justifyContent: 'center',
    alignItems: 'center',
    width: 80,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing.md,
    marginLeft: theme.spacing.sm,
  },
  pinAction: {
    backgroundColor: theme.colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    width: 80,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing.md,
    marginRight: theme.spacing.sm,
  }
});
