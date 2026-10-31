"use client";

import { useEffect, useRef, useState, type ClipboardEvent } from "react";

const places = [
  { name: "浅草寺", time: "09:00", kind: "寺院", tip: "从雷门慢慢走进仲见世通。", nearby: "合羽桥道具街" },
  { name: "隅田公园", time: "10:30", kind: "散步", tip: "沿河散步，记得看看天气。", nearby: "吾妻桥" },
  { name: "上野公园", time: "12:00", kind: "公园", tip: "想看博物馆，先确认开放情况。", nearby: "不忍池" },
  { name: "阿美横丁", time: "14:00", kind: "逛街", tip: "午餐和购物可以放在这一段。", nearby: "上野御徒町" },
  { name: "东京站", time: "16:00", kind: "交通", tip: "换乘与找出口都预留些时间。", nearby: "KITTE 丸之内" },
];

const dayNotes = [
  "浅草和上野步行较多，穿舒适的鞋。",
  "出发前确认博物馆与店铺的营业时间。",
  "东京站换乘时预留找路时间。",
];

type TextStyle = "hand" | "poster" | "stamp";
type JournalEntry = {
  id: string;
  kind: "photo" | "text";
  text: string;
  style: TextStyle;
  createdAt: number;
  blob?: Blob;
  src?: string;
};

const mapsSearch = (query: string) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;

const createId = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

function openJournal(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("gojapan-journal-demo", 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains("entries")) {
        request.result.createObjectStore("entries", { keyPath: "id" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function readEntries(): Promise<JournalEntry[]> {
  const db = await openJournal();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("entries", "readonly");
    const request = transaction.objectStore("entries").getAll();
    request.onsuccess = () => resolve((request.result as JournalEntry[]).sort((a, b) => a.createdAt - b.createdAt));
    request.onerror = () => reject(request.error);
    transaction.oncomplete = () => db.close();
  });
}

async function saveEntry(entry: JournalEntry): Promise<void> {
  const db = await openJournal();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("entries", "readwrite");
    const { src: _src, ...stored } = entry;
    void _src;
    transaction.objectStore("entries").put(stored);
    transaction.oncomplete = () => { db.close(); resolve(); };
    transaction.onerror = () => { db.close(); reject(transaction.error); };
  });
}

async function removeEntry(id: string): Promise<void> {
  const db = await openJournal();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("entries", "readwrite");
    transaction.objectStore("entries").delete(id);
    transaction.oncomplete = () => { db.close(); resolve(); };
    transaction.onerror = () => { db.close(); reject(transaction.error); };
  });
}

function MiniMap({ selected, onSelect }: { selected: number; onSelect: (index: number) => void }) {
  return <div className="mini-map" aria-label="东京行程地图示意">
    <div className="map-river" /><div className="map-road road-one" /><div className="map-road road-two" />
    <span className="map-area a">ASAKUSA</span><span className="map-area b">UENO</span>
    {places.map((place, index) => <button key={place.name} type="button" className={`map-pin pin-${index + 1}${selected === index ? " current" : ""}`} onClick={() => onSelect(index)} aria-label={`选择${place.name}`}>{index + 1}</button>)}
    <span className="map-demo-label">地图示意</span>
  </div>;
}

