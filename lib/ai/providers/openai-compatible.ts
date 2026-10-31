import { parseModelRef } from "../model-ref";
import { AiProviderError, type AiProviderAdapter, type GenerateRequest, type GenerateResult } from "../types";

type OpenAiCompatibleOptions = {
  id: string;
  apiKey: string;
  baseUrl: string;
};

export class OpenAiCompatibleAdapter implements AiProviderAdapter {
  readonly id: string;

  constructor(private readonly options: OpenAiCompatibleOptions) {
    this.id = options.id;
  }

  async generate(request: GenerateRequest, signal?: AbortSignal): Promise<GenerateResult> {
    const { model } = parseModelRef(request.model);
    const response = await fetch(`${this.options.baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${this.options.apiKey}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        model,
        messages: request.messages,
        temperature: request.temperature ?? 0,
        max_tokens: request.maxOutputTokens ?? 2_000,
        ...(request.responseFormat === "json" ? { response_format: { type: "json_object" } } : {}),
      }),
      signal,
    });

    if (!response.ok) throw await toProviderError(response);
    const body = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
      usage?: { prompt_tokens?: number; completion_tokens?: number };
    };
    const text = body.choices?.[0]?.message?.content;
    if (!text) throw new AiProviderError("INVALID_RESPONSE", "Model returned no content", false);
    return {
      text,
      resolvedModel: request.model,
      usage: { inputTokens: body.usage?.prompt_tokens, outputTokens: body.usage?.completion_tokens },
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

