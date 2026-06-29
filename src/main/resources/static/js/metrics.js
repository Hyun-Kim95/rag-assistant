(function () {
  "use strict";

  const els = {
    banner: document.getElementById("error-banner"),
    bannerText: document.getElementById("error-banner-text"),
    bannerClose: document.getElementById("error-banner-close"),
    form: document.getElementById("filter-form"),
    channel: document.getElementById("channel-select"),
    from: document.getElementById("from-input"),
    to: document.getElementById("to-input"),
    provider: document.getElementById("provider-input"),
    refreshBtn: document.getElementById("refresh-btn"),
    stateLoading: document.getElementById("state-loading"),
    stateEmpty: document.getElementById("state-empty"),
    stateDisabled: document.getElementById("state-disabled"),
    content: document.getElementById("metrics-content"),
    rangeCaption: document.getElementById("range-caption"),
    providerTbody: document.getElementById("provider-tbody"),
    bucket: document.getElementById("bucket-select"),
    driftEmpty: document.getElementById("drift-empty"),
    driftCharts: document.getElementById("drift-charts"),
  };

  const SVG_NS = "http://www.w3.org/2000/svg";

  let busy = false;

  function showError(message) {
    els.bannerText.textContent = message;
    els.banner.classList.add("is-visible");
  }

  function hideError() {
    els.banner.classList.remove("is-visible");
  }

  async function parseError(response) {
    try {
      const data = await response.json();
      return data.message || data.error || response.statusText;
    } catch {
      return response.statusText || "요청에 실패했습니다.";
    }
  }

  // null/undefined는 빈 상태("–")로. 비율은 % 1자리, 정수/토큰은 천단위, 비용은 유효숫자.
  function pct(v) {
    return v == null ? "–" : `${(v * 100).toFixed(1)}%`;
  }

  function intMs(v) {
    return v == null ? "–" : Math.round(v).toLocaleString("ko-KR");
  }

  function tokens(v) {
    return v == null ? "–" : Math.round(v).toLocaleString("ko-KR");
  }

  function cost(v) {
    if (v == null) return "–";
    if (v === 0) return "0";
    // 작은 비용도 보이도록 유효숫자 우선 표기
    return v < 0.01 ? v.toPrecision(3) : v.toFixed(4);
  }

  function setText(id, value) {
    document.getElementById(id).textContent = value;
  }

  function setBusy(b) {
    busy = b;
    els.refreshBtn.disabled = b;
    els.stateLoading.classList.toggle("is-visible", b);
    els.stateLoading.setAttribute("aria-busy", b ? "true" : "false");
    if (b) {
      els.refreshBtn.innerHTML = '<span class="spinner" aria-hidden="true"></span> 조회 중';
    } else {
      els.refreshBtn.textContent = "조회";
    }
  }

  function showOnly(state) {
    // state: "loading"(별도 토글) | "empty" | "disabled" | "content" | "none"
    els.stateEmpty.classList.toggle("is-visible", state === "empty");
    els.stateDisabled.classList.toggle("is-visible", state === "disabled");
    els.content.hidden = state !== "content";
  }

  function buildQuery() {
    const params = new URLSearchParams();
    if (els.channel.value) params.set("channel", els.channel.value);
    if (els.from.value) params.set("from", `${els.from.value}:00`);
    if (els.to.value) params.set("to", `${els.to.value}:00`);
    const provider = els.provider.value.trim();
    if (provider) params.set("provider", provider);
    return params;
  }

  function renderProviderTable(byProvider) {
    els.providerTbody.innerHTML = "";
    const entries = byProvider ? Object.entries(byProvider) : [];
    if (entries.length === 0) {
      const tr = document.createElement("tr");
      const td = document.createElement("td");
      td.colSpan = 3;
      td.className = "kpi__sub";
      td.textContent = "데이터 없음";
      tr.appendChild(td);
      els.providerTbody.appendChild(tr);
      return;
    }
    for (const [name, info] of entries) {
      const tr = document.createElement("tr");
      const cells = [
        { text: name, cls: "" },
        { text: (info.interactions ?? 0).toLocaleString("ko-KR"), cls: "num" },
        { text: cost(info.costPerInteraction), cls: "num" },
      ];
      for (const c of cells) {
        const td = document.createElement("td");
        td.textContent = c.text;
        if (c.cls) td.className = c.cls;
        tr.appendChild(td);
      }
      els.providerTbody.appendChild(tr);
    }
  }

  function render(data) {
    const q = data.quality || {};
    const lat = data.latency || {};
    const total = lat.total || {};
    const stage = lat.stage || {};
    const c = data.cost || {};
    const tk = c.tokensPerInteraction || {};
    const rel = data.reliability || {};
    const ns = data.northStar || {};
    const counts = data.counts || {};

    if (data.range) {
      els.rangeCaption.textContent = `기간: ${data.range.from} ~ ${data.range.to} · 채널: ${data.channel}`;
    }

    setText("ns-chat", pct(ns.chat ? ns.chat.value : null));
    setText("ns-voice", pct(ns.voice ? ns.voice.value : null));

    setText("m-interactions", (counts.interactions ?? 0).toLocaleString("ko-KR"));
    setText("m-sessions", (counts.sessions ?? 0).toLocaleString("ko-KR"));
    setText("m-grounded", pct(q.groundedRate));
    setText("m-noanswer", pct(q.noAnswerRate));

    setText("m-p50", intMs(total.p50));
    setText("m-p95", intMs(total.p95));
    setText("m-p99", intMs(total.p99));
    setText("m-embed", intMs(stage.embedP95));
    setText("m-retrieve", intMs(stage.retrieveP95));
    setText("m-rerank", intMs(stage.rerankP95));
    setText("m-gen", intMs(stage.genP95));

    setText("m-tokens-avg", tokens(tk.avg));
    setText("m-tokens-p95", tk.p95 == null ? "" : `P95 ${tokens(tk.p95)}`);
    setText("m-cost", cost(c.costPerInteraction));
    setText("m-currency", c.currency ? c.currency : "");
    renderProviderTable(c.byProvider);

    setText("m-handoff", pct(rel.handoffRate));
    setText("m-completion", pct(rel.taskCompletionRate));

    // 빈 상태: chat/agent 인터랙션 0 AND voice 세션 0 이면 데이터 없음으로 본다.
    const noData = (counts.interactions ?? 0) === 0 && (counts.sessions ?? 0) === 0;
    showOnly(noData ? "empty" : "content");
  }

  // ── 추이(드리프트) 인라인 SVG 라인차트 ──
  function svgEl(name, attrs) {
    const el = document.createElementNS(SVG_NS, name);
    for (const k in attrs) el.setAttribute(k, attrs[k]);
    return el;
  }

  // points: 전체 버킷 배열, accessor: 값 추출(null 허용). null 값은 선에서 끊지 않고 건너뛴다.
  function buildSpark(points, accessor, color) {
    const W = 300, H = 72, pad = 8;
    const svg = svgEl("svg", {
      class: "chart-card__svg",
      viewBox: `0 0 ${W} ${H}`,
      preserveAspectRatio: "none",
      role: "img",
    });
    const ys = points.map(accessor);
    const valid = ys.filter((v) => v != null && !Number.isNaN(v));
    if (valid.length === 0) return null;

    const min = Math.min(...valid);
    const max = Math.max(...valid);
    const range = max - min || 1;
    const n = ys.length;
    const xstep = n > 1 ? (W - 2 * pad) / (n - 1) : 0;
    const toXY = (v, i) => {
      const x = n > 1 ? pad + i * xstep : W / 2;
      const y = H - pad - ((v - min) / range) * (H - 2 * pad);
      return [x, y];
    };

    const coords = [];
    let lastXY = null;
    ys.forEach((v, i) => {
      if (v == null || Number.isNaN(v)) return;
      const [x, y] = toXY(v, i);
      coords.push(`${x.toFixed(1)},${y.toFixed(1)}`);
      lastXY = [x, y];
    });

    svg.appendChild(svgEl("line", {
      x1: pad, y1: H - pad, x2: W - pad, y2: H - pad,
      stroke: "#e5e7eb", "stroke-width": 1,
    }));
    if (coords.length === 1) {
      svg.appendChild(svgEl("circle", { cx: lastXY[0], cy: lastXY[1], r: 3, fill: color }));
    } else {
      svg.appendChild(svgEl("polyline", { class: "spark-line", stroke: color, points: coords.join(" ") }));
    }
    if (lastXY) {
      svg.appendChild(svgEl("circle", { class: "spark-dot-last", cx: lastXY[0], cy: lastXY[1], r: 2.5, fill: color }));
    }
    return svg;
  }

  function lastValue(points, accessor) {
    for (let i = points.length - 1; i >= 0; i--) {
      const v = accessor(points[i]);
      if (v != null && !Number.isNaN(v)) return v;
    }
    return null;
  }

  function chartCard(title, points, accessor, fmt, color) {
    const card = document.createElement("div");
    card.className = "chart-card";

    const head = document.createElement("div");
    head.className = "chart-card__head";
    const t = document.createElement("span");
    t.className = "chart-card__title";
    t.textContent = title;
    const val = document.createElement("span");
    val.className = "chart-card__value";
    val.textContent = fmt(lastValue(points, accessor));
    head.append(t, val);
    card.appendChild(head);

    const spark = buildSpark(points, accessor, color);
    if (spark) {
      card.appendChild(spark);
    } else {
      const empty = document.createElement("div");
      empty.className = "chart-card__empty";
      empty.textContent = "데이터 없음";
      card.appendChild(empty);
    }

    if (points.length > 0) {
      const axis = document.createElement("div");
      axis.className = "chart-card__axis";
      const first = document.createElement("span");
      first.textContent = shortBucket(points[0].bucketStart);
      const last = document.createElement("span");
      last.textContent = shortBucket(points[points.length - 1].bucketStart);
      axis.append(first, last);
      card.appendChild(axis);
    }
    return card;
  }

  function shortBucket(iso) {
    if (!iso) return "";
    // 2026-06-29T00:00 → 06-29 00시 (시간 버킷일 때만 시각 표시)
    const d = iso.slice(5, 10);
    const h = iso.slice(11, 13);
    return h && h !== "00" ? `${d} ${h}시` : d;
  }

  function renderDrift(data) {
    const points = (data && data.points) || [];
    els.driftCharts.innerHTML = "";
    if (points.length === 0) {
      els.driftEmpty.classList.add("is-visible");
      return;
    }
    els.driftEmpty.classList.remove("is-visible");
    const cards = [
      chartCard("인터랙션", points, (p) => p.interactions, (v) => (v == null ? "–" : tokens(v)), "#2563eb"),
      chartCard("grounded 비율", points, (p) => p.groundedRate, pct, "#16a34a"),
      chartCard("no-answer 비율", points, (p) => p.noAnswerRate, pct, "#b91c1c"),
      chartCard("P95 지연(ms)", points, (p) => p.p95, intMs, "#7c3aed"),
      chartCard("인터랙션당 토큰", points, (p) => p.avgTokens, tokens, "#0891b2"),
      chartCard("검색 top score", points, (p) => p.avgTopScore, (v) => (v == null ? "–" : v.toFixed(3)), "#ca8a04"),
    ];
    cards.forEach((c) => els.driftCharts.appendChild(c));
  }

  async function loadSummary() {
    const res = await fetch(`/api/metrics/summary?${buildQuery()}`, {
      headers: { Accept: "application/json" },
    });
    if (res.status === 404) {
      showOnly("disabled");
      return false;
    }
    if (!res.ok) {
      throw new Error(await parseError(res));
    }
    render(await res.json());
    return true;
  }

  async function loadDrift() {
    const params = buildQuery();
    params.set("bucket", els.bucket.value);
    const res = await fetch(`/api/metrics/timeseries?${params}`, {
      headers: { Accept: "application/json" },
    });
    if (res.status === 404) return; // 비활성은 요약에서 이미 처리
    if (!res.ok) {
      throw new Error(await parseError(res));
    }
    renderDrift(await res.json());
  }

  async function load() {
    if (busy) return;
    hideError();
    showOnly("none");
    setBusy(true);
    try {
      const ok = await loadSummary();
      if (ok) {
        await loadDrift();
      }
    } catch (err) {
      showOnly("none");
      showError(err && err.message ? err.message : "지표를 불러오지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function reloadDriftOnly() {
    if (busy) return;
    hideError();
    setBusy(true);
    try {
      await loadDrift();
    } catch (err) {
      showError(err && err.message ? err.message : "추이를 불러오지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  els.form.addEventListener("submit", (e) => {
    e.preventDefault();
    load();
  });
  els.bucket.addEventListener("change", reloadDriftOnly);
  els.bannerClose.addEventListener("click", hideError);

  load();
})();
