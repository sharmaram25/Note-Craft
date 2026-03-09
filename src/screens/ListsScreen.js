import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, View, Text, StyleSheet, FlatList, TouchableOpacity, TextInput } from 'react-native';
import { theme } from '../styles/theme';
import { useStore } from '../store/useStore';
import ListCard from '../components/ListCard';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function ListsScreen({ navigation }) {
  const { lists, loadLists, addList, deleteList } = useStore();
  const [newListTitle, setNewListTitle] = useState('');
  const insets = useSafeAreaInsets();

  useEffect(() => {
    loadLists();
  }, []);

  const handleCreateList = async () => {
    if (!newListTitle.trim()) return;
    const newId = await addList(newListTitle.trim());
    setNewListTitle('');
    navigation.navigate('ListEditor', { listId: newId, listTitle: newListTitle.trim() });
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <KeyboardAvoidingView 
        style={{ flex: 1 }} 
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <Text style={styles.headerTitle}>Lists</Text>
        <View style={styles.createContainer}>
          <TextInput
            style={styles.input}
            placeholder="New list..."
            placeholderTextColor={theme.colors.textSecondary}
            value={newListTitle}
            onChangeText={setNewListTitle}
            onSubmitEditing={handleCreateList}
          />
          <TouchableOpacity style={styles.addButton} onPress={handleCreateList}>
            <Ionicons name="add" size={24} color="#FFF" />
          </TouchableOpacity>
        </View>

        <FlatList
          data={lists}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item }) => (
            <ListCard 
              list={item} 
              onPress={() => navigation.navigate('ListEditor', { listId: item.id, listTitle: item.title })}
              onDelete={deleteList}
            />
          )}
          contentContainerStyle={styles.listContainer}
          ListEmptyComponent={
            <Text style={styles.emptyText}>No checklists yet.</Text>
          }
        />
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  createContainer: {
    flexDirection: 'row',
    padding: theme.spacing.md,
    gap: theme.spacing.sm,
  },
  input: {
    flex: 1,
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    paddingHorizontal: theme.spacing.md,
    height: 48,
    ...theme.typography.body,
    color: theme.colors.text,
    ...theme.shadows.paper,
  },
  addButton: {
    width: 48,
    height: 48,
    backgroundColor: theme.colors.primary,
    borderRadius: theme.borderRadius.md,
    justifyContent: 'center',
    alignItems: 'center',
    ...theme.shadows.paper,
  },
  listContainer: {
    padding: theme.spacing.md,
  },
  emptyText: {
    ...theme.typography.body,
    color: theme.colors.textSecondary,
    textAlign: 'center',
    marginTop: theme.spacing.xl,
  },
  headerTitle: {
    ...theme.typography.h1,
    fontSize: 28,
    color: theme.colors.text,
    paddingHorizontal: theme.spacing.md,
    marginTop: theme.spacing.sm,
    marginBottom: theme.spacing.xs,
  }
});
