(function () {
  'use strict';
  var root = document.documentElement;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  function store(key, val) {
    try { if (val === undefined) return localStorage.getItem(key); localStorage.setItem(key, val); } catch (e) { return null; }
  }

  /* ---- Day / Night ---- */
  function isDark() {
    var t = root.getAttribute('data-theme');
    return t ? t === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  function paintSeg() {
    document.querySelectorAll('[data-theme-set]').forEach(function (b) {
      b.setAttribute('aria-pressed', String((b.dataset.themeSet === 'dark') === isDark()));
    });
  }
  var saved = store('uncross-guide-theme');
  if (saved === 'dark' || saved === 'light') root.setAttribute('data-theme', saved);
  document.querySelectorAll('[data-theme-set]').forEach(function (b) {
    b.addEventListener('click', function () {
      root.setAttribute('data-theme', b.dataset.themeSet);
      store('uncross-guide-theme', b.dataset.themeSet);
      paintSeg();
    });
  });
  paintSeg();

  /* ---- Part numbers, pager, contents ---- */
  var parts = Array.prototype.slice.call(document.querySelectorAll('section.part'));
  var links = Array.prototype.slice.call(document.querySelectorAll('nav.toc a[href^="#"]'));
  var nowEl = document.getElementById('now');
  parts.forEach(function (sec, i) {
    var prev = parts[i - 1], next = parts[i + 1];
    var pager = document.createElement('nav');
    pager.className = 'pager';
    pager.setAttribute('aria-label', 'Part navigation');
    function link(target, cls, label) {
      var a = document.createElement('a');
      a.href = '#' + target.id; a.className = cls;
      a.innerHTML = '<small>' + label + '</small>' + target.querySelector('h2').textContent;
      return a;
    }
    if (prev) pager.appendChild(link(prev, 'prev', '← Previous'));
    if (next) pager.appendChild(link(next, 'next', 'Next →'));
    sec.appendChild(pager);
  });

  var current = -1;
  function setCurrent(i) {
    if (i === current || i < 0) return;
    current = i;
    links.forEach(function (a) { a.setAttribute('aria-current', String(a.getAttribute('href') === '#' + parts[i].id)); });
    if (nowEl) nowEl.innerHTML = '<b>Part ' + (i + 1) + ' of ' + parts.length + '</b> · ' + parts[i].querySelector('h2').textContent;
  }
  var bar = document.querySelector('.progress');
  var toTop = document.querySelector('.to-top');
  var ticking = false;
  function onScroll() {
    ticking = false;
    var h = document.documentElement.scrollHeight - window.innerHeight;
    if (bar) bar.style.transform = 'scaleX(' + (h > 0 ? Math.min(1, window.scrollY / h) : 0) + ')';
    if (toTop) toTop.classList.toggle('show', window.scrollY > 900);
    var line = window.innerHeight * 0.3, idx = 0;
    for (var i = 0; i < parts.length; i++) { if (parts[i].getBoundingClientRect().top <= line) idx = i; }
    setCurrent(idx);
  }
  window.addEventListener('scroll', function () { if (!ticking) { ticking = true; requestAnimationFrame(onScroll); } }, { passive: true });
  onScroll();
  if (toTop) toTop.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' }); });

  /* ---- Mobile contents drawer ---- */
  var toc = document.querySelector('nav.toc'), scrim = document.querySelector('.scrim'), menu = document.querySelector('.menu-btn');
  function drawer(open) {
    if (!toc) return;
    toc.classList.toggle('open', open); scrim.classList.toggle('show', open);
    if (menu) menu.setAttribute('aria-expanded', String(open));
  }
  if (menu) menu.addEventListener('click', function () { drawer(!toc.classList.contains('open')); });
  if (scrim) scrim.addEventListener('click', function () { drawer(false); });
  links.forEach(function (a) { a.addEventListener('click', function () { drawer(false); }); });

  /* ---- Keyboard: j / k between parts ---- */
  document.addEventListener('keydown', function (e) {
    if (e.target.closest('input, textarea, select') || e.metaKey || e.ctrlKey || e.altKey) return;
    if (e.key === 'Escape') { drawer(false); closeLightbox(); return; }
    var step = e.key === 'j' ? 1 : e.key === 'k' ? -1 : 0;
    if (!step) return;
    var t = parts[Math.max(0, Math.min(parts.length - 1, current + step))];
    if (t) t.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth' });
  });

  /* ---- Reveal on scroll ---- */
  if (!reduce && 'IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
    }, { rootMargin: '0px 0px -8% 0px' });
    document.querySelectorAll('.card, .say, .box, figure, .stats, .tablewrap, .code, .stepper').forEach(function (el) {
      el.classList.add('reveal'); io.observe(el);
    });
  }

  /* ---- Copy buttons on code ---- */
  document.querySelectorAll('pre').forEach(function (pre) {
    var wrap = document.createElement('div');
    wrap.className = 'code';
    pre.parentNode.insertBefore(wrap, pre); wrap.appendChild(pre);
    var b = document.createElement('button');
    b.className = 'btn copy'; b.type = 'button'; b.textContent = 'Copy';
    b.addEventListener('click', function () {
      var text = pre.innerText;
      var done = function () { b.textContent = 'Copied'; setTimeout(function () { b.textContent = 'Copy'; }, 1400); };
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () {}); else done();
    });
    wrap.appendChild(b);
  });

  /* ---- Lightbox for diagrams ---- */
  var lb = document.createElement('div');
  lb.className = 'lightbox'; lb.setAttribute('role', 'dialog'); lb.setAttribute('aria-modal', 'true');
  lb.innerHTML = '<button class="btn x" type="button">Close</button><img alt="">';
  document.body.appendChild(lb);
  function closeLightbox() { lb.classList.remove('open'); }
  lb.addEventListener('click', closeLightbox);
  document.querySelectorAll('figure .frame img').forEach(function (img) {
    var frame = img.parentNode;
    frame.setAttribute('tabindex', '0'); frame.setAttribute('role', 'button'); frame.setAttribute('aria-label', 'Enlarge diagram');
    function open() { lb.querySelector('img').src = img.src; lb.querySelector('img').alt = img.alt; lb.classList.add('open'); lb.querySelector('.x').focus(); }
    frame.addEventListener('click', open);
    frame.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
  });

  /* ---- Table filters ---- */
  document.querySelectorAll('input[data-filter]').forEach(function (input) {
    var table = document.querySelector(input.dataset.filter);
    var rows = table.querySelectorAll('tbody tr');
    var count = input.closest('.tools').querySelector('.count');
    var empty = table.parentNode.parentNode.querySelector('.empty');
    function run() {
      var q = input.value.trim().toLowerCase(), shown = 0;
      rows.forEach(function (r) {
        var hit = !q || r.textContent.toLowerCase().indexOf(q) !== -1;
        r.classList.toggle('hidden-row', !hit); if (hit) shown++;
      });
      if (count) count.textContent = shown + ' of ' + rows.length;
      if (empty) empty.classList.toggle('show', shown === 0);
    }
    input.addEventListener('input', run); run();
  });

  /* ---- Q&A: numbering, open/close all, progress ---- */
  document.querySelectorAll('.qa').forEach(function (qa) {
    var items = qa.querySelectorAll('details');
    var seen = new Set();
    var count = qa.querySelector('.count'), meter = qa.querySelector('.meter i');
    var chev = '<svg class="chev" viewBox="0 0 24 24" fill="none" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>';
    items.forEach(function (d, i) {
      var s = d.querySelector('summary');
      s.innerHTML = '<span class="qn">' + String(i + 1).padStart(2, '0') + '</span><span>' + s.innerHTML + '</span>' + chev;
      d.addEventListener('toggle', function () { if (d.open) { seen.add(i); paint(); } });
    });
    function paint() {
      if (count) count.textContent = seen.size + ' of ' + items.length + ' answers opened';
      if (meter) meter.style.width = (100 * seen.size / items.length) + '%';
    }
    qa.querySelectorAll('[data-qa]').forEach(function (b) {
      b.addEventListener('click', function () {
        var mode = b.dataset.qa;
        if (mode === 'reset') { seen.clear(); items.forEach(function (d) { d.open = false; }); paint(); return; }
        items.forEach(function (d) { d.open = mode === 'open'; });
      });
    });
    paint();
  });

  window.addEventListener('beforeprint', function () { document.querySelectorAll('details').forEach(function (d) { d.open = true; }); });
  window.UncrossGuide = { store: store, reduce: reduce };
})();
