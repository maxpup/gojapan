import type { ModelRef } from "./types";

export function parseModelRef(value: string): { provider: string; model: string; ref: ModelRef } {
  const slash = value.indexOf("/");
  if (slash <= 0 || slash === value.length - 1) {
    throw new Error(`Invalid model ref: ${value}. Expected provider/model.`);
  }
  return {
    provider: value.slice(0, slash),
    model: value.slice(slash + 1),
    ref: value as ModelRef,
  };
}

