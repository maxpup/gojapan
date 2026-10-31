import { ModelGateway, type ModelPolicy } from "./gateway";
import { AnthropicAdapter } from "./providers/anthropic";
import { OpenAiCompatibleAdapter } from "./providers/openai-compatible";
import type { AiProviderAdapter, ModelRef } from "./types";

export function createModelGateway(env: NodeJS.ProcessEnv = process.env): { gateway: ModelGateway; policy: ModelPolicy } {
  const adapters: AiProviderAdapter[] = [];
  if (env.OPENAI_API_KEY) {
    adapters.push(new OpenAiCompatibleAdapter({
      id: "openai",
      apiKey: env.OPENAI_API_KEY,
      baseUrl: env.OPENAI_BASE_URL ?? "https://api.openai.com/v1",
    }));
  }
  if (env.ANTHROPIC_API_KEY) adapters.push(new AnthropicAdapter(env.ANTHROPIC_API_KEY, env.ANTHROPIC_BASE_URL));

  const primary = env.GOJAPAN_AI_PRIMARY as ModelRef | undefined;
  if (!primary) throw new Error("GOJAPAN_AI_PRIMARY is required (provider/model)");
  const fallbacks = (env.GOJAPAN_AI_FALLBACKS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean) as ModelRef[];
  return { gateway: new ModelGateway(adapters), policy: { primary, fallbacks } };
}

