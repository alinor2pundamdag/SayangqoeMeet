import { isUnregisteredFcmToken } from "./fcm-token-errors.ts";

Deno.test("removes only a provider-confirmed unregistered FCM token", () => {
  const payload = { error: { details: [{
    "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
    errorCode: "UNREGISTERED",
  }] } };
  if (!isUnregisteredFcmToken(404, payload)) throw new Error("Expected UNREGISTERED to be permanent");
});

Deno.test("does not remove tokens for transient, credential, or payload errors", () => {
  const cases: Array<[number, unknown]> = [
    [429, { error: { status: "RESOURCE_EXHAUSTED", details: [{ errorCode: "UNREGISTERED" }] } }],
    [401, { error: { status: "UNAUTHENTICATED" } }],
    [403, { error: { status: "PERMISSION_DENIED" } }],
    [500, { error: { status: "INTERNAL" } }],
    [400, { error: { status: "INVALID_ARGUMENT", details: [{ errorCode: "INVALID_ARGUMENT" }] } }],
    [404, { error: { status: "NOT_FOUND" } }],
    [200, { error: { details: [{ errorCode: "UNREGISTERED" }] } }],
  ];
  for (const [status, body] of cases) {
    if (isUnregisteredFcmToken(status, body)) throw new Error(`Unexpected token deletion for HTTP ${status}`);
  }
});
