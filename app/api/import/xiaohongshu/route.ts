import { AiProviderError } from "../../../../lib/ai/types";
import { importTravelNote } from "../../../../lib/import/import-travel-note";

export const runtime = "edge";

export async function POST(request: Request): Promise<Response> {
  try {
    const body = (await request.json()) as { url?: unknown; text?: unknown };
    const url = typeof body.url === "string" ? body.url : undefined;
    const text = typeof body.text === "string" ? body.text : undefined;
    const result = await importTravelNote({ url, text });
    return Response.json(result, { status: 200 });
  } catch (error) {
    const aiError = error instanceof AiProviderError ? error : undefined;
    const message = aiError ? publicAiErrorMessage(aiError) : error instanceof Error ? error.message : "导入失败";
    const status = aiError?.code === "AUTH" ? 503 : aiError ? 502 : 422;
    return Response.json(
      {
        error: aiError?.code ?? "IMPORT_FAILED",
        message,
        suggestion: "若公开链接无法读取，请复制正文后重新导入；不要提交账号密码或 Cookie。",
      },
      { status },
    );
  }
}

function publicAiErrorMessage(error: AiProviderError): string {
  switch (error.code) {
    case "AUTH": return "模型服务尚未正确配置";
    case "RATE_LIMIT": return "模型服务繁忙，请稍后重试";
    case "TIMEOUT": return "模型处理超时，请重试";
    case "INVALID_RESPONSE": return "模型结果未通过结构校验，请重试或更换模型";
    default: return "模型服务暂时不可用";
  }
}
