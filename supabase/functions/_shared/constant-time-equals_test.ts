import { constantTimeEquals } from "./constant-time-equals.ts";

Deno.test("constant-work secret comparison accepts only identical values", () => {
  const cases: Array<[string, string, boolean]> = [
    ["secret", "secret", true],
    ["secret", "secreT", false],
    ["secret", "secret ", false],
    ["", "", true],
    ["", "x", false],
    ["🔐token", "🔐token", true],
  ];
  for (const [left, right, expected] of cases) {
    if (constantTimeEquals(left, right) !== expected) {
      throw new Error("Secret comparison returned an unexpected result");
    }
  }
});
