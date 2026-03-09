import React, { useState, useEffect, useRef } from 'react';
import { View, TextInput, StyleSheet, TouchableOpacity, Text, ScrollView } from 'react-native';
import { theme } from '../styles/theme';
import { useStore } from '../store/useStore';
import { Ionicons } from '@expo/vector-icons';
import { RichEditor, RichToolbar, actions } from 'react-native-pell-rich-editor';
import MarkdownDisplay from 'react-native-markdown-display';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function NoteEditorScreen({ route, navigation }) {
  const existingNote = route.params?.note;
  const { addNote, updateNote } = useStore();

  const [id, setId] = useState(existingNote?.id || null);
  const [title, setTitle] = useState(existingNote?.title || '');
  const [content, setContent] = useState(existingNote?.content || '');
  const [tag, setTag] = useState(existingNote?.tag || '');
  const [isMarkdown, setIsMarkdown] = useState(false);
  
  const editorRef = useRef();

  // Use refs to keep track of latest state for reliable auto-saving
  const idRef = useRef(existingNote?.id || null);
  const titleRef = useRef(title);
  const contentRef = useRef(content);
  const tagRef = useRef(tag);

  useEffect(() => {
    titleRef.current = title;
    contentRef.current = content;
    tagRef.current = tag;
  }, [title, content, tag]);

  // Auto-save logic
  useEffect(() => {
    const saveTimeout = setTimeout(async () => {
      const currentTitle = titleRef.current;
      const currentContent = contentRef.current;
      const currentTag = tagRef.current;
      const currentId = idRef.current;

      if (!currentTitle && !currentContent) return;

      try {
        if (currentId) {
          await updateNote(currentId, currentTitle, currentContent, currentTag);
        } else {
          const newId = await addNote(currentTitle, currentContent, currentTag);
          setId(newId);
          idRef.current = newId;
        }
      } catch (error) {
        console.error('Failed to auto-save note:', error);
      }
    }, 1000); // Save 1s after last typing

    return () => clearTimeout(saveTimeout);
  }, [title, content, tag, updateNote, addNote]);

  const handleTagToggle = (colorName) => {
    setTag(tag === colorName ? '' : colorName);
  };

  const tagColors = ['work', 'ideas', 'personal', 'research'];
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.container, { paddingTop: Math.max(insets.top, 10) }]}>
      {/* Header / Toolbar */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <View style={styles.tagSelector}>
          {tagColors.map(colorName => (
            <TouchableOpacity 
              key={colorName} 
              style={[
                styles.tagCircle, 
                { backgroundColor: theme.colors.tags[colorName] },
                tag === colorName && styles.tagSelected
              ]}
              onPress={() => handleTagToggle(colorName)}
            />
          ))}
        </View>

        <TouchableOpacity 
          style={styles.modeToggle} 
          onPress={() => setIsMarkdown(!isMarkdown)}
        >
          <Text style={styles.modeText}>{isMarkdown ? 'MD' : 'RTF'}</Text>
        </TouchableOpacity>
      </View>

      <TextInput
        style={styles.titleInput}
        placeholder="Note Title"
        placeholderTextColor={theme.colors.textSecondary}
        value={title}
        onChangeText={setTitle}
        autoFocus={!existingNote}
      />

      {isMarkdown ? (
        <ScrollView style={styles.editorContainer}>
          <TextInput
            style={styles.markdownInput}
            multiline
            placeholder="Write in Markdown..."
            placeholderTextColor={theme.colors.textSecondary}
            value={content}
            onChangeText={setContent}
            textAlignVertical="top"
          />
          {content.length > 0 && (
            <View style={styles.previewContainer}>
              <Text style={styles.previewLabel}>Preview:</Text>
              <View style={styles.markdownPreview}>
                <MarkdownDisplay style={markdownStyles}>
                  {content}
                </MarkdownDisplay>
              </View>
            </View>
          )}
        </ScrollView>
      ) : (
        <View style={styles.editorContainer}>
          <RichToolbar
            editor={editorRef}
            actions={[
              actions.setBold, actions.setItalic, actions.setUnderline, 
              actions.heading1, actions.insertBulletsList, actions.insertOrderedList
            ]}
            iconTint={theme.colors.textSecondary}
            selectedIconTint={theme.colors.primary}
            style={styles.richToolbar}
          />
          <ScrollView style={styles.richContainer}>
            <RichEditor
              ref={editorRef}
              initialContentHTML={content}
              onChange={setContent}
              placeholder="Write something amazing..."
              editorStyle={{
                backgroundColor: theme.colors.background,
                color: theme.colors.text,
                padding: theme.spacing.md,
              }}
            />
          </ScrollView>
        </View>
      )}
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
    alignItems: 'center',
    padding: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  backBtn: {
    padding: theme.spacing.xs,
  },
  tagSelector: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: theme.spacing.sm,
  },
  tagCircle: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  tagSelected: {
    borderColor: theme.colors.text,
  },
  modeToggle: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: theme.colors.card,
    borderRadius: theme.borderRadius.md,
    ...theme.shadows.paper,
  },
  modeText: {
    ...theme.typography.h2,
    fontSize: 14,
    color: theme.colors.primary,
  },
  titleInput: {
    ...theme.typography.h1,
    color: theme.colors.text,
    padding: theme.spacing.md,
  },
  editorContainer: {
    flex: 1,
  },
  markdownInput: {
    flex: 1,
    ...theme.typography.body,
    color: theme.colors.text,
    padding: theme.spacing.md,
    minHeight: 200,
  },
  previewContainer: {
    padding: theme.spacing.md,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    backgroundColor: theme.colors.card,
  },
  previewLabel: {
    ...theme.typography.caption,
    marginBottom: theme.spacing.sm,
  },
  markdownPreview: {
    opacity: 0.8,
  },
  richToolbar: {
    backgroundColor: theme.colors.card,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  richContainer: {
    flex: 1,
  }
});

const markdownStyles = {
  body: {
    ...theme.typography.body,
    color: theme.colors.text,
  },
  heading1: {
    ...theme.typography.h1,
    color: theme.colors.text,
  },
  heading2: {
    ...theme.typography.h2,
    color: theme.colors.text,
  }
};
