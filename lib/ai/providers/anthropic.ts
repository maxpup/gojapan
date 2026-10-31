import { parseModelRef } from "../model-ref";
import { AiProviderError, type AiProviderAdapter, type GenerateRequest, type GenerateResult } from "../types";

export class AnthropicAdapter implements AiProviderAdapter {
  readonly id = "anthropic";

  constructor(private readonly apiKey: string, private readonly baseUrl = "https://api.anthropic.com") {}

  async generate(request: GenerateRequest, signal?: AbortSignal): Promise<GenerateResult> {
    const { model } = parseModelRef(request.model);
    const system = request.messages.filter((message) => message.role === "system").map((message) => message.content).join("\n\n");
    const messages = request.messages.filter((message) => message.role !== "system");
    const response = await fetch(`${this.baseUrl.replace(/\/$/, "")}/v1/messages`, {
      method: "POST",
      headers: {
        "x-api-key": this.apiKey,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
      },
      body: JSON.stringify({ model, system, messages, max_tokens: request.maxOutputTokens ?? 2_000, temperature: request.temperature ?? 0 }),
      signal,
    });
    if (!response.ok) throw await toProviderError(response);
    const body = (await response.json()) as {
      content?: Array<{ type: string; text?: string }>;
      usage?: { input_tokens?: number; output_tokens?: number };
    };
    const text = body.content?.filter((item) => item.type === "text").map((item) => item.text ?? "").join("");
    if (!text) throw new AiProviderError("INVALID_RESPONSE", "Model returned no text", false);
    return {
      text,
      resolvedModel: request.model,
      usage: { inputTokens: body.usage?.input_tokens, outputTokens: body.usage?.output_tokens },
    };
  }
}

async function toProviderError(response: Response): Promise<AiProviderError> {
  const detail = (await response.text()).slice(0, 500);
  if (response.status === 401 || response.status === 403) return new AiProviderError("AUTH", detail, false, response.status);
  if (response.status === 429) return new AiProviderError("RATE_LIMIT", detail, true, response.status);
  if (response.status >= 500) return new AiProviderError("PROVIDER", detail, true, response.status);
  return new AiProviderError("INVALID_REQUEST", detail, false, response.status);
}

