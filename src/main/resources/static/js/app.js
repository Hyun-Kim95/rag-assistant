(function () {
  "use strict";

  const els = {
    banner: document.getElementById("error-banner"),
    bannerText: document.getElementById("error-banner-text"),
    bannerClose: document.getElementById("error-banner-close"),
    dropzone: document.getElementById("dropzone"),
    fileInput: document.getElementById("file-input"),
    pickFileBtn: document.getElementById("pick-file-btn"),
    docList: document.getElementById("doc-list"),
    chatThread: document.getElementById("chat-thread"),
    chatForm: document.getElementById("chat-form"),
    questionInput: document.getElementById("question-input"),
    sendBtn: document.getElementById("send-btn"),
    chatLoading: document.getElementById("chat-loading"),
  };

  let chatBusy = false;
  let uploadBusy = false;

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

  function setUploadBusy(busy) {
    uploadBusy = busy;
    els.pickFileBtn.disabled = busy;
    els.fileInput.disabled = busy;
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

  function appendUserMessage(text) {
    clearChatEmpty();
    const wrap = document.createElement("div");
    wrap.className = "message message--user";
    wrap.innerHTML =
      '<p class="message__label">질문</p>' +
      `<div class="message__bubble">${escapeHtml(text)}</div>`;
    els.chatThread.appendChild(wrap);
    els.chatThread.scrollTop = els.chatThread.scrollHeight;
  }

  function appendAssistantMessage(answer, grounded, sources) {
    const wrap = document.createElement("div");
    wrap.className =
      "message message--assistant" + (grounded ? "" : " message--no-answer");

    let html =
      '<p class="message__label">답변</p>' +
      `<div class="message__bubble">${escapeHtml(answer)}</div>`;

    if (grounded && sources && sources.length > 0) {
      html += '<div class="sources"><p class="sources__title">출처</p>';
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
      html += "</div>";
    } else if (!grounded) {
      html +=
        '<p class="sources__title" style="margin-top:12px">관련 출처 없음</p>';
    }

    wrap.innerHTML = html;
    els.chatThread.appendChild(wrap);
    els.chatThread.scrollTop = els.chatThread.scrollHeight;
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
        '<p class="doc-list__empty">업로드된 문서가 없습니다. TXT 또는 Markdown 파일을 추가하세요.</p>';
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
    setUploadBusy(true);

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

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question }),
      });
      if (!res.ok) {
        showError(await parseError(res));
        return;
      }
      const data = await res.json();
      appendAssistantMessage(data.answer, data.grounded, data.sources);
    } catch {
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
    e.preventDefault();
    els.dropzone.classList.add("is-dragover");
  });

  els.dropzone.addEventListener("dragleave", () => {
    els.dropzone.classList.remove("is-dragover");
  });

  els.dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    els.dropzone.classList.remove("is-dragover");
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
