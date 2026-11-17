(function () {
  'use strict';
  var G = window.UncrossGuide;

  /* ---- Matching, one step at a time ---- */
  var demo = document.getElementById('matchDemo');
  if (demo) {
    var A = function (p, q, note, cls) { return { side: 'ASK', p: p, q: q, note: note || '', cls: cls || '' }; };
    var B = function (p, q, note, cls) { return { side: 'BID', p: p, q: q, note: note || '', cls: cls || '' }; };
    var steps = [
      { t: 'A new order arrives', c: 'Someone sends <b>BUY 500 at ₹102.50</b>. Before it touches the book, the engine looks at the sellers, cheapest first.',
        inc: 'BUY 500 @ 102.50', rows: [A('102.60', 300), A('102.50', 200, 'best ask'), null, B('102.40', 400, 'best bid'), B('102.30', 250)] },
      { t: 'Compare with the best ask', c: 'The cheapest seller asks <b>₹102.50</b>. The buyer will pay up to <b>₹102.50</b>. The prices cross, so a trade is possible.',
        inc: 'BUY 500 @ 102.50', rows: [A('102.60', 300), A('102.50', 200, 'prices cross', 'look'), null, B('102.40', 400), B('102.30', 250)] },
      { t: 'Trade', c: '<b>200 shares trade at ₹102.50</b>, the price of the order that was waiting. That seller is fully filled and leaves the book. The buyer still wants 300.',
        inc: 'BUY 300 left', rows: [A('102.60', 300), A('102.50', 200, 'TRADE 200 @ 102.50', 'hit'), null, B('102.40', 400), B('102.30', 250)] },
      { t: 'Check the next seller', c: 'The next seller wants <b>₹102.60</b>, more than the buyer’s limit of ₹102.50. Too dear, so matching stops here.',
        inc: 'BUY 300 left', rows: [A('102.60', 300, 'too dear — stop', 'look'), null, B('102.40', 400), B('102.30', 250)] },
      { t: 'The rest waits in the book', c: 'The 300 left over <b>rest</b> in the book as a new bid at ₹102.50, and it becomes the new best bid. This is a <b>partial fill</b>: completely normal.',
        inc: '', rows: [A('102.60', 300, 'best ask'), null, B('102.50', 300, 'new best bid', 'rest'), B('102.40', 400), B('102.30', 250)] }
    ];
    var i = 0, timer = null;
    var bookEl = demo.querySelector('.book-live'), cap = demo.querySelector('.cap'), title = demo.querySelector('.st-title');
    var dots = demo.querySelector('.dots'), prev = demo.querySelector('[data-s="prev"]'), next = demo.querySelector('[data-s="next"]'), play = demo.querySelector('[data-s="play"]');
    steps.forEach(function (_, n) {
      var b = document.createElement('button');
      b.type = 'button'; b.setAttribute('aria-label', 'Step ' + (n + 1));
      b.addEventListener('click', function () { stop(); show(n); });
      dots.appendChild(b);
    });
    function show(n) {
      i = n; var s = steps[n];
      var html = '<div class="hd">ORDER BOOK · RELIANCE</div>';
      html += '<div class="r inc' + (s.inc ? '' : ' gone') + '"><span>NEW</span><span class="p">' + (s.inc || '—') + '</span></div>';
      s.rows.forEach(function (r) {
        if (!r) { html += '<div class="spread">— the spread —</div>'; return; }
        html += '<div class="r ' + r.side.toLowerCase() + ' ' + r.cls + '"><span>' + r.side + '</span><span class="p">' + r.p + '</span><span>' + r.q + '</span><span class="n">' + r.note + '</span></div>';
      });
      bookEl.innerHTML = html;
      title.textContent = 'Step ' + (n + 1) + ' of ' + steps.length + ' · ' + s.t;
      cap.innerHTML = s.c;
      dots.querySelectorAll('button').forEach(function (d, k) { d.setAttribute('aria-current', String(k === n)); });
      prev.disabled = n === 0; next.disabled = n === steps.length - 1;
    }
    function stop() { clearInterval(timer); timer = null; play.textContent = 'Play'; }
    prev.addEventListener('click', function () { stop(); show(Math.max(0, i - 1)); });
    next.addEventListener('click', function () { stop(); show(Math.min(steps.length - 1, i + 1)); });
    play.addEventListener('click', function () {
      if (timer) { stop(); return; }
      if (i === steps.length - 1) show(0);
      play.textContent = 'Pause';
      timer = setInterval(function () { if (i >= steps.length - 1) { stop(); return; } show(i + 1); }, 2600);
    });
    show(0);
  }

  /* ---- Circuit breaker, played through ---- */
  var flow = document.getElementById('breakerFlow');
  if (flow) {
    var chips = flow.querySelectorAll('span');
    var notes = ['Trading normally. The breaker watches how fast the price moves.', 'The price moved 10% within 60 seconds: halted for 30 seconds. Orders wait; nothing trades.',
      'Call auction for 30 seconds: orders collect, and the engine finds the one price that trades the most shares.', 'Reopened at the auction price. Continuous trading resumes.'];
    var cap2 = document.getElementById('breakerCap'), btn = document.getElementById('breakerPlay'), k = -1, t2 = null;
    function on(n) { chips.forEach(function (c, j) { c.classList.toggle('on', j === n); }); cap2.textContent = notes[n]; }
    btn.addEventListener('click', function () {
      clearInterval(t2); k = 0; on(0); btn.textContent = 'Replay';
      t2 = setInterval(function () { k++; if (k >= chips.length) { clearInterval(t2); return; } on(k); }, G.reduce ? 1 : 2200);
    });
  }

  /* ---- Rehearsal timer ---- */
  var plan = document.getElementById('planTable');
  if (plan) {
    var rows = Array.prototype.slice.call(plan.querySelectorAll('tbody tr'));
    var starts = rows.map(function (r) { var p = r.querySelector('.time').textContent.split(':'); return (+p[0]) * 60 + (+p[1]); });
    var total = 12 * 60;
    var clock = document.getElementById('rehClock'), go = document.getElementById('rehGo'), reset = document.getElementById('rehReset'), hint = document.getElementById('rehHint');
    var startedAt = 0, elapsed = 0, tick = null;
    function fmt(s) { s = Math.floor(s); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
    function paint() {
      clock.textContent = fmt(elapsed);
      clock.classList.toggle('over', elapsed > total);
      var idx = -1; starts.forEach(function (s, n) { if (elapsed >= s) idx = n; });
      rows.forEach(function (r, n) { r.classList.toggle('live', n === idx && (tick || elapsed > 0)); r.classList.toggle('done', n < idx); });
      hint.textContent = elapsed > total ? 'Over time by ' + fmt(elapsed - total) + ' — move to questions.' : (idx >= 0 ? 'Now: ' + rows[idx].children[1].textContent : 'Press Start and talk through the plan as if presenting.');
    }
    go.addEventListener('click', function () {
      if (tick) { clearInterval(tick); tick = null; go.textContent = 'Resume'; paint(); return; }
      startedAt = Date.now() - elapsed * 1000; go.textContent = 'Pause';
      tick = setInterval(function () { elapsed = (Date.now() - startedAt) / 1000; paint(); }, 250);
    });
    reset.addEventListener('click', function () { clearInterval(tick); tick = null; elapsed = 0; go.textContent = 'Start'; paint(); });
    paint();
  }

  /* ---- Demo checklist (remembered in this browser) ---- */
  var list = document.getElementById('prepList');
  if (list) {
    var boxes = list.querySelectorAll('input[type="checkbox"]');
    var key = 'uncross-guide-prep';
    var state = {}; try { state = JSON.parse(G.store(key) || '{}'); } catch (e) { state = {}; }
    var count = document.getElementById('prepCount'), meter = document.getElementById('prepMeter');
    function paint() {
      var n = 0; boxes.forEach(function (b) { if (b.checked) n++; });
      count.textContent = n === boxes.length ? 'All set. Good luck.' : n + ' of ' + boxes.length + ' ready';
      meter.style.width = (100 * n / boxes.length) + '%';
    }
    boxes.forEach(function (b, n) {
      b.checked = !!state[n];
      b.addEventListener('change', function () { state[n] = b.checked; G.store(key, JSON.stringify(state)); paint(); });
    });
    document.getElementById('prepReset').addEventListener('click', function () { boxes.forEach(function (b) { b.checked = false; }); state = {}; G.store(key, '{}'); paint(); });
    paint();
  }
})();
