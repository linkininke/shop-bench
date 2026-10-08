let state = null;
let currentWav = null;
let recoMode = "cf";

const $ = (id) => document.getElementById(id);

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[ch]));
}

function toast(message) {
  const el = $("toast");
  el.textContent = message;
  el.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove("show"), 2400);
}

async function api(url, options) {
  const res = await fetch(url, options);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || "请求失败");
  return data;
}

async function load() {
  state = await api("/api/bootstrap");
  const user = $("user");
  const selected = user.value;
  user.innerHTML = Object.entries(state.users)
    .map(([id, name]) => `<option value="${esc(id)}">${esc(name)}</option>`)
    .join("");
  if (selected) user.value = selected;
  const category = $("category");
  const selectedCategory = category.value;
  category.innerHTML = `<option value="">全部类目</option>` + state.categories
    .map((name) => `<option value="${esc(name)}">${esc(name)}</option>`).join("");
  if (selectedCategory) category.value = selectedCategory;
  renderAds();
  renderErp();
  if (!$("reco").hidden) renderReco();
}

function renderAds() {
  $("ads").innerHTML = state.ads.map((ad) => {
    const spent = Number(ad.spentYuan);
    const budget = Number(ad.budgetYuan);
    const used = budget > 0 ? Math.min(100, Math.round(spent / budget * 100)) : 0;
    return `
      <article class="ad">
        <div class="ad-top">
          <strong>${esc(ad.name)}</strong>
          <span class="pill ${ad.enabled ? "ok" : "low"}">${ad.enabled ? "投放中" : "已停"}</span>
        </div>
        <div class="meta">关键词 ${esc(ad.keywords.join("、"))}</div>
        <div class="metrics">
          <div><span>出价</span><b>${esc(ad.bidYuan)} 元</b></div>
          <div><span>预估点击率</span><b>${esc(ad.pctr)}%</b></div>
          <div><span>排序分</span><b>${esc(ad.rankScore)}</b></div>
        </div>
        <div class="bar" title="已花预算"><i style="width:${used}%"></i></div>
        <div class="meta">剩余预算 ${esc(ad.remainingYuan)} 元 · 点击 ${esc(ad.clicks)} · 展示 ${esc(ad.impressions)}</div>
        <div class="bid-row">
          <input data-bid-yuan="${ad.id}" value="${esc(ad.bidYuan)}" />
          <button type="button" data-bid-save="${ad.id}">保存出价</button>
          <button type="button" data-toggle="${ad.id}" data-on="${ad.enabled ? "1" : "0"}">${ad.enabled ? "暂停" : "恢复"}</button>
        </div>
      </article>`;
  }).join("");
}

function renderErp() {
  const low = state.alerts.length;
  const summary = state.summary;
  $("stats").innerHTML = `
    <div><span>销售额</span><b>${esc(summary.revenueYuan)}</b></div>
    <div><span>毛利</span><b>${esc(summary.profitYuan)}</b></div>
    <div><span>广告花费</span><b>${esc(summary.adSpentYuan)}</b></div>
    <div><span>低库存</span><b>${low}</b></div>`;
  $("alerts").innerHTML = low
    ? `<div class="banner">低库存：${state.alerts.map((p) => `${esc(p.name)} 剩 ${esc(p.stock)}`).join("，")}</div>`
    : `<div class="banner calm">目前没有低于预警线的商品</div>`;
  $("stock-body").innerHTML = state.products.map((p) => `
    <tr class="${p.lowStock ? "low" : ""}">
      <td><strong>${esc(p.name)}</strong><div class="meta">${esc(p.description)}</div></td>
      <td><span class="pill">${esc(p.category)}</span></td>
      <td class="price">${esc(p.priceYuan)}<div class="meta">成本 ${esc(p.costYuan)}</div></td>
      <td><span class="pill ${p.lowStock ? "low" : "ok"}">${esc(p.stock)}</span></td>
      <td>
        <button type="button" data-in="${p.id}">入库 10</button>
        <button type="button" data-out="${p.id}">卖出 1</button>
        <input data-count-input="${p.id}" value="${esc(p.stock)}" />
        <button type="button" data-count="${p.id}">按盘点改</button>
      </td>
    </tr>`).join("");
  $("logs").innerHTML = state.logs.slice(0, 8).map((log) => {
    const label = log.type === "PURCHASE_IN" ? "入库" : log.type === "ADJUST" ? "盘点" : "出库";
    const good = log.delta >= 0;
    return `<article class="log">
      <div><strong>${esc(log.skuName)}</strong><div class="meta">${esc(log.note)}</div></div>
      <span class="pill ${good ? "ok" : "low"}">${label} ${log.delta > 0 ? "+" : ""}${esc(log.delta)}</span>
    </article>`;
  }).join("") || `<div class="empty">还没有流水。入库或卖出后会出现在这里。</div>`;
}

