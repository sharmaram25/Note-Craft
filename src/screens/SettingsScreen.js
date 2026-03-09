import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Linking } from 'react-native';
import { theme } from '../styles/theme';
import { useStore } from '../store/useStore';
import { Ionicons } from '@expo/vector-icons';
import db from '../database/database';

export default function SettingsScreen() {
  const { notes, loadNotes, loadLists, loadReminders } = useStore();
  const [stats, setStats] = useState({
    totalNotes: 0,
    totalWords: 0,
    longestNote: 0,
    avgLength: 0
  });

  useEffect(() => {
    calculateStats();
  }, [notes]);

  const calculateStats = () => {
    if (notes.length === 0) {
      setStats({ totalNotes: 0, totalWords: 0, longestNote: 0, avgLength: 0 });
      return;
    }

    let totalWords = 0;
    let longestNote = 0;

    notes.forEach(note => {
      const text = `${note.title} ${note.content}`.replace(/[#_*\[\]]/g, '');
      const words = text.split(/\s+/).filter(w => w.length > 0).length;
      
      totalWords += words;
      if (words > longestNote) longestNote = words;
    });

    setStats({
      totalNotes: notes.length,
      totalWords,
      longestNote,
      avgLength: Math.round(totalWords / notes.length)
    });
  };

  return (
    <ScrollView style={styles.container}>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Writing Statistics</Text>
        <View style={styles.statsGrid}>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{stats.totalNotes}</Text>
            <Text style={styles.statLabel}>Total Notes</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{stats.totalWords.toLocaleString()}</Text>
            <Text style={styles.statLabel}>Total Words</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{stats.longestNote}</Text>
            <Text style={styles.statLabel}>Longest (Words)</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{stats.avgLength}</Text>
            <Text style={styles.statLabel}>Avg Length</Text>
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Privacy</Text>
        <Text style={styles.privacyText}>
          NoteCraft stores all data locally on your device using SQLite. No information leaves your phone. No internet connectivity is required.
        </Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>About</Text>
        <View style={styles.aboutContainer}>
          <Text style={styles.appName}>NoteCraft</Text>
          <Text style={styles.appVersion}>Version 2.1.1</Text>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Developer</Text>
        <View style={styles.developerCard}>
          <View style={styles.developerInfo}>
            <Ionicons name="person-circle-outline" size={48} color={theme.colors.primary} />
            <View style={styles.developerTextContainer}>
              <Text style={styles.developerName}>RAM Sharma</Text>
              <Text style={styles.developerSubtitle}>Creator & Developer</Text>
            </View>
          </View>
          
          <TouchableOpacity 
            style={styles.linkButton} 
            onPress={() => Linking.openURL('https://github.com/sharmaram25')}
          >
            <Ionicons name="logo-github" size={20} color={theme.colors.text} />
            <Text style={styles.linkText}>GitHub Profile</Text>
            <Ionicons name="open-outline" size={16} color={theme.colors.textSecondary} style={{ marginLeft: 'auto' }} />
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={styles.linkButton} 
            onPress={() => Linking.openURL('https://github.com/sharmaram25/Note-Craft')}
          >
            <Ionicons name="code-slash-outline" size={20} color={theme.colors.text} />
            <Text style={styles.linkText}>Source Code</Text>
            <Ionicons name="open-outline" size={16} color={theme.colors.textSecondary} style={{ marginLeft: 'auto' }} />
          </TouchableOpacity>
        </View>
      </View>

    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  section: {
    padding: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  sectionTitle: {
    ...theme.typography.h2,
    color: theme.colors.text,
    marginBottom: theme.spacing.md,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: theme.spacing.md,
  },
  statBox: {
    flex: 1,
    minWidth: '40%',
    backgroundColor: theme.colors.card,
    padding: theme.spacing.md,
    borderRadius: theme.borderRadius.md,
    alignItems: 'center',
    ...theme.shadows.paper,
  },
  statValue: {
    ...theme.typography.h1,
    color: theme.colors.primary,
    marginBottom: theme.spacing.xs,
  },
  statLabel: {
    ...theme.typography.caption,
    color: theme.colors.textSecondary,
  },
  aboutText: {
    ...theme.typography.body,
    color: theme.colors.text,
    marginBottom: theme.spacing.sm,
  },
  privacyText: {
    ...theme.typography.body,
    fontSize: 14,
    color: theme.colors.textSecondary,
    fontStyle: 'italic',
    lineHeight: 20,
  },
  aboutContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing.md,
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    ...theme.shadows.paper,
  },
  appName: {
    ...theme.typography.h1,
    color: theme.colors.primary,
    fontSize: 24,
    marginBottom: 4,
  },
  appVersion: {
    ...theme.typography.caption,
    color: theme.colors.textSecondary,
    fontSize: 14,
  },
  developerCard: {
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    padding: theme.spacing.md,
    ...theme.shadows.paper,
  },
  developerInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: theme.spacing.md,
    paddingBottom: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  developerTextContainer: {
    marginLeft: theme.spacing.sm,
  },
  developerName: {
    ...theme.typography.h2,
    color: theme.colors.text,
    fontSize: 18,
  },
  developerSubtitle: {
    ...theme.typography.caption,
    color: theme.colors.primary,
  },
  linkButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: theme.spacing.sm,
    gap: theme.spacing.sm,
  },
  linkText: {
    ...theme.typography.body,
    color: theme.colors.text,
    fontSize: 15,
  }
});
