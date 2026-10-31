import assert from "node:assert/strict";
import test from "node:test";
import { fetchPublicXiaohongshuSource } from "../lib/import/xiaohongshu-source";

test("extracts traceable metadata and visible text from a public page", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });
  globalThis.fetch = async () => new Response(`
    <html><head>
      <meta property="og:title" content="涩谷三家咖啡店">
      <meta property="og:description" content="东京旅行收藏">
    </head><body><main>第一家：Example Coffee，位于涩谷。</main></body></html>
  `, { headers: { "content-type": "text/html; charset=utf-8" } });

  const source = await fetchPublicXiaohongshuSource("https://www.xiaohongshu.com/explore/demo#tracking");
  assert.equal(source.url, "https://www.xiaohongshu.com/explore/demo");
  assert.equal(source.title, "涩谷三家咖啡店");
  assert.match(source.text, /Example Coffee/);
});

test("rejects non-Xiaohongshu URLs before fetching", async () => {
  await assert.rejects(
    fetchPublicXiaohongshuSource("https://example.com/private"),
    /只支持小红书 HTTPS 公开链接/,
  );
});

test("validates every redirect target", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });
  globalThis.fetch = async () => new Response(null, {
    status: 302,
    headers: { location: "https://example.com/steal" },
  });

  await assert.rejects(
    fetchPublicXiaohongshuSource("https://xhslink.com/demo"),
    /只支持小红书 HTTPS 公开链接/,
  );
});

