(function () {
  "use strict";

  const els = {
    banner: document.getElementById("error-banner"),
    bannerText: document.getElementById("error-banner-text"),
    bannerClose: document.getElementById("error-banner-close"),
    dropzone: document.getElementById("dropzone"),
    fileInput: document.getElementById("file-input"),
    pickFileBtn: document.getElementById("pick-file-btn"),
    uploadLoading: document.getElementById("upload-loading"),
    uploadLoadingText: document.getElementById("upload-loading-text"),
    docList: document.getElementById("doc-list"),
    chatThread: document.getElementById("chat-thread"),
    chatForm: document.getElementById("chat-form"),
    questionInput: document.getElementById("question-input"),
    sendBtn: document.getElementById("send-btn"),
    chatLoading: document.getElementById("chat-loading"),
  };

  let chatBusy = false;
  let uploadBusy = false;

  /** 하단 고정 판정 여유(px) — 이보다 위면 사용자가 스크롤 올린 것으로 간주 */
  const SCROLL_PIN_THRESHOLD = 48;

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

  function formatLength(n) {
    return `${Number(n).toLocaleString("ko-KR")}자`;
  }

  function formatScore(score) {
    return Number(score).toFixed(2);
  }

  function setUploadBusy(busy, fileName) {
    uploadBusy = busy;
    els.pickFileBtn.disabled = busy;
    els.fileInput.disabled = busy;
    els.dropzone.classList.toggle("is-uploading", busy);
    els.uploadLoading.classList.toggle("is-visible", busy);
    els.uploadLoading.setAttribute("aria-busy", busy ? "true" : "false");

    if (busy) {
        els.uploadLoadingText.textContent = fileName
          ? `업로드 중… ${fileName}`
          : "업로드 중…";
      els.pickFileBtn.innerHTML =
        '<span class="spinner" aria-hidden="true"></span> 업로드 중';
    } else {
      els.uploadLoadingText.textContent = "업로드 중…";
      els.pickFileBtn.textContent = "파일 선택";
    }
  }

  function setChatBusy(busy) {
    chatBusy = busy;
    els.questionInput.disabled = busy;
    els.sendBtn.disabled = busy;
    els.chatLoading.classList.toggle("is-visible", busy);
    if (busy) {
      els.sendBtn.innerHTML = '<span class="spinner" aria-hidden="true"></span> 전송 중';
    } else {
      els.sendBtn.textContent = "전송";
    }
  }

  function clearChatEmpty() {
    const empty = els.chatThread.querySelector(".chat-empty");
    if (empty) {
      empty.remove();
    }
  }

  function isChatPinnedToBottom() {
    const el = els.chatThread;
    return el.scrollHeight - el.scrollTop - el.clientHeight <= SCROLL_PIN_THRESHOLD;
  }

  function scrollChatToBottom(force) {
    if (force || isChatPinnedToBottom()) {
      els.chatThread.scrollTop = els.chatThread.scrollHeight;
    }
  }

  function appendUserMessage(text) {
    clearChatEmpty();
    const wrap = document.createElement("div");
    wrap.className = "message message--user";
    wrap.innerHTML =
      '<p class="message__label">질문</p>' +
      `<div class="message__bubble">${escapeHtml(text)}</div>`;
    els.chatThread.appendChild(wrap);
    scrollChatToBottom(true);
  }

  /** 스트리밍용: 빈 assistant 말풍선 + 출처 자리만 먼저 만든다 */
  function appendAssistantShell() {
    clearChatEmpty();
    const wrap = document.createElement("div");
    wrap.className = "message message--assistant";
    wrap.innerHTML =
      '<p class="message__label">답변</p>' +
      '<div class="message__bubble message__bubble--streaming"></div>' +
      '<div class="sources-host"></div>';
    els.chatThread.appendChild(wrap);
    scrollChatToBottom(true);
    return {
      wrap,
      bubble: wrap.querySelector(".message__bubble"),
      sourcesHost: wrap.querySelector(".sources-host"),
    };
  }

  function buildSourceCardsHtml(sources) {
    let html = "";
    for (const src of sources) {
      html +=
        '<article class="source-card">' +
        '<div class="source-card__head">' +
        `<span class="source-card__name">${escapeHtml(src.documentName)}</span>` +
        `<span class="source-card__score">Score: ${formatScore(src.score)}</span>` +
        "</div>" +
        `<p class="source-card__snippet">${escapeHtml(src.snippet || "")}</p>` +
        "</article>";
    }
    return html;
  }

  /** 출처: 기본 접힘 — done 후 레이아웃 점프 완화 */
  function renderSourcesInto(host, grounded, sources) {
    host.innerHTML = "";
    const message = host.closest(".message");
    if (grounded && sources && sources.length > 0) {
      const count = sources.length;
      host.innerHTML =
        '<div class="sources sources--collapsed">' +
        '<button type="button" class="sources-toggle" aria-expanded="false">' +
        `<span class="sources-toggle__label">출처 ${count}건</span>` +
        '<span class="sources-toggle__chevron" aria-hidden="true">▼</span>' +
        "</button>" +
        '<div class="sources-panel" hidden>' +
        buildSourceCardsHtml(sources) +
        "</div></div>";
      const panel = host.querySelector(".sources-panel");
      if (panel) {
        panel.hidden = true;
      }
      message.classList.remove("message--no-answer");
    } else if (!grounded) {
      host.innerHTML =
        '<p class="sources__title sources__title--standalone">관련 출처 없음</p>';
      message.classList.add("message--no-answer");
    }
  }

  function toggleSourcesPanel(toggleBtn) {
    const sources = toggleBtn.closest(".sources");
    const panel = sources.querySelector(".sources-panel");
    const expanded = !sources.classList.contains("sources--expanded");
    sources.classList.toggle("sources--expanded", expanded);
    sources.classList.toggle("sources--collapsed", !expanded);
    toggleBtn.setAttribute("aria-expanded", expanded ? "true" : "false");
    panel.hidden = !expanded;
    if (expanded && isChatPinnedToBottom()) {
      scrollChatToBottom(true);
    }
  }

  /**
   * SSE(text/event-stream) 버퍼 파싱 — 이벤트 블록은 빈 줄(\n\n)로 구분
   * @returns 남은 미완성 버퍼
   */
  function parseSseChunk(buffer, onEvent) {
    const parts = buffer.split("\n\n");
    const rest = parts.pop() || "";
    for (const block of parts) {
      if (!block.trim()) {
        continue;
      }
      let eventName = "message";
      let dataLine = "";
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataLine += line.slice(5).trim();
        }
      }
      if (dataLine) {
        try {
          onEvent(eventName, JSON.parse(dataLine));
        } catch {
          /* 잘못된 JSON 블록 무시 */
        }
      }
    }
    return rest;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderDocuments(documents) {
    els.docList.innerHTML = "";
    if (!documents || documents.length === 0) {
      els.docList.innerHTML =
        '<p class="doc-list__empty">업로드된 문서가 없습니다. TXT, Markdown, PDF 파일을 추가하세요.</p>';
      return;
    }

    for (const doc of documents) {
      const item = document.createElement("div");
      item.className = "doc-item";
      item.dataset.id = String(doc.id);
      item.innerHTML =
        '<span class="doc-item__icon" aria-hidden="true">📄</span>' +
        '<div class="doc-item__meta">' +
        `<p class="doc-item__name">${escapeHtml(doc.name)}</p>` +
        `<p class="doc-item__length">${formatLength(doc.textLength)}</p>` +
        "</div>" +
        `<button type="button" class="btn btn--ghost doc-delete" aria-label="${escapeHtml(doc.name)} 삭제">삭제</button>`;
      els.docList.appendChild(item);
    }
  }

  async function loadDocuments() {
    try {
      const res = await fetch("/api/documents");
      if (!res.ok) {
        showError(await parseError(res));
        return;
      }
      const data = await res.json();
      renderDocuments(data.documents);
    } catch {
      showError("문서 목록을 불러올 수 없습니다. 서버가 실행 중인지 확인하세요.");
    }
  }

  async function uploadFile(file) {
    if (!file || uploadBusy) {
      return;
    }

    hideError();
    setUploadBusy(true, file.name);

    const form = new FormData();
    form.append("file", file);

    try {
      const res = await fetch("/api/documents/upload", {
        method: "POST",
        body: form,
      });
      if (!res.ok) {
        showError(await parseError(res));
        return;
      }
      await loadDocuments();
    } catch {
      showError("파일 업로드에 실패했습니다.");
    } finally {
      setUploadBusy(false);
      els.fileInput.value = "";
    }
  }

  async function deleteDocument(id) {
    hideError();
    try {
      const res = await fetch(`/api/documents/${id}`, { method: "DELETE" });
      if (!res.ok && res.status !== 204) {
        showError(await parseError(res));
        return;
      }
      await loadDocuments();
    } catch {
      showError("문서 삭제에 실패했습니다.");
    }
  }

  async function sendQuestion(question) {
    if (!question || chatBusy) {
      return;
    }

    hideError();
    setChatBusy(true);
    appendUserMessage(question);
    els.questionInput.value = "";

    const shell = appendAssistantShell();
    els.chatLoading.classList.remove("is-visible");
    let buffer = "";
    let streamStarted = false;

    try {
      const res = await fetch("/api/chat/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question }),
      });

      if (!res.ok) {
        shell.wrap.remove();
        showError(await parseError(res));
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        buffer = parseSseChunk(buffer, (eventName, data) => {
          if (eventName === "delta" && data.text) {
            if (!streamStarted) {
              streamStarted = true;
              shell.bubble.classList.add("message__bubble--streaming");
            }
            shell.bubble.textContent += data.text;
            scrollChatToBottom(false);
          }
          if (eventName === "done") {
            shell.bubble.classList.remove("message__bubble--streaming");
            if (data.answer != null) {
              shell.bubble.textContent = data.answer;
            }
            renderSourcesInto(shell.sourcesHost, data.grounded, data.sources);
          }
        });
      }
    } catch {
      shell.wrap.remove();
      showError("질문 전송에 실패했습니다.");
    } finally {
      setChatBusy(false);
      els.questionInput.focus();
    }
  }

  els.bannerClose.addEventListener("click", hideError);

  els.pickFileBtn.addEventListener("click", () => els.fileInput.click());

  els.fileInput.addEventListener("change", (e) => {
    const file = e.target.files && e.target.files[0];
    if (file) {
      uploadFile(file);
    }
  });

  els.dropzone.addEventListener("dragover", (e) => {
    if (uploadBusy) {
      return;
    }
    e.preventDefault();
    els.dropzone.classList.add("is-dragover");
  });

  els.dropzone.addEventListener("dragleave", () => {
    els.dropzone.classList.remove("is-dragover");
  });

  els.dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    els.dropzone.classList.remove("is-dragover");
    if (uploadBusy) {
      return;
    }
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) {
      uploadFile(file);
    }
  });

  els.docList.addEventListener("click", (e) => {
    const btn = e.target.closest(".doc-delete");
    if (!btn) {
      return;
    }
    const item = btn.closest(".doc-item");
    const id = item && item.dataset.id;
    if (id) {
      deleteDocument(id);
    }
  });

  els.chatThread.addEventListener("click", (e) => {
    const toggle = e.target.closest(".sources-toggle");
    if (toggle) {
      toggleSourcesPanel(toggle);
    }
  });

  els.chatForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const question = els.questionInput.value.trim();
    if (!question) {
      showError("질문을 입력하세요.");
      return;
    }
    sendQuestion(question);
  });

  loadDocuments();
})();
