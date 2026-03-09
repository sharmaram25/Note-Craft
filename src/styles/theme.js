export const theme = {
  colors: {
    background: '#FAF8F5',     // Warm paper beige
    card: '#FFFFFF',          // Clean white for notes
    primary: '#4A90E2',       // Soft blue accent
    text: '#2C3E50',          // Dark graphite text
    textSecondary: '#7F8C8D', // Lighter graphite for secondary text
    border: '#E8E5E0',        // Subtle border
    danger: '#E74C3C',        // Red for delete
    success: '#2ECC71',       // Green for completion
    
    // Tag colors
    tags: {
      work: '#3498DB',
      ideas: '#F1C40F',
      personal: '#9B59B6',
      research: '#E67E22',
    }
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  typography: {
    h1: { fontSize: 28, fontWeight: '700' },
    h2: { fontSize: 22, fontWeight: '600' },
    body: { fontSize: 16, lineHeight: 24 },
    caption: { fontSize: 13, color: '#7F8C8D' },
  },
  shadows: {
    paper: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.05,
      shadowRadius: 8,
      elevation: 2,
    },
    float: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.1,
      shadowRadius: 12,
      elevation: 5,
    }
  },
  borderRadius: {
    sm: 6,
    md: 12,
    lg: 20,
    round: 999,
  }
};
