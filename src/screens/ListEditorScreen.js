import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, View, Text, StyleSheet, FlatList, TouchableOpacity, TextInput } from 'react-native';
import { theme } from '../styles/theme';
import { Ionicons } from '@expo/vector-icons';
import db from '../database/database';
import Animated, { FadeIn, Layout } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function ListEditorScreen({ route, navigation }) {
  const { listId, listTitle } = route.params;
  const [items, setItems] = useState([]);
  const [newItemText, setNewItemText] = useState('');
  const insets = useSafeAreaInsets();

  useEffect(() => {
    loadItems();
  }, []);

  const loadItems = async () => {
    // Sort so uncompleted are first, completed are pushed to bottom
    const results = await db.getAllAsync(
      'SELECT * FROM list_items WHERE list_id = ? ORDER BY completed ASC, id ASC',
      [listId]
    );
    setItems(results);
  };

  const handleAddItem = async () => {
    if (!newItemText.trim()) return;
    await db.runAsync(
      'INSERT INTO list_items (list_id, text, completed) VALUES (?, ?, 0)',
      [listId, newItemText.trim()]
    );
    setNewItemText('');
    await loadItems();
  };

  const toggleItem = async (id, currentCompleted) => {
    await db.runAsync(
      'UPDATE list_items SET completed = ? WHERE id = ?',
      [currentCompleted ? 0 : 1, id]
    );
    await loadItems();
  };

  const deleteItem = async (id) => {
    await db.runAsync('DELETE FROM list_items WHERE id = ?', [id]);
    await loadItems();
  };

  return (
    <KeyboardAvoidingView 
      style={[styles.container, { paddingTop: Math.max(insets.top, 10) }]} 
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        <Text style={styles.title} numberOfLines={1}>{listTitle}</Text>
      </View>

      <FlatList
        data={items}
        keyExtractor={item => item.id.toString()}
        renderItem={({ item }) => (
          <Animated.View layout={Layout.springify()} entering={FadeIn} style={[styles.itemRow, item.completed && styles.itemRowCompleted]}>
            <TouchableOpacity onPress={() => toggleItem(item.id, item.completed)} style={styles.checkbox}>
              <Ionicons 
                name={item.completed ? "checkmark-circle" : "ellipse-outline"} 
                size={24} 
                color={item.completed ? theme.colors.success : theme.colors.textSecondary} 
              />
            </TouchableOpacity>
            
            <Text style={[styles.itemText, item.completed && styles.itemTextCompleted]}>
              {item.text}
            </Text>
            
            <TouchableOpacity onPress={() => deleteItem(item.id)}>
              <Ionicons name="close" size={20} color={theme.colors.danger} />
            </TouchableOpacity>
          </Animated.View>
        )}
        contentContainerStyle={styles.listContainer}
      />

      <View style={styles.inputContainer}>
        <TextInput
          style={styles.input}
          placeholder="Add item..."
          placeholderTextColor={theme.colors.textSecondary}
          value={newItemText}
          onChangeText={setNewItemText}
          onSubmitEditing={handleAddItem}
        />
        <TouchableOpacity style={styles.addButton} onPress={handleAddItem}>
          <Ionicons name="add" size={24} color="#FFF" />
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  backBtn: {
    padding: theme.spacing.sm,
    marginRight: theme.spacing.sm,
  },
  title: {
    ...theme.typography.h1,
    fontSize: 22,
    color: theme.colors.text,
    flex: 1,
  },
  listContainer: {
    padding: theme.spacing.md,
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.card,
    padding: theme.spacing.md,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing.sm,
    ...theme.shadows.paper,
  },
  itemRowCompleted: {
    opacity: 0.6,
  },
  checkbox: {
    marginRight: theme.spacing.md,
  },
  itemText: {
    flex: 1,
    ...theme.typography.body,
    color: theme.colors.text,
  },
  itemTextCompleted: {
    textDecorationLine: 'line-through',
    color: theme.colors.textSecondary,
  },
  inputContainer: {
    flexDirection: 'row',
    padding: theme.spacing.md,
    backgroundColor: theme.colors.card,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    gap: theme.spacing.sm,
  },
  input: {
    flex: 1,
    backgroundColor: theme.colors.background,
    borderRadius: theme.borderRadius.md,
    paddingHorizontal: theme.spacing.md,
    height: 48,
    ...theme.typography.body,
    color: theme.colors.text,
  },
  addButton: {
    width: 48,
    height: 48,
    backgroundColor: theme.colors.primary,
    borderRadius: theme.borderRadius.md,
    justifyContent: 'center',
    alignItems: 'center',
  }
});
