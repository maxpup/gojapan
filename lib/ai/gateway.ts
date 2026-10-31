import { parseModelRef } from "./model-ref";
import { AiProviderError, type AiProviderAdapter, type GenerateRequest, type GenerateResult, type ModelRef } from "./types";

export type ModelPolicy = { primary: ModelRef; fallbacks: ModelRef[] };

export class ModelGateway {
  private readonly providers = new Map<string, AiProviderAdapter>();

  constructor(adapters: AiProviderAdapter[]) {
    for (const adapter of adapters) this.providers.set(adapter.id, adapter);
  }

  async generate(
    policy: ModelPolicy,
    input: Omit<GenerateRequest, "model">,
    options: { timeoutMs?: number } = {},
  ): Promise<GenerateResult & { attempts: ModelRef[] }> {
    const candidates = [policy.primary, ...policy.fallbacks];
    const attempts: ModelRef[] = [];
    let lastError: unknown;

    for (const model of candidates) {
      const { provider } = parseModelRef(model);
      const adapter = this.providers.get(provider);
      if (!adapter) {
        lastError = new AiProviderError("UNAVAILABLE", `Provider ${provider} is not configured`, true);
        continue;
      }
      attempts.push(model);
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 30_000);
      try {
        const result = await adapter.generate({ ...input, model }, controller.signal);
        return { ...result, attempts };
      } catch (error) {
        lastError = error instanceof DOMException && error.name === "AbortError"
          ? new AiProviderError("TIMEOUT", "Model request timed out", true)
          : error;
        if (lastError instanceof AiProviderError && !lastError.retryable) throw lastError;
      } finally {
        clearTimeout(timeout);
      }
    }
    throw lastError ?? new AiProviderError("UNAVAILABLE", "No model is configured", true);
  }
}

