import { isSafeChatMediaObjectPath } from "./chat-media-path.ts";

Deno.test("accepts a scoped chat attachment object path", () => {
  if (!isSafeChatMediaObjectPath(
    "chat-attachments",
    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/photo.jpg",
  )) throw new Error("Expected a normal conversation-scoped object path to pass");
});

Deno.test("rejects unsafe buckets, prefixes, traversal, and malformed segments", () => {
  const conversation = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const cases: Array<[string, string, string]> = [
    ["other-bucket", conversation, conversation + "/user/file.jpg"],
    ["chat-attachments", conversation, "other-conversation/user/file.jpg"],
    ["chat-attachments", conversation, "/" + conversation + "/user/file.jpg"],
    ["chat-attachments", conversation, conversation + "/user/../private.jpg"],
    ["chat-attachments", conversation, conversation + "/./user/file.jpg"],
    ["chat-attachments", conversation, conversation + "//file.jpg"],
    ["chat-attachments", conversation, conversation + "/user\\\\file.jpg"],
    ["chat-attachments", conversation, conversation + "/user/%2e%2e/private.jpg"],
    ["chat-attachments", conversation, conversation + "/user/file.jpg?download=1"],
    ["chat-attachments", conversation, conversation + "/file.jpg"],
    ["chat-attachments", conversation, ""],
  ];
  for (const [bucket, id, path] of cases) {
    if (isSafeChatMediaObjectPath(bucket, id, path)) {
      throw new Error("Unsafe object path was accepted");
    }
  }
});
