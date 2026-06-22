const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const AndroidSpeech = window.AndroidSpeech;

const elements = {
  supportBadge: document.querySelector("#supportBadge"),
  micStatus: document.querySelector("#micStatus"),
  listenStatus: document.querySelector("#listenStatus"),
  testMicButton: document.querySelector("#testMicButton"),
  stopMicButton: document.querySelector("#stopMicButton"),
  startButton: document.querySelector("#startButton"),
  pauseButton: document.querySelector("#pauseButton"),
  resetButton: document.querySelector("#resetButton"),
  scriptInput: document.querySelector("#scriptInput"),
  promptDisplay: document.querySelector("#promptDisplay"),
  transcriptOutput: document.querySelector("#transcriptOutput"),
  volumeBar: document.querySelector("#volumeBar"),
  progressBar: document.querySelector("#progressBar"),
  wordCount: document.querySelector("#wordCount"),
  fontSizeInput: document.querySelector("#fontSizeInput"),
  scrollSpeedInput: document.querySelector("#scrollSpeedInput"),
  stepMic: document.querySelector("#stepMic"),
  stepRead: document.querySelector("#stepRead"),
};

let micStream = null;
let audioContext = null;
let analyser = null;
let animationFrameId = null;
let recognition = null;
let isListening = false;
let isAndroidListening = false;
let scriptChars = [];
let normalizedScript = "";
let readIndex = 0;
let lastMatchedTranscript = "";

