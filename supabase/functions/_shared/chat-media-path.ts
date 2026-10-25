/**
 * Validate a queued Storage object path before a service-role deletion.
 * The conversation ID comes from the trusted cleanup queue; the object path is
 * treated as untrusted data because clients can submit attachment metadata.
 */
export function isSafeChatMediaObjectPath(
  bucketId: string,
  conversationId: string,
  objectPath: string,
): boolean {
  if (bucketId !== "chat-attachments" || !conversationId || !objectPath) return false;
  if (objectPath.startsWith("/") || objectPath.includes("\\") || /[?#\u0000-\u001f\u007f]/.test(objectPath)) return false;
  if (/%(?:2e|2f|5c)/i.test(objectPath)) return false;
  if (!objectPath.startsWith(conversationId + "/")) return false;
  const segments = objectPath.split("/");
  if (segments.length < 3 || segments.some((segment) => !segment || segment === "." || segment === "..")) {
    return false;
  }
  return true;
}