export function TripConcepts() {
  const [mode, setMode] = useState<"plan" | "record">("plan");
  const [mapOpen, setMapOpen] = useState(false);
  const [selected, setSelected] = useState(2);
  const [search, setSearch] = useState("");
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [status, setStatus] = useState("");
  const fileInput = useRef<HTMLInputElement>(null);
  const urls = useRef<string[]>([]);
  const cardRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    let mounted = true;
    readEntries().then(items => {
      if (!mounted) return;
      const restored = items.map(item => {
        if (!item.blob) return item;
        const src = URL.createObjectURL(item.blob);
        urls.current.push(src);
        return { ...item, src };
      });
      setEntries(current => {
        const currentIds = new Set(current.map(item => item.id));
        return [...restored.filter(item => !currentIds.has(item.id)), ...current].sort((a, b) => a.createdAt - b.createdAt);
      });
    }).catch(() => setStatus("记录暂时只能保存在当前页面。"));
    return () => { mounted = false; urls.current.forEach(url => URL.revokeObjectURL(url)); };
  }, []);

  function selectPlace(index: number) {
    setSelected(index);
    cardRefs.current[index]?.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
  }

  async function addPhotos(files: FileList | File[]) {
    const images = Array.from(files).filter(file => file.type.startsWith("image/"));
    if (!images.length) return;
    const next = images.map(file => {
      const src = URL.createObjectURL(file);
      urls.current.push(src);
      return { id: createId(), kind: "photo" as const, text: "", style: "hand" as const, createdAt: Date.now() + Math.random(), blob: file, src };
    });
    setEntries(current => [...current, ...next]);
    const results = await Promise.allSettled(next.map(saveEntry));
    setStatus(results.some(result => result.status === "rejected") ? "照片已贴上，但保存失败；刷新后可能消失。" : `${next.length} 张照片已贴入手账。`);
  }

  async function addText() {
    const entry: JournalEntry = { id: createId(), kind: "text", text: "", style: "hand", createdAt: Date.now() };
    setEntries(current => [...current, entry]);
    try { await saveEntry(entry); setStatus("文字便签已加入。" ); }
    catch { setStatus("文字已加入，但暂时无法保存。" ); }
  }

  function updateEntry(id: string, patch: Partial<JournalEntry>) {
    setEntries(current => current.map(entry => entry.id === id ? { ...entry, ...patch } : entry));
  }

  async function persistEntry(id: string, patch: Partial<JournalEntry>) {
    const entry = entries.find(item => item.id === id);
    if (!entry) return;
    try { await saveEntry({ ...entry, ...patch }); setStatus("手账已保存。" ); }
    catch { setStatus("保存失败，请稍后再试。" ); }
  }

  async function deleteEntry(id: string) {
    const item = entries.find(entry => entry.id === id);
    if (!item) return;
    setEntries(current => current.filter(entry => entry.id !== id));
    try { await removeEntry(id); setStatus("已移除这张贴纸。" ); }
    catch { setEntries(current => [...current, item].sort((a, b) => a.createdAt - b.createdAt)); setStatus("移除失败，请重试。" ); }
  }

  function handlePaste(event: ClipboardEvent<HTMLElement>) {
    if (mode !== "record") return;
    const images = Array.from(event.clipboardData.files).filter(file => file.type.startsWith("image/"));
    if (images.length) { event.preventDefault(); void addPhotos(images); }
  }

  const visiblePlaces = places.map((place, index) => ({ ...place, index })).filter(place => place.name.includes(search.trim()));
  const place = places[selected];

  return <main className="trip-app">
    <div className="book-page">
      <header className="book-header">
        <div className="book-brand"><span>G</span><b>goJapan</b></div>
        <span className="book-date">东京 · 10月18日</span>
      </header>

      <div className="book-heading"><span>MY TRAVEL BOOK / 01</span><h1>东京的一天<span>✳</span></h1></div>

      <nav className="mode-tabs" aria-label="行程用途">
        <button type="button" className={mode === "plan" ? "active" : ""} aria-current={mode === "plan" ? "page" : undefined} onClick={() => setMode("plan")}>计划 <small>PLAN</small></button>
        <button type="button" className={mode === "record" ? "active" : ""} aria-current={mode === "record" ? "page" : undefined} onClick={() => setMode("record")}>记录 <small>JOURNAL</small></button>
      </nav>

      <section className="map-fold">
        <button type="button" className="map-fold-toggle" aria-expanded={mapOpen} onClick={() => setMapOpen(value => !value)}><span>⌖ 地点地图</span><span>{mapOpen ? "收起 −" : "展开 +"}</span></button>
        {mapOpen && <div className="map-fold-content"><MiniMap selected={selected} onSelect={selectPlace} /><a href={mapsSearch(`${place.name} 东京`)} target="_blank" rel="noopener noreferrer">在 Google 地图打开 {place.name} ↗</a></div>}
      </section>

      {mode === "plan" ? <section className="plan-page" aria-label="今日计划">
        <div className="section-heading"><div><span>01 / BEFORE YOU GO</span><h2>今日计划</h2></div><b>{places.length} 站</b></div>
        <div className="plan-notes"><b>出发前看一眼</b>{dayNotes.map(note => <span key={note}>• {note}</span>)}</div>
        <div className="place-search"><span>⌕</span><input value={search} onChange={event => setSearch(event.target.value)} aria-label="搜索行程地点" placeholder="搜索今天的地点" />{search && <button type="button" onClick={() => setSearch("")} aria-label="清空搜索">×</button>}</div>
        <div className="place-carousel" aria-label="横向滚动查看地点">{visiblePlaces.length ? visiblePlaces.map(item => <button key={item.name} ref={element => { cardRefs.current[item.index] = element; }} type="button" className={`place-card${selected === item.index ? " selected" : ""}`} aria-pressed={selected === item.index} onClick={() => selectPlace(item.index)}><span className="card-number">{String(item.index + 1).padStart(2, "0")}</span><b>{item.name}</b><small>{item.time} · {item.kind}</small></button>) : <p className="no-results">没有匹配的地点</p>}</div>
        <div className="place-tip"><span>✦ {place.name} Tips</span><p>{place.tip} 附近：{place.nearby}。</p></div>
      </section> : <section className="journal-page" aria-label="旅行记录" onPaste={handlePaste} tabIndex={-1}>
        <div className="section-heading"><div><span>02 / COLLECT THE MOMENTS</span><h2>旅行手账</h2></div><b>{entries.length} 张贴纸</b></div>
        <div className="journal-actions"><button type="button" onClick={() => fileInput.current?.click()}>＋ 贴照片</button><button type="button" onClick={() => void addText()}>✎ 加艺术文字</button><small>也可以在这里粘贴图片</small></div>
        <input ref={fileInput} type="file" accept="image/*" multiple className="visually-hidden" onChange={event => { const files = event.target.files ? Array.from(event.target.files) : []; event.target.value = ""; if (files.length) void addPhotos(files); }} />
        <div className="journal-board">
          {entries.length === 0 && <div className="journal-empty"><div className="empty-photo"><span>＋</span><p>把旅途照片贴进来</p></div><div className="empty-letter"><i>Dear Tokyo,</i><p>写下今天想记住的一句话。</p><span>✳</span></div></div>}
          {entries.map((entry, index) => <article className={`journal-item ${entry.kind === "photo" ? "photo-item" : `text-item style-${entry.style}`} angle-${index % 3}`} key={entry.id}>
            <span className="washi-tape" aria-hidden="true" />
            <button type="button" className="remove-item" aria-label="移除这张贴纸" onClick={() => void deleteEntry(entry.id)}>×</button>
            {entry.kind === "photo" ? <><img src={entry.src} alt={entry.text || "旅行照片"} /><input aria-label="照片说明" placeholder="写一句照片说明…" value={entry.text} onChange={event => updateEntry(entry.id, { text: event.target.value })} onBlur={event => void persistEntry(entry.id, { text: event.target.value })} /></> : <><div className="style-picker" aria-label="文字风格">{(["hand", "poster", "stamp"] as const).map(style => <button type="button" key={style} className={entry.style === style ? "active" : ""} onClick={() => { updateEntry(entry.id, { style }); void persistEntry(entry.id, { style }); }}>{({ hand: "手写", poster: "海报", stamp: "印章" })[style]}</button>)}</div><textarea aria-label="艺术文字内容" placeholder="写下这一刻…" value={entry.text} onChange={event => updateEntry(entry.id, { text: event.target.value })} onBlur={event => void persistEntry(entry.id, { text: event.target.value })} /></>}
          </article>)}
        </div>
      </section>}
      {status && <div className="save-status" role="status">{status}</div>}
      <footer className="book-footer">GOJAPAN · TRAVEL MEMORY</footer>
    </div>
  </main>;
}
