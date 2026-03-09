import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, View, Text, StyleSheet, FlatList, TouchableOpacity, TextInput } from 'react-native';
import { theme } from '../styles/theme';
import { useStore } from '../store/useStore';
import { Ionicons } from '@expo/vector-icons';
import dayjs from 'dayjs';
import * as Notifications from 'expo-notifications';
import DateTimePicker from '@react-native-community/datetimepicker';
import { Swipeable } from 'react-native-gesture-handler';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export default function RemindersScreen() {
  const { reminders, loadReminders, addReminder, deleteReminder } = useStore();
  const [newTitle, setNewTitle] = useState('');
  const [date, setDate] = useState(new Date());
  const [showPicker, setShowPicker] = useState(false);
  const [mode, setMode] = useState('date'); // 'date' | 'time'

  useEffect(() => {
    loadReminders();
    requestPermissions();
  }, []);

  const requestPermissions = async () => {
    try {
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== 'granted') {
        console.log('Notification permissions not granted');
      }
    } catch (error) {
      console.log('Push notifications restricted in Expo Go. Local notifications might still work:', error);
    }
  };

  const scheduleNotification = async (title, dateObj) => {
    try {
      const trigger = dateObj;
      await Notifications.scheduleNotificationAsync({
        content: {
          title: "NoteCraft Reminder",
          body: title,
          sound: true,
        },
        trigger,
      });
    } catch (error) {
      console.log('Could not schedule local notification in this environment:', error);
    }
  };

  const handleAddReminder = async () => {
    if (!newTitle.trim()) return;
    
    // Add to local DB
    const dateStr = date.toISOString();
    const newId = addReminder(newTitle.trim(), dateStr);
    
    // Schedule Local Notification
    if (date > new Date()) {
      await scheduleNotification(newTitle.trim(), date);
    }

    setNewTitle('');
    setDate(new Date());
  };

  const handleDelete = (id) => {
    deleteReminder(id);
  };

  const onDateChange = (event, selectedDate) => {
    setShowPicker(false);
    if (selectedDate) {
      setDate(selectedDate);
      if (mode === 'date') {
        setMode('time');
        setShowPicker(true); // show time picker immediately after date
      } else {
        setMode('date');
      }
    }
  };

  const openPicker = () => {
    setMode('date');
    setShowPicker(true);
  };

  const renderReminder = ({ item }) => {
    const isPast = dayjs(item.datetime).isBefore(dayjs());

    const renderRightActions = () => (
      <TouchableOpacity style={styles.deleteAction} onPress={() => handleDelete(item.id)}>
        <Ionicons name="trash-outline" size={24} color="#FFF" />
      </TouchableOpacity>
    );

    return (
      <Swipeable renderRightActions={renderRightActions}>
        <View style={[styles.card, isPast && styles.cardPast]}>
          <View style={styles.cardHeader}>
            <Text style={[styles.title, isPast && styles.textPast]} numberOfLines={1}>{item.title}</Text>
            <Ionicons name="alarm-outline" size={20} color={isPast ? theme.colors.textSecondary : theme.colors.primary} />
          </View>
          <Text style={[styles.datetime, isPast && styles.textPast]}>
            {dayjs(item.datetime).format('MMM D, YYYY • h:mm A')}
          </Text>
        </View>
      </Swipeable>
    );
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.createContainer}>
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            placeholder="New reminder..."
            placeholderTextColor={theme.colors.textSecondary}
            value={newTitle}
            onChangeText={setNewTitle}
          />
          <TouchableOpacity style={styles.dateButton} onPress={openPicker}>
            <Ionicons name="calendar-outline" size={24} color={theme.colors.primary} />
          </TouchableOpacity>
        </View>
        
        <View style={styles.actionRow}>
          <Text style={styles.selectedDateText}>
            Scheduled for: {dayjs(date).format('MMM D, h:mm A')}
          </Text>
          <TouchableOpacity 
            style={[styles.addButton, !newTitle.trim() && styles.addButtonDisabled]} 
            onPress={handleAddReminder}
            disabled={!newTitle.trim()}
          >
            <Text style={styles.addButtonText}>Save</Text>
          </TouchableOpacity>
        </View>
      </View>

      {showPicker && (
        <DateTimePicker
          value={date}
          mode={mode}
          is24Hour={false}
          onChange={onDateChange}
          minimumDate={new Date()}
        />
      )}

      <FlatList
        data={reminders}
        keyExtractor={item => item.id.toString()}
        renderItem={renderReminder}
        contentContainerStyle={styles.listContainer}
        ListEmptyComponent={
          <Text style={styles.emptyText}>No reminders set.</Text>
        }
      />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  createContainer: {
    padding: theme.spacing.md,
    backgroundColor: theme.colors.card,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    ...theme.shadows.paper,
    zIndex: 10,
  },
  inputRow: {
    flexDirection: 'row',
    gap: theme.spacing.sm,
    marginBottom: theme.spacing.md,
  },
  input: {
    flex: 1,
    backgroundColor: theme.colors.background,
    borderRadius: theme.borderRadius.md,
    paddingHorizontal: theme.spacing.md,
    height: 48,
    borderWidth: 1,
    borderColor: theme.colors.border,
    ...theme.typography.body,
    color: theme.colors.text,
  },
  dateButton: {
    width: 48,
    height: 48,
    backgroundColor: theme.colors.background,
    borderRadius: theme.borderRadius.md,
    borderWidth: 1,
    borderColor: theme.colors.border,
    justifyContent: 'center',
    alignItems: 'center',
  },
  actionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  selectedDateText: {
    ...theme.typography.caption,
    color: theme.colors.primary,
  },
  addButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: theme.spacing.md,
    paddingVertical: theme.spacing.sm,
    borderRadius: theme.borderRadius.md,
  },
  addButtonDisabled: {
    opacity: 0.5,
  },
  addButtonText: {
    color: '#FFF',
    ...theme.typography.h2,
    fontSize: 16,
  },
  listContainer: {
    padding: theme.spacing.md,
  },
  card: {
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    padding: theme.spacing.md,
    marginBottom: theme.spacing.md,
    ...theme.shadows.paper,
  },
  cardPast: {
    opacity: 0.6,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing.xs,
  },
  title: {
    ...theme.typography.h2,
    fontSize: 18,
    color: theme.colors.text,
    flex: 1,
  },
  datetime: {
    ...theme.typography.caption,
    color: theme.colors.textSecondary,
  },
  textPast: {
    textDecorationLine: 'line-through',
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
  emptyText: {
    ...theme.typography.body,
    color: theme.colors.textSecondary,
    textAlign: 'center',
    marginTop: theme.spacing.xl,
  }
});