async function renderReco() {
  const data = await api("/api/recommend?userId=" + encodeURIComponent($("user").value) + "&mode=" + recoMode);
  $("reco-list").innerHTML = data.items.map((item) => `
    <article class="card reco-card">
      <div class="meta">${esc(item.product.category)} · 库存 ${esc(item.product.stock)}</div>
      <div class="title">${esc(item.product.name)}</div>
      <p class="reason">${esc(item.reason)}</p>
      <div class="score">相关分 ${esc(item.score)} · ${esc(item.product.priceYuan)} 元</div>
    </article>`).join("") || `<div class="empty">这个顾客暂时没有推荐。</div>`;
}

function show(tab) {
  document.querySelectorAll("nav button").forEach((b) => b.classList.toggle("on", b.dataset.tab === tab));
  ["search", "reco", "erp", "media"].forEach((id) => $(id).hidden = id !== tab);
  if (tab === "reco") renderReco();
  if (tab === "media" && !$("recipes").childElementCount) loadRecipes();
}

document.querySelectorAll("nav button").forEach((btn) => {
  btn.onclick = () => show(btn.dataset.tab);
});

function drawWave(base64) {
  const canvas = $("wave");
  const ctx = canvas.getContext("2d");
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const view = new DataView(bytes.buffer);
  const start = 44;
  const samples = Math.floor((bytes.length - start) / 2);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#241f1b";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = "#e7a07a";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  const step = Math.max(1, Math.floor(samples / canvas.width));
  for (let x = 0; x < canvas.width; x++) {
    const i = Math.min(samples - 1, x * step);
    const sample = view.getInt16(start + i * 2, true) / 32768;
    const y = canvas.height / 2 - sample * (canvas.height / 2 - 8);
    if (x === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.stroke();
}

function renderResults(data) {
  if (!data.items.length) {
    $("results").innerHTML = `<div class="empty">没有匹配的商品。换个词，比如蜂蜜或围巾。</div>`;
    return;
  }
  $("results").innerHTML = data.items.map((item) => {
    const p = item.product;
    const sponsored = item.sponsored;
    const debug = sponsored
      ? `出价 ${esc(item.bidYuan)} 元 × 预估点击率 ${esc(item.pctr)}% = 排序分 ${esc(item.rankScore)}`
      : `BM25 ${esc(item.score)}`;
    return `<article class="card hit ${sponsored ? "ad-hit" : ""}">
      <div>
        ${sponsored ? `<span class="badge">广告</span>` : `<span class="pill">自然结果</span>`}
        <div class="title">${esc(p.name)}</div>
        <div class="meta">${esc(p.description)}</div>
        <div class="debug">${debug}</div>
        ${sponsored ? `<button type="button" data-click="${item.adId}">模拟点击扣费</button>` : ""}
      </div>
      <div>
        <div class="price">${esc(p.priceYuan)} 元</div>
        <div class="meta">库存 ${esc(p.stock)}</div>
      </div>
    </article>`;
  }).join("");
}

$("search-form").onsubmit = async (event) => {
  event.preventDefault();
  const params = new URLSearchParams({ q: $("q").value.trim(), category: $("category").value });
  const data = await api("/api/search?" + params.toString());
  renderResults(data);
  renderAuction(data.auction);
  await load();
};

document.body.addEventListener("click", async (event) => {
  const t = event.target.closest("button");
  if (!t) return;
  try {
    if (t.dataset.q) {
      $("q").value = t.dataset.q;
      $("search-form").requestSubmit();
    } else if (t.dataset.click) {
      await api("/api/ads/" + t.dataset.click + "/click", { method: "POST" });
      toast("已按出价扣了一次点击费用");
      await load();
      $("search-form").requestSubmit();
    } else if (t.dataset.bidSave) {
      const yuan = document.querySelector(`[data-bid-yuan="${t.dataset.bidSave}"]`).value;
      await api("/api/ads/" + t.dataset.bidSave + "/bid", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ bidCents: Math.round(Number(yuan) * 100) })
      });
      toast("出价已保存，再搜一次看排名");
      await load();
    } else if (t.dataset.toggle) {
      await api("/api/ads/" + t.dataset.toggle + "/enabled", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled: t.dataset.on !== "1" })
      });
      toast(t.dataset.on === "1" ? "广告已暂停" : "广告已恢复");
      await load();
    } else if (t.dataset.count) {
      const counted = Number(document.querySelector(`[data-count-input="${t.dataset.count}"]`).value);
      state = await api("/api/stock/adjust", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ skuId: Number(t.dataset.count), counted })
      });
      renderAds();
      renderErp();
      toast("已按盘点数量改库存");
    } else if (t.dataset.in) {
      state = await api("/api/stock/in", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ skuId: Number(t.dataset.in), qty: 10, note: "面试演示补货" })
      });
      renderAds();
      renderErp();
      toast("已入库 10 件");
    } else if (t.dataset.out) {
      state = await api("/api/stock/out", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ skuId: Number(t.dataset.out), qty: 1, userId: $("user").value || "u-li" })
      });
      renderAds();
      renderErp();
      toast("已卖出 1 件，并记了一条流水");
    }
  } catch (err) {
    toast(err.message);
  }
});

