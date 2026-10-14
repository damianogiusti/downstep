(function () {
  var root = document.documentElement;

  // Theme toggle. The choice lasts 5 minutes from the last page view (see <head>).
  var themeButton = document.getElementById("theme");
  if (themeButton) themeButton.addEventListener("click", function () {
    var current = root.dataset.theme || (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    var next = current === "light" ? "dark" : "light";
    root.dataset.theme = next;
    localStorage.setItem("sd-theme", JSON.stringify({ value: next, at: Date.now() }));
  });

  // The language toggle is a plain link; the saved choice drives the redirect in <head>.
  document.querySelectorAll("a.toggle[data-lang]").forEach(function (link) {
    link.addEventListener("click", function () { localStorage.setItem("sd-lang", link.dataset.lang); });
  });

  loopDemo();

  /**
   * The hero animation: listen, set A and B, loop, slow down to 70%, change key, speed up again.
   * Positions are fractions of the track; the playhead moves at the current speed.
   */
  function loopDemo() {
    var svg = document.getElementById("wave");
    if (!svg) return null;
    var NS = "http://www.w3.org/2000/svg";
    var W = 1000, TOP = 44, BOTTOM = 166, BARS = 60, DURATION = 192;
    var A = 0.36, B = 0.52;
    var timeEl = document.getElementById("demo-time");
    var speedEl = document.getElementById("demo-speed");
    var pitchEl = document.getElementById("demo-pitch");
    var aEl = document.getElementById("demo-a");
    var bEl = document.getElementById("demo-b");
    document.getElementById("demo-end").textContent = clock(1);
    var captionEl = document.getElementById("demo-caption");


    // Waveform bars: a fixed, song-like shape (louder chorus in the middle), same on every load.
    var seed = 7;
    function random() { seed = (seed * 16807) % 2147483647; return seed / 2147483647; }
    var step = W / BARS, barWidth = step * 0.56, mid = (TOP + BOTTOM) / 2, maxHalf = (BOTTOM - TOP) / 2;
    var region = el("rect", { "class": "region", x: 0, y: TOP - 8, width: 0, height: BOTTOM - TOP + 12, rx: 12, opacity: 0 });
    svg.appendChild(region);
    var bars = [];
    for (var i = 0; i < BARS; i++) {
      var t = i / (BARS - 1);
      var envelope = 0.35 + 0.45 * Math.sin(Math.PI * t) + 0.2 * Math.sin(Math.PI * 4 * t) * Math.sin(Math.PI * t);
      var half = Math.max(8, maxHalf * Math.min(1, envelope * (0.55 + 0.45 * random())));
      var x = i * step + (step - barWidth) / 2;
      var bar = el("rect", { "class": "bar", x: x.toFixed(1), y: (mid - half).toFixed(1), width: barWidth.toFixed(1), height: (half * 2).toFixed(1), rx: (barWidth / 2).toFixed(1) });
      bar.centre = (i + 0.5) / BARS;
      svg.appendChild(bar);
      bars.push(bar);
    }
    var markerA = marker("A", "a");
    var markerB = marker("B", "b");
    var head = el("rect", { "class": "head", x: 0, y: TOP - 12, width: 3, height: BOTTOM - TOP + 20, rx: 1.5 });
    svg.appendChild(head);

    function el(name, attrs) {
      var node = document.createElementNS(NS, name);
      for (var key in attrs) node.setAttribute(key, attrs[key]);
      return node;
    }
    function marker(letter, kind) {
      var g = el("g", { "class": "marker", opacity: 0 });
      g.appendChild(el("rect", { "class": "mark-" + kind, x: -2, y: 30, width: 4, height: BOTTOM - 28, rx: 2 }));
      g.appendChild(el("rect", { "class": "flag-" + kind, x: -17, y: 4, width: 34, height: 26, rx: 9 }));
      var text = el("text", { "class": "flag-text " + kind, x: 0, y: 17.5 });
      text.textContent = letter;
      g.appendChild(text);
      svg.appendChild(g);
      return g;
    }
    function place(g, at, visible) {
      g.setAttribute("transform", "translate(" + (at * W).toFixed(1) + " " + (visible ? 0 : -10) + ")");
      g.setAttribute("opacity", visible ? 1 : 0);
    }
    function clock(fraction) {
      var s = Math.round(fraction * DURATION);
      return Math.floor(s / 60) + ":" + ("0" + (s % 60)).slice(-2);
    }

    var state = { pos: 0, speed: 100, pitch: 0, hasA: false, hasB: false, loopOn: false, caption: "listen" };

    function draw() {
      head.setAttribute("x", (state.pos * W - 1.5).toFixed(1));
      place(markerA, A, state.hasA);
      place(markerB, B, state.hasB);
      region.setAttribute("x", (A * W).toFixed(1));
      region.setAttribute("width", ((B - A) * W).toFixed(1));
      region.setAttribute("opacity", state.loopOn ? 1 : 0);
      for (var i = 0; i < bars.length; i++) {
        var bar = bars[i];
        var inLoop = state.loopOn && bar.centre > A && bar.centre < B;
        bar.classList.toggle("in", inLoop);
        bar.style.opacity = bar.centre <= state.pos || inLoop ? 1 : 0.5;
      }
      timeEl.textContent = clock(state.pos);
      speedEl.textContent = Math.round(state.speed) + "%";
      pitchEl.textContent = (state.pitch < 0 ? "−" + Math.abs(state.pitch) : state.pitch) + " st";
      // Like the player: the loop times appear next to the position once A and B are set.
      aEl.textContent = state.hasA ? "A " + clock(A) : "";
      bEl.textContent = state.hasB ? "B " + clock(B) : "";
      captionEl.textContent = captionEl.dataset[state.caption] || "";
    }

    // Static picture for people who asked for less motion.
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) {
      state = { pos: A + (B - A) * 0.4, speed: 70, pitch: 0, hasA: true, hasB: true, loopOn: true, caption: "slow" };
      draw();
      return;
    }

    // Track fraction per second of animation at 100%: a whole loop lasts about 1.6 s.
    var RATE = (B - A) / 1.6;
    var loops = 0, last = null, phase = "listen", phaseTime = 0;

    function frame(now) {
      var dt = last === null ? 0 : Math.min(0.05, (now - last) / 1000);
      last = now;
      phaseTime += dt;
      state.pos += RATE * (state.speed / 100) * dt;

      if (phase === "listen") {
        state.caption = "listen";
        if (state.pos >= A - 0.12) { phase = "mark"; phaseTime = 0; }
      } else if (phase === "mark") {
        state.caption = "mark";
        if (state.pos >= A) state.hasA = true;
        if (state.pos >= B) { state.hasB = true; state.loopOn = true; phase = "slow"; phaseTime = 0; state.pos = A; loops = 0; }
      } else if (state.pos >= B) {
        state.pos = A;
        loops++;
      }

      if (phase === "slow") {
        state.caption = "slow";
        state.speed = Math.max(70, 100 - phaseTime * 9);
        if (loops >= 3) { phase = "key"; phaseTime = 0; loops = 0; }
      } else if (phase === "key") {
        state.caption = "key";
        state.pitch = phaseTime > 0.6 ? -2 : phaseTime > 0.3 ? -1 : 0;
        if (loops >= 2) { phase = "faster"; phaseTime = 0; loops = 0; }
      } else if (phase === "faster") {
        state.caption = "faster";
        // Up by 5% steps, like the +5% button.
        state.speed = Math.min(100, 70 + 5 * Math.floor(phaseTime / 0.9));
        if (state.speed >= 100 && loops >= 2) { phase = "reset"; phaseTime = 0; }
      } else if (phase === "reset") {
        state.loopOn = false;
        if (state.pos > 0.98 || phaseTime > 1.8) {
          state = { pos: 0, speed: 100, pitch: 0, hasA: false, hasB: false, loopOn: false, caption: "listen" };
          phase = "listen"; phaseTime = 0;
        }
      }
      draw();
    }

    // Animate only while visible.
    var running = false;
    function tick(now) {
      if (!running) { last = null; return; }
      frame(now);
      requestAnimationFrame(tick);
    }
    new IntersectionObserver(function (entries) {
      var visible = entries[0].isIntersecting;
      if (visible && !running) { running = true; requestAnimationFrame(tick); }
      running = visible;
    }).observe(svg);

    draw();
  }
})();