function normalizeText(value) {
  return value
    .toLowerCase()
    .replace(/[，。！？、；：“”‘’《》（）【】,.!?;:"'()[\]\s]/g, "");
}

function renderScript() {
  const text = elements.scriptInput.value.trim();
  scriptChars = Array.from(text);
  normalizedScript = normalizeText(text);
  readIndex = Math.min(readIndex, normalizedScript.length);
  elements.wordCount.textContent = `${scriptChars.filter((char) => normalizeText(char)).length} 字`;
  elements.promptDisplay.innerHTML = "";

  scriptChars.forEach((char, visualIndex) => {
    const span = document.createElement("span");
    span.className = `char${char.trim() ? "" : " is-space"}`;
    span.dataset.visualIndex = String(visualIndex);
    span.dataset.normalized = normalizeText(char);
    span.textContent = char === "\n" ? "\n" : char;
    elements.promptDisplay.appendChild(span);
  });

  updateHighlight();
}

function normalizedIndexToVisualIndex(targetIndex) {
  let normalizedCount = 0;
  const spans = elements.promptDisplay.querySelectorAll(".char");

  for (const span of spans) {
    const normalized = span.dataset.normalized;
    if (!normalized) {
      continue;
    }
    normalizedCount += normalized.length;
    if (normalizedCount >= targetIndex) {
      return Number(span.dataset.visualIndex);
    }
  }

  return scriptChars.length - 1;
}

function updateHighlight() {
  const currentVisualIndex = normalizedIndexToVisualIndex(readIndex + 1);
  let normalizedCount = 0;
  const spans = elements.promptDisplay.querySelectorAll(".char");

  spans.forEach((span) => {
    const normalized = span.dataset.normalized;
    if (normalized) {
      normalizedCount += normalized.length;
    }

    const isRead = normalizedCount <= readIndex && normalizedCount > 0;
    const isCurrent = Number(span.dataset.visualIndex) === currentVisualIndex && readIndex < normalizedScript.length;
    span.classList.toggle("is-read", isRead);
    span.classList.toggle("is-current", isCurrent);
  });

  const progress = normalizedScript.length ? (readIndex / normalizedScript.length) * 100 : 0;
  elements.progressBar.style.width = `${Math.min(progress, 100)}%`;
  keepCurrentLineVisible();
}

function keepCurrentLineVisible() {
  const current = elements.promptDisplay.querySelector(".char.is-current");
  const speed = Number(elements.scrollSpeedInput.value);
  if (!current || speed === 0) {
    return;
  }

  const displayRect = elements.promptDisplay.getBoundingClientRect();
  const currentRect = current.getBoundingClientRect();
  const targetTop = displayRect.top + displayRect.height * 0.38;

  if (currentRect.top > targetTop || currentRect.top < displayRect.top + 80) {
    const delta = currentRect.top - targetTop;
    elements.promptDisplay.scrollBy({ top: delta * speed, behavior: "smooth" });
  }
}

function findBestProgress(transcript) {
  const normalizedTranscript = normalizeText(transcript);
  if (!normalizedTranscript) {
    return readIndex;
  }

  const searchStart = Math.max(0, readIndex - 8);
  const forwardScript = normalizedScript.slice(searchStart);
  let bestIndex = readIndex;

  for (let length = Math.min(normalizedTranscript.length, 18); length >= 2; length -= 1) {
    const snippet = normalizedTranscript.slice(-length);
    const foundAt = forwardScript.indexOf(snippet);
    if (foundAt >= 0) {
      bestIndex = Math.max(bestIndex, searchStart + foundAt + length);
      break;
    }
  }

  if (normalizedScript.slice(readIndex).startsWith(normalizedTranscript.slice(-1))) {
    bestIndex = Math.max(bestIndex, readIndex + 1);
  }

  return Math.min(bestIndex, normalizedScript.length);
}

async function startMicTest() {
  if (AndroidSpeech) {
    elements.micStatus.textContent = "正在请求安卓麦克风权限";
    elements.testMicButton.disabled = true;
    AndroidSpeech.testMic();
    return;
  }

  try {
    stopMicTest();
    micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new AudioContext();
    const source = audioContext.createMediaStreamSource(micStream);
    analyser = audioContext.createAnalyser();
    analyser.fftSize = 1024;
    source.connect(analyser);

    elements.micStatus.textContent = "正在监听音量";
    elements.testMicButton.disabled = true;
    elements.stopMicButton.disabled = false;
    elements.startButton.disabled = !SpeechRecognition;
    elements.stepMic.classList.remove("is-active");
    elements.stepRead.classList.add("is-active");
    drawVolume();
  } catch (error) {
    elements.micStatus.textContent = "无法访问麦克风";
    elements.transcriptOutput.textContent = "请确认浏览器已允许麦克风权限，并尽量通过 localhost 或 HTTPS 打开页面。";
  }
}

function drawVolume() {
  if (!analyser) {
    return;
  }

  const samples = new Uint8Array(analyser.frequencyBinCount);
  analyser.getByteTimeDomainData(samples);
  const peak = samples.reduce((max, value) => Math.max(max, Math.abs(value - 128)), 0);
  const percent = Math.min(100, Math.round((peak / 60) * 100));
  elements.volumeBar.style.width = `${percent}%`;
  animationFrameId = requestAnimationFrame(drawVolume);
}

function stopMicTest() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
  if (micStream) {
    micStream.getTracks().forEach((track) => track.stop());
    micStream = null;
  }
  if (audioContext) {
    audioContext.close();
    audioContext = null;
  }
  analyser = null;
  elements.volumeBar.style.width = "0%";
  elements.testMicButton.disabled = false;
  elements.stopMicButton.disabled = true;
  if (elements.micStatus.textContent === "正在监听音量") {
    elements.micStatus.textContent = "测试已通过";
  }
}

function createRecognition() {
  if (!SpeechRecognition) {
    return null;
  }

  const instance = new SpeechRecognition();
  instance.lang = "zh-CN";
  instance.continuous = true;
  instance.interimResults = true;

  instance.onstart = () => {
    isListening = true;
    elements.listenStatus.textContent = "正在跟读";
    elements.startButton.disabled = true;
    elements.pauseButton.disabled = false;
  };

  instance.onresult = (event) => {
    let transcript = "";
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index][0].transcript;
    }

    lastMatchedTranscript = transcript || lastMatchedTranscript;
    elements.transcriptOutput.textContent = lastMatchedTranscript || "正在等待语音...";
    readIndex = findBestProgress(lastMatchedTranscript);
    updateHighlight();
  };

  instance.onerror = (event) => {
    elements.listenStatus.textContent = "识别出错";
    elements.transcriptOutput.textContent = `语音识别异常：${event.error}`;
  };

  instance.onend = () => {
    isListening = false;
    elements.startButton.disabled = !SpeechRecognition;
    elements.pauseButton.disabled = true;
    if (readIndex >= normalizedScript.length && normalizedScript.length > 0) {
      elements.listenStatus.textContent = "已完成";
    } else if (elements.listenStatus.textContent !== "识别出错") {
      elements.listenStatus.textContent = "已暂停";
    }
  };

  return instance;
}