function renderAuction(rows) {
  if (!rows || !rows.length) {
    $("auction").innerHTML = `<div class="empty">输入关键词后，这里列出每条广告有没有命中、排序分是多少。</div>`;
    return;
  }
  $("auction").innerHTML = rows.map((ad) => `
    <article class="ad">
      <div class="ad-top">
        <strong>${esc(ad.name)}</strong>
        <span class="pill ${ad.matched ? "ok" : ""}">${ad.matched ? "命中关键词" : "这次不参与"}</span>
      </div>
      <div class="meta">出价 ${esc(ad.bidYuan)} 元 × 预估点击率 ${esc(ad.pctr)}% = 排序分 ${esc(ad.score)} · ${ad.enabled ? "可投放" : "已停"}</div>
    </article>`).join("");
}

$("user").onchange = () => { if (!$("reco").hidden) renderReco(); };
$("mode-cf").onclick = () => setRecoMode("cf");
$("mode-content").onclick = () => setRecoMode("content");
function setRecoMode(mode) {
  recoMode = mode;
  $("mode-cf").classList.toggle("primary", mode === "cf");
  $("mode-content").classList.toggle("primary", mode === "content");
  $("reco-note").textContent = mode === "cf"
    ? "协同过滤：看买过同样东西的人还买了什么。李强和王敏都买过醋，所以会推王敏还买的蜂蜜。"
    : "内容推荐：不看别人买了什么，只看类目和标题词是否接近。赵磊买过围巾，所以会推同一类的丝绸礼盒。";
  if (!$("reco").hidden) renderReco();
}
$("product-form").onsubmit = async (event) => {
  event.preventDefault();
  try {
    state = await api("/api/products", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: $("new-name").value,
        category: $("new-category").value,
        priceYuan: $("new-price").value,
        costYuan: $("new-cost").value,
        stock: $("new-stock").value,
        warnBelow: 5,
        description: "面试演示时新建的商品"
      })
    });
    renderAds();
    renderErp();
    toast("新品已建档，搜索索引已重建");
  } catch (err) {
    toast(err.message);
  }
};

$("reset").onclick = async () => {
  await api("/api/reset", { method: "POST" });
  currentWav = null;
  $("player").removeAttribute("src");
  $("meters").innerHTML = "";
  const ctx = $("wave").getContext("2d");
  ctx.fillStyle = "#241f1b";
  ctx.fillRect(0, 0, $("wave").width, $("wave").height);
  await load();
  $("results").innerHTML = `<div class="empty">样本已恢复。搜「蜂蜜」或「醋」看广告怎么插到结果里。</div>`;
  toast("样本数据已恢复");
};

function play(data) {
  currentWav = data.wavBase64;
  const info = data.info;
  $("meters").innerHTML = `
    <div><span>采样率</span><b>${esc(info.sampleRate)} Hz</b></div>
    <div><span>时长</span><b>${esc(info.durationSec)} 秒</b></div>
    <div><span>响度</span><b>${esc(info.rmsDb)} dB</b></div>
    <div><span>峰值</span><b>${esc(info.peak)}</b></div>`;
  $("player").src = "data:audio/wav;base64," + currentWav;
  drawWave(currentWav);
}

async function postMedia(url, extra) {
  try {
    play(await api(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(extra)
    }));
  } catch (err) {
    toast(err.message);
  }
}

$("make-tone").onclick = () => postMedia("/api/media/tone", {
  freq: Number($("freq").value),
  seconds: Number($("seconds").value),
  amplitude: Number($("amp").value)
});
$("make-gain").onclick = () => {
  if (!currentWav) return toast("先生成一段试听");
  postMedia("/api/media/gain", { wavBase64: currentWav, gain: 0.5 });
};
$("make-fade").onclick = () => {
  if (!currentWav) return toast("先生成一段试听");
  postMedia("/api/media/fade", { wavBase64: currentWav, fadeIn: 0.15, fadeOut: 0.15 });
};
$("make-trim").onclick = () => {
  if (!currentWav) return toast("先生成一段试听");
  postMedia("/api/media/trim", { wavBase64: currentWav, start: 0, duration: 0.4 });
};
$("make-mix").onclick = () => {
  if (!currentWav) return toast("先生成一段试听");
  postMedia("/api/media/mix", { wavBase64: currentWav, freq: 220 });
};

async function loadRecipes() {
  const names = { transcode: "转成网页能播的 MP4", thumbnail: "截一张封面", extractWav: "抽出 WAV", trim: "按时间裁一段" };
  const data = await api("/api/media/recipes");
  $("recipes").innerHTML = Object.entries(data).map(([key, cmd]) => `
    <article class="recipe">
      <strong>${names[key] || esc(key)}</strong>
      <code>${esc(cmd.join(" "))}</code>
    </article>`).join("");
}

$("results").innerHTML = `<div class="empty">搜「蜂蜜」看广告位，搜「围巾」看没有广告时的自然排序。</div>`;
load();
