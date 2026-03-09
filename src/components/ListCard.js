import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { theme } from '../styles/theme';
import dayjs from 'dayjs';
import { Ionicons } from '@expo/vector-icons';
import { Swipeable } from 'react-native-gesture-handler';

export default function ListCard({ list, onPress, onDelete }) {
  const renderRightActions = () => (
    <TouchableOpacity style={styles.deleteAction} onPress={() => onDelete(list.id)}>
      <Ionicons name="trash-outline" size={24} color="#FFF" />
    </TouchableOpacity>
  );

  return (
    <Swipeable renderRightActions={renderRightActions}>
      <TouchableOpacity style={styles.card} onPress={() => onPress(list)}>
        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={1}>{list.title}</Text>
          <Ionicons name="list" size={20} color={theme.colors.primary} />
        </View>
        
        <View style={styles.footer}>
          <Text style={styles.date}>{dayjs(list.created_at).format('MMM D, YYYY')}</Text>
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
    marginBottom: theme.spacing.md,
  },
  title: {
    ...theme.typography.h2,
    fontSize: 18,
    color: theme.colors.text,
    flex: 1,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  date: {
    ...theme.typography.caption,
  },
  deleteAction: {
    backgroundColor: theme.colors.danger,
    justifyContent: 'center',
    alignItems: 'center',
    width: 80,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing.md,
    marginLeft: theme.spacing.sm,
  }
});
