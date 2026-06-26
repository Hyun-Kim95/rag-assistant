(function () {
    "use strict";

    const els = {
        state: document.getElementById("call-state"),
        partial: document.getElementById("partial"),
        thread: document.getElementById("voice-thread"),
        startBtn: document.getElementById("start-btn"),
        stopBtn: document.getElementById("stop-btn"),
        unsupported: document.getElementById("unsupported"),
    };

    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
        els.unsupported.hidden = false;
        els.startBtn.disabled = true;
    }

    let ws = null;
    let recog = null;
    let callActive = false;
    let speaking = false;
    let sttBlocked = false;   // STT가 환경적으로 불가(network/권한) → 재시작 루프 차단
    let currentAnswerEl = null;
    let currentAudio = null;
    let awaitingAnswer = false;   // THINKING~answer.done 사이: 오디오(filler)가 끝나도 LISTENING 대신 THINKING 유지

    function setState(s) {
        els.state.textContent = s;
        els.state.dataset.state = s;
    }

    function appendMsg(role, text) {
        const el = document.createElement("div");
        el.className = `vmsg vmsg--${role}`;
        el.textContent = text;
        els.thread.appendChild(el);
        el.scrollIntoView({ block: "end" });
        return el;
    }

    // --- WebSocket ---
    function connect() {
        const proto = location.protocol === "https:" ? "wss" : "ws";
        ws = new WebSocket(`${proto}://${location.host}/ws/voice`);
        ws.binaryType = "arraybuffer";
        ws.onmessage = (e) => {
            if (typeof e.data === "string") {
                handleEvent(JSON.parse(e.data));   // 제어/텍스트 이벤트
            } else {
                playAudio(e.data);                 // Google TTS MP3(ArrayBuffer)
            }
        };
        ws.onclose = () => { if (callActive) setState("DISCONNECTED"); };
        ws.onerror = () => setState("ERROR");
    }

    function handleEvent(ev) {
        switch (ev.event) {
            case "state":
                setState(ev.state);
                if (ev.state === "THINKING") awaitingAnswer = true;
                break;
            case "answer.delta":
                if (!currentAnswerEl) currentAnswerEl = appendMsg("assistant", "");
                currentAnswerEl.textContent += ev.text;
                break;
            case "answer.done":
                awaitingAnswer = false;   // 본 답 도착 → 이후 오디오가 끝나면 LISTENING으로
                if (!currentAnswerEl) currentAnswerEl = appendMsg("assistant", "");
                currentAnswerEl.textContent = ev.answer || currentAnswerEl.textContent;
                currentAnswerEl = null;
                // 재생은 tts.audio(바이너리) 또는 tts.fallback에서 처리
                break;
            case "notice":
                // 검색 시작 안내(filler): 자막만 표시. 음성은 서버가 본 답변과 같은 엔진으로
                // 보낸다(Google TTS 바이너리, 또는 비활성 시 tts.fallback → 브라우저 TTS).
                appendMsg("system", ev.text);
                break;
            case "tts.fallback":
                speak(ev.text);   // Google 비활성/실패 → 브라우저 TTS 강등
                break;
            case "handoff":
                appendMsg("system", `상담원에게 연결합니다 (${ev.reason})`);
                break;
            case "error":
                appendMsg("system", ev.message || "오류가 발생했습니다.");
                break;
        }
    }

    // --- STT (Web Speech API, 무료·브라우저 내장) ---
    function initRecognition() {
        recog = new SR();
        recog.lang = "ko-KR";
        recog.continuous = true;
        recog.interimResults = true;

        let speechStartAt = 0;   // stt_ms 측정: 말소리 시작 시각
        recog.onspeechstart = () => { speechStartAt = performance.now(); };

        recog.onresult = (e) => {
            let interim = "";
            let finalText = "";
            for (let i = e.resultIndex; i < e.results.length; i++) {
                const r = e.results[i];
                if (r.isFinal) finalText += r[0].transcript;
                else interim += r[0].transcript;
            }
            if (interim) {
                els.partial.textContent = interim;
                if (speaking) bargeIn();   // 봇 발화 중 사용자 끼어듦 → TTS 중단
            }
            if (finalText.trim()) {
                els.partial.textContent = "";
                if (speaking) bargeIn();
                appendMsg("user", finalText.trim());
                const sttMs = speechStartAt ? Math.round(performance.now() - speechStartAt) : 0;
                speechStartAt = 0;
                ws.send(JSON.stringify({ type: "user_utterance", text: finalText.trim(), sttMs }));
            }
        };

        // 일부 브라우저는 무음 시 자동 종료 → 통화 중이면 재시작 (단 STT 불가 환경이면 멈춤)
        recog.onend = () => { if (callActive && !sttBlocked) try { recog.start(); } catch (_) {} };
        recog.onerror = (e) => {
            if (e.error === "not-allowed" || e.error === "service-not-allowed") {
                sttBlocked = true; stopCall();
                appendMsg("system", "마이크 권한이 필요합니다. 브라우저 주소창의 권한 설정에서 마이크를 허용해 주세요.");
            } else if (e.error === "network") {
                sttBlocked = true; stopCall();
                appendMsg("system", "이 브라우저에서는 음성 인식이 제한됩니다(network). Cursor 내장 브라우저 대신 일반 Chrome 또는 Edge에서 http://localhost:8080/voice.html 을 열어 주세요.");
            }
            // no-speech/aborted 등 일시적 오류는 onend의 재시작에 맡긴다.
        };
    }

    // TTS로 읽기 전 마크다운·코드 서식·기호를 사람이 듣기 좋은 형태로 정제
    function cleanForSpeech(text) {
        if (!text) return "";
        return text
            .replace(/```/g, " ")               // 코드펜스 표시 제거
            .replace(/`/g, "")                  // 인라인 백틱 제거
            .replace(/\*\*([^*]*)\*\*/g, "$1")  // **굵게**
            .replace(/__([^_]*)__/g, "$1")      // __굵게__
            .replace(/\[(.*?)]\(.*?\)/g, "$1") // [텍스트](링크) → 텍스트
            .replace(/^\s*[-*•]\s+/gm, "")      // 줄머리 불릿
            .replace(/[#>*_~]/g, " ")           // 남은 마크다운 기호
            .replace(/[/\\]/g, " ")             // 슬래시·역슬래시 → 공백 (v1/chat → v1 chat)
            .replace(/@/g, " ")                 // @Primary → Primary
            .replace(/[ \t]{2,}/g, " ")         // 중복 공백
            .replace(/\n{2,}/g, ". ")           // 빈 줄 → 문장 끊기
            .trim();
    }

    // --- 오디오 재생 (Google TTS 바이너리 MP3) ---
    function playAudio(arrayBuffer) {
        stopPlayback();
        const blob = new Blob([arrayBuffer], { type: "audio/mp3" });
        const url = URL.createObjectURL(blob);
        currentAudio = new Audio(url);
        speaking = true;
        setState("SPEAKING");
        currentAudio.onended = () => {
            speaking = false; currentAudio = null;
            URL.revokeObjectURL(url);
            setState(awaitingAnswer ? "THINKING" : "LISTENING");
        };
        currentAudio.onerror = () => { speaking = false; currentAudio = null; setState(awaitingAnswer ? "THINKING" : "LISTENING"); };
        currentAudio.play().catch(() => { speaking = false; setState(awaitingAnswer ? "THINKING" : "LISTENING"); });
    }

    function stopPlayback() {
        if (currentAudio) { currentAudio.pause(); currentAudio = null; }
        if (window.speechSynthesis) window.speechSynthesis.cancel();
    }

    // --- TTS (speechSynthesis 폴백, 무료·브라우저 내장) ---
    function speak(text) {
        const clean = cleanForSpeech(text);
        if (!clean || !window.speechSynthesis) return;
        const u = new SpeechSynthesisUtterance(clean);
        u.lang = "ko-KR";
        u.onstart = () => { speaking = true; };
        u.onend = () => { speaking = false; setState(awaitingAnswer ? "THINKING" : "LISTENING"); };
        window.speechSynthesis.speak(u);
    }

    function bargeIn() {
        stopPlayback();
        speaking = false;
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: "barge_in" }));
        }
    }

    // --- 통화 제어 ---
    function startCall() {
        if (callActive) return;
        callActive = true;
        sttBlocked = false;
        els.startBtn.disabled = true;
        els.stopBtn.disabled = false;
        connect();
        initRecognition();
        try { recog.start(); } catch (_) {}
        setState("LISTENING");
    }

    function stopCall() {
        callActive = false;
        els.startBtn.disabled = false;
        els.stopBtn.disabled = true;
        if (recog) try { recog.stop(); } catch (_) {}
        if (ws) ws.close();
        stopPlayback();
        speaking = false;
        setState("IDLE");
    }

    els.startBtn.addEventListener("click", startCall);
    els.stopBtn.addEventListener("click", stopCall);
})();
