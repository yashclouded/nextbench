export function stringValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

export function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];
}

export function messagePreview(message: Record<string, unknown>): string {
  const text = stringValue(message.text);
  if (text) return text.slice(0, 160);
  if (message.image) return "Sent you a photo";
  if (message.video) return "Sent you a video";
  if (message.file) return "Sent you a document";
  if (message.audioUrl || message.type === "voice") return "Sent you a voice message";
  return "Sent you a message";
}
