# NoteCraft 📝

<div align="center">
  <img src="assets/icon.png" width="128" height="128" alt="NoteCraft Icon" />
  <h3>Modern. Minimalist. Local-First.</h3>
  <p>Version 2.1.1</p>
</div>

---

**NoteCraft** is a premium, distraction-free note-taking and task-management application built for Android. It combines the tactile feel of "Paper & Ink" with the power of a modern relational database, all while keeping your data 100% private and on-device.

## 📥 Direct Download

You can download the latest stable release directly from this repository:
[**Download NoteCraft-v2.1.1-arm64.apk**](./NoteCraft-v2.1.1-arm64.apk)

---

## ✨ Core Pillars

### 📋 Intelligent Notes
- **Distraction-Free**: A clean canvas for your thoughts.
- **Rich Context**: Effortlessly switch between note-taking and editing.
- **Haptic Precision**: Tactile feedback for every meaningful interaction.

### ⚡ Blazing Fast Lists
- **Intuitive UX**: Move seamlessly between "Notes" and "Lists".
- **Dynamic Checklists**: Perfect for grocery runs, project milestones, or daily habits.
- **Keyboard Optimization**: Native `KeyboardAvoidingView` ensures your input is never hidden.

### 🔔 Smart Reminders
- **Native Notifications**: Local scheduling that works without an internet connection.
- **Visual Clarity**: Organized overview of upcoming tasks and alerts.

### 🔍 Search (FTS5)
- Powered by SQLite's **Full-Text Search (FTS5)** engine.
- Instantaneous results across all notes and lists, even with thousands of entries.

---

## 🛠 Technical Architecture

NoteCraft is engineered for performance and longevity:

- **Core**: React Native / Expo (SDK 54)
- **Engine**: Hermes JS Engine for optimized startup times.
- **Database**: `expo-sqlite` (Next Gen) utilizing FTS5 for lightning-fast indexing.
- **State**: `Zustand` for lightweight, predictable state management.
- **Animations**: `React Native Reanimated` for 60FPS UI transitions.
- **Privacy**: No cloud sync, no tracking, no analytics. Your data stays in your pocket.

### Build Optimization
The app uses **ABI Splitting** and **R8 (Proguard)** minification to reduce the binary footprint:
- **Universal Build**: ~86MB
- **Optimized ARM64 Build**: **~29MB**

---

## 🚀 Getting Started (Development)

1. **Clone & Install**:
   ```bash
   git clone https://github.com/sharmaram25/Note-Craft.git
   cd Note-Craft
   npm install
   ```
2. **Launch**:
   ```bash
   npx expo start
   ```
3. **Build Native APK**:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```

---

## 👨‍💻 Creator & Vision

**Ram Sharma**  
*Lead Developer & UI Designer*

NoteCraft was envisioned as a tool for thinkers. In an age of information overload and constant cloud synchronization, NoteCraft returns to the basics: a fast, reliable, and private space for your ideas to grow.

---

## 📄 License
MIT License - Developed with ❤️ by Ram Sharma.
