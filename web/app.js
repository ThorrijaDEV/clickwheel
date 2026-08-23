/* Console — local music player. No uploads, no accounts, no network. */
(() => {
  "use strict";

  const audio = document.getElementById("audio");
  const els = {
    title: document.getElementById("np-title"),
    artist: document.getElementById("np-artist"),
    play: document.getElementById("btn-play"),
    prev: document.getElementById("btn-prev"),
    next: document.getElementById("btn-next"),
    shuffle: document.getElementById("btn-shuffle"),
    repeat: document.getElementById("btn-repeat"),
    seek: document.getElementById("seek"),
    volume: document.getElementById("volume"),
    tCur: document.getElementById("t-cur"),
    tEnd: document.getElementById("t-end"),
    list: document.getElementById("track-list"),
    empty: document.getElementById("lib-empty"),
    folder: document.getElementById("folder"),
  };

  let library = [];
  let queue = [];
  let queueIndex = -1;
  let shuffle = false;
  let repeatMode = "off"; // off | all | one
  let seeking = false;

  const AUDIO_RE = /\.(mp3|flac|m4a|aac|ogg|opus|wav|webm)$/i;

  function fmt(s) {
    if (!Number.isFinite(s)) return "0:00";
    s = Math.max(0, Math.floor(s));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
  }

  function nameFromFile(file) {
    const base = file.name.replace(AUDIO_RE, "");
    const dash = base.indexOf(" - ");
    if (dash > 0) {
      return { title: base.slice(dash + 3).trim(), artist: base.slice(0, dash).trim() };
    }
    return { title: base, artist: file.webkitRelativePath?.split("/")[0] || "Local" };
  }

  function loadFolder(files) {
    library = [...files]
      .filter((f) => AUDIO_RE.test(f.name))
      .map((file, i) => ({ id: i, file, ...nameFromFile(file), url: URL.createObjectURL(file) }));
    library.sort((a, b) => a.file.name.localeCompare(b.file.name, undefined, { numeric: true }));
    queueIndex = -1;
    renderLibrary();
    if (library.length) playIndex(0);
  }

  function renderLibrary() {
    els.empty.hidden = library.length > 0;
    els.list.innerHTML = "";
    for (const track of library) {
      const li = document.createElement("li");
      li.dataset.id = String(track.id);
      const name = document.createElement("span");
      name.className = "track-name";
      name.textContent = `${track.title} · ${track.artist}`;
      const dur = document.createElement("span");
      dur.className = "track-time";
      dur.textContent = "";
      li.append(name, dur);
      li.addEventListener("click", () => {
        queue = library.slice();
        queueIndex = library.findIndex((t) => t.id === track.id);
        playCurrent();
      });
      els.list.appendChild(li);
    }
  }

  function markCurrent(durationSec) {
    for (const li of els.list.children) {
      const isCur = Number(li.dataset.id) === queue[queueIndex]?.id;
      li.classList.toggle("current", isCur);
    }
    const cur = els.list.querySelector(".current .track-time");
    if (cur && durationSec !== undefined) cur.textContent = fmt(durationSec);
  }

  function playIndex(i) {
    if (!library.length) return;
    queue = shuffle ? shuffledFrom(i) : library.slice();
    queueIndex = 0;
    playCurrent();
  }

  function shuffledFrom(startIdx) {
    const rest = library.filter((_, i) => i !== startIdx);
    for (let k = rest.length - 1; k > 0; k--) {
      const j = Math.floor(Math.random() * (k + 1));
      [rest[k], rest[j]] = [rest[j], rest[k]];
    }
    return [library[startIdx], ...rest];
  }

  function playCurrent() {
    const track = queue[queueIndex];
    if (!track) return;
    audio.src = track.url;
    audio.play().catch(() => {});
    els.title.textContent = track.title;
    els.artist.textContent = track.artist;
    document.title = `${track.title} — Console`;
    markCurrent();
    updatePlayButton();
  }

  function togglePlay() {
    if (!queue.length) return playIndex(0);
    if (audio.paused) audio.play().catch(() => {}); else audio.pause();
  }

  function nextTrack(auto = false) {
    if (!queue.length) return;
    if (auto && repeatMode === "one") { audio.currentTime = 0; audio.play(); return; }
    if (queueIndex < queue.length - 1) {
      queueIndex++;
    } else if (repeatMode === "all" || !auto) {
      queueIndex = 0;
    } else {
      audio.pause();
      return;
    }
    playCurrent();
  }

  function prevTrack() {
    if (audio.currentTime > 3) { audio.currentTime = 0; return; }
    if (queue.length && queueIndex > 0) { queueIndex--; playCurrent(); }
  }

  function updatePlayButton() {
    els.play.textContent = audio.paused ? "▶" : "❚❚";
    els.play.closest(".display-glass")?.parentElement?.classList.toggle("playing", !audio.paused);
    document.querySelector(".deck-left").classList.toggle("playing", !audio.paused);
  }

  /* ---------- events ---------- */
  els.folder.addEventListener("change", (e) => loadFolder(e.target.files));
  els.play.addEventListener("click", togglePlay);
  els.next.addEventListener("click", () => nextTrack(false));
  els.prev.addEventListener("click", prevTrack);

  els.shuffle.addEventListener("click", () => {
    shuffle = !shuffle;
    els.shuffle.classList.toggle("on", shuffle);
  });
  els.repeat.addEventListener("click", () => {
    repeatMode = { off: "all", all: "one", one: "off" }[repeatMode];
    els.repeat.classList.toggle("on", repeatMode !== "off");
    els.repeat.textContent = repeatMode === "one" ? "⟳¹" : "⟳";
  });

  audio.addEventListener("play", updatePlayButton);
  audio.addEventListener("pause", updatePlayButton);
  audio.addEventListener("ended", () => nextTrack(true));
  audio.addEventListener("loadedmetadata", () => {
    els.tEnd.textContent = fmt(audio.duration);
    markCurrent(audio.duration);
  });
  audio.addEventListener("timeupdate", () => {
    if (!seeking) {
      els.seek.value = audio.duration ? (audio.currentTime / audio.duration) * 1000 : 0;
      els.tCur.textContent = fmt(audio.currentTime);
    }
  });

  els.seek.addEventListener("input", () => { seeking = true; });
  els.seek.addEventListener("change", () => {
    if (audio.duration) audio.currentTime = (els.seek.value / 1000) * audio.duration;
    seeking = false;
  });

  const savedVol = localStorage.getItem("console.volume");
  if (savedVol !== null) els.volume.value = savedVol;
  audio.volume = els.volume.value / 100;
  els.volume.addEventListener("input", () => {
    audio.volume = els.volume.value / 100;
    localStorage.setItem("console.volume", els.volume.value);
  });

  document.addEventListener("keydown", (e) => {
    if (e.target.tagName === "INPUT" && e.target.type === "file") return;
    switch (e.code) {
      case "Space": e.preventDefault(); togglePlay(); break;
      case "ArrowRight": nextTrack(false); break;
      case "ArrowLeft": prevTrack(); break;
      case "KeyS": els.shuffle.click(); break;
      case "KeyR": els.repeat.click(); break;
    }
  });
})();
