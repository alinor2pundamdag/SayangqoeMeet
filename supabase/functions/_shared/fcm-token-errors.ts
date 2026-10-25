/** FCM v1 error classification. Never remove tokens for transient/auth/payload errors. */
export function isUnregisteredFcmToken(statusCode: number, payload: unknown): boolean {
  // FCM v1 reports UNREGISTERED as HTTP 404. Ignore even contradictory details on other statuses.
  if (statusCode !== 404) return false;
  if (!payload || typeof payload !== "object") return false;
  const root = payload as { error?: { details?: unknown } };
  const details = root.error?.details;
  if (!Array.isArray(details)) return false;
  return details.some((detail) => {
    if (!detail || typeof detail !== "object") return false;
    const entry = detail as { errorCode?: unknown; "@type"?: unknown };
    return entry.errorCode === "UNREGISTERED" &&
      (entry["@type"] === "type.googleapis.com/google.firebase.fcm.v1.FcmError" ||
       entry["@type"] === undefined);
  });
}
