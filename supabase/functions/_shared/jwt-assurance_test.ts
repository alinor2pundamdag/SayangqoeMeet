import { readJwtAssuranceLevel } from "./jwt-assurance.ts";

function tokenWithClaims(claims: Record<string, unknown>): string {
  const payload = btoa(JSON.stringify(claims)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return "header." + payload + ".signature";
}

Deno.test("reads AAL2 only as a claim value after upstream token verification", () => {
  if (readJwtAssuranceLevel(tokenWithClaims({ aal: "aal2" })) !== "aal2") {
    throw new Error("Expected aal2 claim");
  }
  if (readJwtAssuranceLevel(tokenWithClaims({ aal: "aal1" })) !== "aal1") {
    throw new Error("Expected aal1 claim");
  }
  if (readJwtAssuranceLevel("malformed") !== null || readJwtAssuranceLevel("a.e30.b") !== null) {
    throw new Error("Malformed or missing assurance claim must not be accepted");
  }
});
