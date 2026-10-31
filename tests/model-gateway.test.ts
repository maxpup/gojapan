import assert from "node:assert/strict";
import test from "node:test";
import { ModelGateway } from "../lib/ai/gateway";
import { AiProviderError, type AiProviderAdapter, type GenerateRequest, type GenerateResult } from "../lib/ai/types";

function adapter(id: string, generate: (request: GenerateRequest) => Promise<GenerateResult>): AiProviderAdapter {
  return { id, generate };
}

test("falls back in configured order for retryable provider errors", async () => {
  const gateway = new ModelGateway([
    adapter("first", async () => { throw new AiProviderError("RATE_LIMIT", "busy", true); }),
    adapter("second", async (request) => ({ text: "ok", resolvedModel: request.model })),
  ]);

  const result = await gateway.generate(
    { primary: "first/model-a", fallbacks: ["second/model-b"] },
    { messages: [{ role: "user", content: "hello" }] },
  );

  assert.equal(result.text, "ok");
  assert.deepEqual(result.attempts, ["first/model-a", "second/model-b"]);
});

test("does not use a fallback for non-retryable errors", async () => {
  let fallbackCalled = false;
  const gateway = new ModelGateway([
    adapter("first", async () => { throw new AiProviderError("INVALID_REQUEST", "bad input", false); }),
    adapter("second", async (request) => {
      fallbackCalled = true;
      return { text: "unexpected", resolvedModel: request.model };
    }),
  ]);

  await assert.rejects(
    gateway.generate(
      { primary: "first/model-a", fallbacks: ["second/model-b"] },
      { messages: [{ role: "user", content: "hello" }] },
    ),
    (error: unknown) => error instanceof AiProviderError && error.code === "INVALID_REQUEST",
  );
  assert.equal(fallbackCalled, false);
});