function startAndroidReading() {
  if (!AndroidSpeech) {
    return;
  }

  stopMicTest();
  renderScript();
  isAndroidListening = true;
  lastMatchedTranscript = "";
  elements.listenStatus.textContent = "正在跟读";
  elements.startButton.disabled = true;
  elements.pauseButton.disabled = false;
  AndroidSpeech.start();
}

function startReading() {
  if (AndroidSpeech) {
    startAndroidReading();
    return;
  }

  if (!SpeechRecognition) {
    return;
  }

  stopMicTest();
  renderScript();
  recognition = createRecognition();
  lastMatchedTranscript = "";
  recognition.start();
}

function pauseReading() {
  if (AndroidSpeech && isAndroidListening) {
    AndroidSpeech.stop();
    isAndroidListening = false;
    elements.listenStatus.textContent = "已暂停";
    elements.startButton.disabled = false;
    elements.pauseButton.disabled = true;
    return;
  }

  if (recognition && isListening) {
    recognition.stop();
  }
}

function resetReading() {
  if (AndroidSpeech && isAndroidListening) {
    AndroidSpeech.stop();
    isAndroidListening = false;
  }
  if (recognition && isListening) {
    recognition.stop();
  }
  readIndex = 0;
  lastMatchedTranscript = "";
  elements.transcriptOutput.textContent = "识别结果会显示在这里。";
  elements.listenStatus.textContent = "等待开始";
  elements.promptDisplay.scrollTop = 0;
  updateHighlight();
}

function updateSupportState() {
  const hasMic = Boolean(navigator.mediaDevices?.getUserMedia);
  if (AndroidSpeech) {
    elements.supportBadge.textContent = "安卓可用";
    elements.supportBadge.classList.remove("is-warning");
    elements.testMicButton.disabled = false;
    elements.stopMicButton.disabled = true;
    elements.micStatus.textContent = "等待安卓权限测试";
    elements.startButton.disabled = false;
    return;
  }

  if (SpeechRecognition && hasMic) {
    elements.supportBadge.textContent = "浏览器可用";
    elements.supportBadge.classList.remove("is-warning");
  } else {
    elements.supportBadge.textContent = "需换浏览器";
    elements.supportBadge.classList.add("is-warning");
    elements.listenStatus.textContent = "当前浏览器不支持语音识别";
    elements.testMicButton.disabled = !hasMic;
    elements.startButton.disabled = true;
  }
}

window.onAndroidSpeechResult = (transcript) => {
  lastMatchedTranscript = transcript || lastMatchedTranscript;
  elements.transcriptOutput.textContent = lastMatchedTranscript || "正在等待语音...";
  readIndex = findBestProgress(lastMatchedTranscript);
  updateHighlight();
};

window.onAndroidSpeechError = (message) => {
  isAndroidListening = false;
  elements.listenStatus.textContent = "识别出错";
  elements.transcriptOutput.textContent = `安卓语音识别异常：${message}`;
  elements.startButton.disabled = false;
  elements.pauseButton.disabled = true;
};

window.onAndroidSpeechEnd = () => {
  isAndroidListening = false;
  elements.startButton.disabled = false;
  elements.pauseButton.disabled = true;
  elements.listenStatus.textContent = readIndex >= normalizedScript.length ? "已完成" : "已暂停";
};

window.onAndroidMicTestResult = (message) => {
  elements.micStatus.textContent = message;
  elements.testMicButton.disabled = false;
  elements.stopMicButton.disabled = true;
  elements.stepMic.classList.remove("is-active");
  elements.stepRead.classList.add("is-active");
};

elements.testMicButton.addEventListener("click", startMicTest);
elements.stopMicButton.addEventListener("click", stopMicTest);
elements.startButton.addEventListener("click", startReading);
elements.pauseButton.addEventListener("click", pauseReading);
elements.resetButton.addEventListener("click", resetReading);
elements.scriptInput.addEventListener("input", renderScript);
elements.fontSizeInput.addEventListener("input", () => {
  elements.promptDisplay.style.fontSize = `${elements.fontSizeInput.value}px`;
});

window.addEventListener("beforeunload", () => {
  stopMicTest();
  if (recognition && isListening) {
    recognition.stop();
  }
});

updateSupportState();
renderScript();
