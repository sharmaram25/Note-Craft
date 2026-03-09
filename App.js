import { useEffect, useState } from 'react';
import { Alert } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import BottomTabs from './src/navigation/BottomTabs';
import { setupDatabaseAsync } from './src/database/schema';

export default function App() {
  const [isDbReady, setIsDbReady] = useState(false);

  useEffect(() => {
    // Initialize SQLite tables on app load
    const initDb = async () => {
      try {
        await setupDatabaseAsync();
      } catch(e) {
        console.error('Database setup error:', e);
        Alert.alert('Database Init Error', String(e));
      }
      setIsDbReady(true);
    };
    initDb();
  }, []);

  if (!isDbReady) {
    return null;
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <NavigationContainer>
          <BottomTabs />
          <StatusBar style="auto" />
        </NavigationContainer>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
