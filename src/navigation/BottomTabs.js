import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { theme } from '../styles/theme';
import { Ionicons } from '@expo/vector-icons';

// Screens
import NotesScreen from '../screens/NotesScreen';
import NoteEditorScreen from '../screens/NoteEditorScreen';
import ListsScreen from '../screens/ListsScreen';
import ListEditorScreen from '../screens/ListEditorScreen';
import RemindersScreen from '../screens/RemindersScreen';
import SettingsScreen from '../screens/SettingsScreen';

const Tab = createBottomTabNavigator();
const NotesStack = createNativeStackNavigator();
const ListsStack = createNativeStackNavigator();

function NotesNavigator() {
  return (
    <NotesStack.Navigator screenOptions={{ headerShown: false }}>
      <NotesStack.Screen name="NotesHome" component={NotesScreen} />
      <NotesStack.Screen name="NoteEditor" component={NoteEditorScreen} />
    </NotesStack.Navigator>
  );
}

function ListsNavigator() {
  return (
    <ListsStack.Navigator screenOptions={{ headerShown: false }}>
      <ListsStack.Screen name="ListsHome" component={ListsScreen} />
      <ListsStack.Screen name="ListEditor" component={ListEditorScreen} />
    </ListsStack.Navigator>
  );
}

export default function BottomTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerStyle: {
          backgroundColor: theme.colors.background,
          elevation: 0,
          shadowOpacity: 0,
          borderBottomWidth: 1,
          borderBottomColor: theme.colors.border,
        },
        headerTintColor: theme.colors.text,
        headerTitleStyle: { ...theme.typography.h2 },
        tabBarStyle: {
          backgroundColor: theme.colors.card,
          borderTopWidth: 1,
          borderTopColor: theme.colors.border,
          elevation: 8,
          shadowOpacity: 0.1,
          shadowRadius: 10,
        },
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textSecondary,
        // Hide header on stack screens as they have custom headers
        headerShown: route.name !== 'Notes' && route.name !== 'Lists',
        tabBarHideOnKeyboard: true,
      })}
    >
      <Tab.Screen 
        name="Notes" 
        component={NotesNavigator} 
        options={{ tabBarIcon: ({ color, size }) => <Ionicons name="document-text-outline" size={size} color={color} /> }}
      />
      <Tab.Screen 
        name="Lists" 
        component={ListsNavigator} 
        options={{ tabBarIcon: ({ color, size }) => <Ionicons name="list-outline" size={size} color={color} /> }}
      />
      <Tab.Screen 
        name="Reminders" 
        component={RemindersScreen} 
        options={{ tabBarIcon: ({ color, size }) => <Ionicons name="notifications-outline" size={size} color={color} /> }}
      />
      <Tab.Screen 
        name="Settings" 
        component={SettingsScreen} 
        options={{ tabBarIcon: ({ color, size }) => <Ionicons name="settings-outline" size={size} color={color} /> }}
      />
    </Tab.Navigator>
  );
}
