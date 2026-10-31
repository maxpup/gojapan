import { createModelGateway } from "../ai/config";
import { AiProviderError } from "../ai/types";
import { fetchPublicXiaohongshuSource, type ImportedSource } from "./xiaohongshu-source";

export type PlaceCardDraft = {
  name: string;
  type: "attraction" | "restaurant" | "cafe" | "hotel" | "shopping" | "transport" | "other";
  area?: string;
  address?: { zh?: string; ja?: string };
  summary?: string;
  recommendedItems: string[];
  tags: string[];
  confidence: number;
  evidence: string[];
};

export type ImportTravelNoteResult = {
  source: Pick<ImportedSource, "url" | "title" | "description"> & { mode: "url" | "pasted_text" };
  cards: PlaceCardDraft[];
  model: string;
  attempts: string[];
  needsReview: true;
};

export async function importTravelNote(input: { url?: string; text?: string }): Promise<ImportTravelNoteResult> {
  const pastedText = input.text?.trim();
  let source: ImportedSource;
  let mode: "url" | "pasted_text";
  if (pastedText) {
    if (pastedText.length > 40_000) throw new Error("正文最多 40000 个字符");
    source = { url: input.url ?? "user-pasted://xiaohongshu", text: pastedText };
    mode = "pasted_text";
  } else if (input.url) {
    source = await fetchPublicXiaohongshuSource(input.url);
    mode = "url";
  } else {
    throw new Error("请提供小红书公开链接或粘贴正文");
  }

  const { gateway, policy } = createModelGateway();
  const result = await gateway.generate(policy, {
    responseFormat: "json",
    temperature: 0,
    maxOutputTokens: 3_000,
    messages: [
      {
        role: "system",
        content: [
          "你是 GoJapan 的旅行攻略结构化引擎。只提取输入中有证据支持的日本旅行地点，不补写事实。",
          "返回严格 JSON：{\"cards\":[{\"name\":string,\"type\":\"attraction|restaurant|cafe|hotel|shopping|transport|other\",\"area\"?:string,\"address\"?:{\"zh\"?:string,\"ja\"?:string},\"summary\"?:string,\"recommendedItems\":string[],\"tags\":string[],\"confidence\":number,\"evidence\":string[]}]}",
          "confidence 必须为 0 到 1。evidence 每项必须是输入中的短片段。无法确认地点时返回空 cards。",
        ].join("\n"),
      },
      { role: "user", content: `来源：${source.url}\n\n${source.text}` },
    ],
  });
  const cards = parseCards(result.text);
  return {
    source: { url: source.url, title: source.title, description: source.description, mode },
    cards,
    model: result.resolvedModel,
    attempts: result.attempts,
    needsReview: true,
  };
}

function parseCards(raw: string): PlaceCardDraft[] {
  let value: unknown;
  try {
    value = JSON.parse(raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, ""));
  } catch {
    throw new AiProviderError("INVALID_RESPONSE", "模型未返回有效 JSON", false);
  }
  if (!isRecord(value) || !Array.isArray(value.cards)) throw new AiProviderError("INVALID_RESPONSE", "模型结果缺少 cards", false);
  if (value.cards.length > 30) throw new AiProviderError("INVALID_RESPONSE", "单次最多提取 30 个地点", false);
  return value.cards.map(validateCard);
}

function validateCard(value: unknown): PlaceCardDraft {
  if (!isRecord(value) || typeof value.name !== "string" || !value.name.trim()) throw new AiProviderError("INVALID_RESPONSE", "地点名称无效", false);
  const types = new Set(["attraction", "restaurant", "cafe", "hotel", "shopping", "transport", "other"]);
  if (typeof value.type !== "string" || !types.has(value.type)) throw new AiProviderError("INVALID_RESPONSE", "地点类型无效", false);
  const confidence = typeof value.confidence === "number" ? value.confidence : 0;
  if (confidence < 0 || confidence > 1) throw new AiProviderError("INVALID_RESPONSE", "置信度无效", false);
  return {
    name: value.name.trim().slice(0, 200),
    type: value.type as PlaceCardDraft["type"],
    area: optionalString(value.area),
    address: isRecord(value.address) ? { zh: optionalString(value.address.zh), ja: optionalString(value.address.ja) } : undefined,
    summary: optionalString(value.summary)?.slice(0, 1_000),
    recommendedItems: stringArray(value.recommendedItems, 20),
    tags: stringArray(value.tags, 20),
    evidence: stringArray(value.evidence, 10).map((item) => item.slice(0, 240)),
    confidence,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function stringArray(value: unknown, max: number): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string").slice(0, max) : [];
}

