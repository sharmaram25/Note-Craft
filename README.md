# NoteCraft 📝

**NoteCraft** is a minimalist, local-first note-taking and task-management application built with React Native and Expo. Designed with a clean "Paper & Ink" aesthetic, it prioritizes speed, privacy, and simplicity.

![App Icon](assets/icon.png)

## 🚀 Features

- **Notes**: Rich text support with a distraction-free writing environment.
- **Lists**: Organize your thoughts into actionable checklists.
- **Reminders**: Never miss a beat with locally scheduled notifications.
- **Search**: Blazing fast full-text search (FTS5) for all your notes.
- **Privacy First**: 100% offline. All data is stored locally in a high-performance SQLite database.
- **Modern UI**: Smooth transitions, haptic feedback, and a premium minimalist design philosophy.
- **Optimized for Android**: Lightweight builds with ABI splitting and R8/Proguard minification.

## 🛠 Tech Stack

- **Framework**: React Native / Expo (SDK 54)
- **Database**: SQLite (via `expo-sqlite`)
- **State Management**: Zustand
- **Animations**: React Native Reanimated
- **Notifications**: Expo Notifications
- **Styling**: Structured Design Tokens (Vanilla CSS-in-JS)

## 📦 Installation

To run this project locally, ensure you have [Expo Go](https://expo.dev/go) installed on your mobile device.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sharmaram25/Note-Craft.git
   cd Note-Craft
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start the development server:**
   ```bash
   npx expo start
   ```

4. **Scan the QR Code** with your camera or Expo Go app.

## 🏗 Build & Execution

To generate a production-ready APK:

```bash
cd android
./gradlew assembleRelease
```

The optimized builds utilize ABI splitting to ensure the smallest possible binary size for your specific device architecture.

---

## 👨‍💻 Creator

**Ram Sharma**  
*Lead Developer & Designer*

Passionate about building software that feels like an extension of the human mind. NoteCraft was born out of a desire for a note-taking app that is as simple as paper but as powerful as a modern database.

[GitHub Profile](https://github.com/sharmaram25)

---

## 📄 License

This project is open-source and available under the MIT License.
