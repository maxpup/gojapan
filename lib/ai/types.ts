export type ModelRef = `${string}/${string}`;

export type AiMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type GenerateRequest = {
  model: ModelRef;
  messages: AiMessage[];
  temperature?: number;
  maxOutputTokens?: number;
  responseFormat?: "text" | "json";
};

export type GenerateResult = {
  text: string;
  resolvedModel: ModelRef;
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
  };
};

export type AiErrorCode =
  | "AUTH"
  | "QUOTA"
  | "RATE_LIMIT"
  | "TIMEOUT"
  | "PROVIDER"
  | "INVALID_REQUEST"
  | "INVALID_RESPONSE"
  | "UNAVAILABLE";

export class AiProviderError extends Error {
  constructor(
    public readonly code: AiErrorCode,
    message: string,
    public readonly retryable: boolean,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "AiProviderError";
  }
}

export interface AiProviderAdapter {
  readonly id: string;
  generate(request: GenerateRequest, signal?: AbortSignal): Promise<GenerateResult>;
}

