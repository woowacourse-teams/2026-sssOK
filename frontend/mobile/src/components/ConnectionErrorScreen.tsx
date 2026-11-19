import { Image, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";

const MASCOT = require("../../assets/mascot-close.png");

interface ConnectionErrorScreenProps {
  onRetry: () => void;
}

export const ConnectionErrorScreen = ({ onRetry }: ConnectionErrorScreenProps) => (
  <ScrollView contentContainerStyle={styles.page} bounces={false}>
    <View style={styles.content}>
      <Image source={MASCOT} style={styles.image} resizeMode="contain" accessible={false} />
      <View style={styles.copy}>
        <Text accessibilityRole="header" style={styles.title}>
          쏙에 연결하지 못했어요
        </Text>
        <Text style={styles.description}>{"인터넷 연결을 확인하고\n다시 시도해 주세요."}</Text>
      </View>
    </View>
    <Pressable
      accessibilityRole="button"
      style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
      onPress={onRetry}
    >
      <Text style={styles.buttonText}>다시 시도</Text>
    </Pressable>
  </ScrollView>
);

const styles = StyleSheet.create({
  page: { flexGrow: 1, padding: 16, backgroundColor: "#FFFFFF" },
  content: {
    flexGrow: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 32,
    paddingVertical: 32,
  },
  image: { width: 180, height: 180 },
  copy: { gap: 8 },
  title: { fontSize: 20, lineHeight: 28, fontWeight: "800", color: "#1A1A1A", textAlign: "center" },
  description: {
    fontSize: 15,
    lineHeight: 24,
    fontWeight: "500",
    color: "#8B8781",
    textAlign: "center",
  },
  button: {
    flexShrink: 0,
    height: 65,
    borderRadius: 16,
    backgroundColor: "#F66D3B",
    alignItems: "center",
    justifyContent: "center",
  },
  buttonPressed: { backgroundColor: "#E0592A" },
  buttonText: { fontSize: 18, lineHeight: 24, fontWeight: "600", color: "#FFFFFF" },
});
