/* Dev loader for cards and UI kits: fetches component sources, compiles each file as an isolated module with Babel, exposes window.DalalX. */
(function () {
  var src = document.currentScript.src;
  var root = src.slice(0, src.lastIndexOf('/') + 1);
  window.DALALX_ASSET_BASE = root + 'assets/portraits/';
  var FILES = ["components/masthead/Edition.jsx","components/masthead/Masthead.jsx","components/masthead/HeadlineBanner.jsx","components/layout/SectionLabel.jsx","components/layout/RuledPanel.jsx","components/layout/StockTabs.jsx","components/layout/TheWire.jsx","components/layout/Folio.jsx","components/controls/Button.jsx","components/controls/OrderTypeToggle.jsx","components/controls/SideToggle.jsx","components/controls/PriceInput.jsx","components/controls/XRayToggle.jsx","components/marks/RubberStamp.jsx","components/marks/RedPencilCircle.jsx","components/marks/MarginNote.jsx","components/bots/Portrait.jsx","components/bots/Monogram.jsx","components/bots/BotByline.jsx","components/market/PriceCell.jsx","components/market/OrderBookRow.jsx","components/market/TradeTapeRow.jsx","components/market/PenChart.jsx","components/market/Countdown.jsx","components/market/CandleChart.jsx","components/market/BreadthBar.jsx","components/notices/AlertNotice.jsx","components/notices/Toast.jsx","components/notices/Modal.jsx"];
  var ns = (window.DalalX = window.DalalX || {});
  ns.root = root;
  ns.ready = Promise.all(FILES.map(function (f) { return fetch(root + f).then(function (r) { return r.text(); }); })).then(function (srcs) {
    srcs.forEach(function (s, i) {
      var names = [];
      var body = s
        .replace(/^import React.*$/gm, '')
        .replace(/^import \{([^}]+)\} from .*$/gm, function (m, list) { return 'var {' + list + '} = __ns;'; })
        .replace(/^export (function|const) (\w+)/gm, function (m, kw, n) { names.push(n); return kw + ' ' + n; });
      body += '\nreturn {' + names.join(',') + '};';
      try {
        var out = Babel.transform(body, { presets: ['react'], parserOpts: { allowReturnOutsideFunction: true } }).code;
        Object.assign(ns, new Function('React', '__ns', out)(React, ns));
      } catch (e) { console.error('DalalX loader: ' + FILES[i], e); }
    });
    return ns;
  });
})();
