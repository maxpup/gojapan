const ALLOWED_HOSTS = new Set(["xiaohongshu.com", "www.xiaohongshu.com", "xhslink.com"]);
const MAX_BYTES = 2_000_000;
const MAX_REDIRECTS = 4;

export type ImportedSource = {
  url: string;
  title?: string;
  description?: string;
  text: string;
};

export async function fetchPublicXiaohongshuSource(rawUrl: string): Promise<ImportedSource> {
  let current = validateUrl(rawUrl);
  for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects += 1) {
    const response = await fetch(current, {
      redirect: "manual",
      headers: {
        accept: "text/html,application/xhtml+xml",
        "user-agent": "GoJapanImportBot/0.1 (+user-initiated public-link import)",
      },
    });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) throw new Error("小红书链接重定向缺少目标地址");
      current = validateUrl(new URL(location, current).toString());
      continue;
    }
    if (!response.ok) throw new Error(`公开页面读取失败（HTTP ${response.status}）`);
    const contentType = response.headers.get("content-type") ?? "";
    if (!contentType.includes("text/html")) throw new Error("链接不是可读取的公开网页");
    const declaredLength = Number(response.headers.get("content-length") ?? 0);
    if (declaredLength > MAX_BYTES) throw new Error("页面内容过大");
    const html = (await response.text()).slice(0, MAX_BYTES);
    return extractSource(html, current.toString());
  }
  throw new Error("小红书链接重定向次数过多");
}

function validateUrl(rawUrl: string): URL {
  const url = new URL(rawUrl);
  const hostname = url.hostname.toLowerCase().replace(/\.$/, "");
  if (url.protocol !== "https:" || !ALLOWED_HOSTS.has(hostname)) {
    throw new Error("只支持小红书 HTTPS 公开链接");
  }
  url.username = "";
  url.password = "";
  url.hash = "";
  return url;
}

function extractSource(html: string, url: string): ImportedSource {
  const title = meta(html, "og:title") ?? tagText(html, "title");
  const description = meta(html, "og:description") ?? meta(html, "description", "name");
  const visible = decodeEntities(
    html
      .replace(/<script[\s\S]*?<\/script>/gi, " ")
      .replace(/<style[\s\S]*?<\/style>/gi, " ")
      .replace(/<[^>]+>/g, " ")
      .replace(/\s+/g, " ")
      .trim(),
  );
  const text = [title, description, visible].filter(Boolean).join("\n").slice(0, 40_000);
  if (text.length < 20) throw new Error("公开页面没有足够内容，请改为粘贴正文或分享截图");
  return { url, title, description, text };
}

function meta(html: string, key: string, attribute = "property"): string | undefined {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const patterns = [
    new RegExp(`<meta[^>]+${attribute}=["']${escaped}["'][^>]+content=["']([^"']*)["'][^>]*>`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+${attribute}=["']${escaped}["'][^>]*>`, "i"),
  ];
  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match?.[1]) return decodeEntities(match[1].trim());
  }
}

function tagText(html: string, tag: string): string | undefined {
  const match = html.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\/${tag}>`, "i"));
  return match?.[1] ? decodeEntities(match[1].replace(/<[^>]+>/g, " ").trim()) : undefined;
}

function decodeEntities(value: string): string {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code: string) => String.fromCodePoint(Number(code)));
}

