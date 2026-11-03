// e-Challan landing page: theme toggle and the screenshot gallery.
(function () {
  'use strict';

  var root = document.documentElement;
  var darkQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function storedTheme() {
    try { return localStorage.getItem('theme'); } catch (e) { return null; }
  }

  function currentTheme() {
    var explicit = root.getAttribute('data-theme');
    if (explicit === 'light' || explicit === 'dark') return explicit;
    return darkQuery && darkQuery.matches ? 'dark' : 'light';
  }

  // ---- Theme toggle -------------------------------------------------------------------
  var toggle = document.getElementById('theme-toggle');
  function syncToggleLabel() {
    if (!toggle) return;
    toggle.setAttribute('aria-label', currentTheme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme');
  }
  if (toggle) {
    toggle.addEventListener('click', function () {
      var next = currentTheme() === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem('theme', next); } catch (e) { /* not persisted */ }
      syncToggleLabel();
      renderShots();
    });
  }
  if (darkQuery && darkQuery.addEventListener) {
    darkQuery.addEventListener('change', function () {
      if (!storedTheme()) { syncToggleLabel(); renderShots(); }
    });
  }
  syncToggleLabel();

  // ---- Screenshot gallery ------------------------------------------------------------
  var SCREENS = [
    { id: 'login', title: 'Login', hint: 'Sign in or try the demo',
      text: 'Firebase email sign-in gated by the officer/admin role claim. Builds without a Firebase project offer a one-tap demo that seeds sample data locally.' },
    { id: 'dashboard', title: 'Dashboard', hint: "Today's numbers",
      text: "Challans issued today, fines issued and collected, pending dues, top offences, unsynced records and the most recent challans." },
    { id: 'issue', title: 'Issue violation', hint: 'Plate, offences, fine',
      text: 'Plate validation as you type, offence chips with MV Act fines, GPS location and a live, debounced fine preview that applies the repeat-offender multiplier.' },
    { id: 'history', title: 'History', hint: 'Search and filter',
      text: 'Search by plate or challan number, filter by status and date range, with a running total of the filtered challans.' },
    { id: 'detail', title: 'Challan detail', hint: 'PDF, payment, QR',
      text: 'Fine breakdown, offender and location details, a privacy-safe QR code, PDF sharing and payment recording (cash, UPI, card or online).' },
    { id: 'admin', title: 'Admin analytics', hint: 'Charts and officers',
      text: 'Issued-versus-collected bars, a status donut and top offences drawn on a Compose Canvas, plus the officer directory with activation controls.' }
  ];

  var manifest = { screens: {} };
  var selected = 0;
  var tabsEl = document.getElementById('gallery-tabs');
  var imgEl = document.getElementById('gallery-shot');
  var heroEl = document.getElementById('hero-shot');
  var captionEl = document.getElementById('gallery-caption');
  var statusEl = document.getElementById('shots-status');

  function shotUrl(id) {
    var entry = manifest.screens && manifest.screens[id];
    if (entry) {
      var file = entry[currentTheme()] || entry.light || entry.dark;
      if (file) return 'screenshots/' + file;
    }
    return 'assets/placeholders/' + id + '.svg';
  }

  function setImage(img, id, alt) {
    if (!img) return;
    var url = shotUrl(id);
    if (img.getAttribute('src') !== url) {
      img.classList.remove('shot-swap');
      void img.offsetWidth; // restart the fade animation
      img.classList.add('shot-swap');
      img.setAttribute('src', url);
    }
    img.setAttribute('alt', alt);
  }

  function renderShots() {
    var screen = SCREENS[selected];
    setImage(imgEl, screen.id, screen.title + ' screen of the e-Challan app');
    setImage(heroEl, 'dashboard', "Dashboard screen of the e-Challan app showing today's challans, fines and recent records");
    if (captionEl) {
      captionEl.innerHTML = '';
      var h = document.createElement('h3');
      h.textContent = screen.title;
      var p = document.createElement('p');
      p.textContent = screen.text;
      captionEl.appendChild(h);
      captionEl.appendChild(p);
    }
    if (tabsEl) {
      Array.prototype.forEach.call(tabsEl.children, function (tab, i) {
        tab.setAttribute('aria-selected', i === selected ? 'true' : 'false');
        tab.tabIndex = i === selected ? 0 : -1;
      });
    }
  }

  function select(index, focus) {
    selected = (index + SCREENS.length) % SCREENS.length;
    renderShots();
    if (focus && tabsEl) tabsEl.children[selected].focus();
  }

  if (tabsEl) {
    SCREENS.forEach(function (screen, i) {
      var tab = document.createElement('button');
      tab.type = 'button';
      tab.className = 'gallery-tab';
      tab.setAttribute('role', 'tab');
      tab.id = 'tab-' + screen.id;
      tab.setAttribute('aria-controls', 'gallery-shot');
      tab.innerHTML = '';
      tab.appendChild(document.createTextNode(screen.title));
      var small = document.createElement('small');
      small.textContent = screen.hint;
      tab.appendChild(small);
      tab.addEventListener('click', function () { select(i, false); });
      tab.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') { e.preventDefault(); select(selected + 1, true); }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); select(selected - 1, true); }
      });
      tabsEl.appendChild(tab);
    });
  }

  // Fall back to the placeholder if a published screenshot ever fails to load.
  [imgEl, heroEl].forEach(function (img) {
    if (!img) return;
    img.addEventListener('error', function () {
      var id = img === heroEl ? 'dashboard' : SCREENS[selected].id;
      var fallback = 'assets/placeholders/' + id + '.svg';
      if (img.getAttribute('src') !== fallback) img.setAttribute('src', fallback);
    });
  });

  renderShots();

  // CI writes screenshots/manifest.json next to the PNGs it captured on the emulator.
  if (window.fetch) {
    fetch('screenshots/manifest.json', { cache: 'no-cache' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (!data || !data.screens || !Object.keys(data.screens).length) return;
        manifest = data;
        renderShots();
        if (statusEl) {
          var when = data.generated ? new Date(data.generated) : null;
          var parts = ['Captured on an Android 14 emulator by GitHub Actions'];
          if (when && !isNaN(when)) parts.push('on ' + when.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }));
          if (data.commit) parts.push('from commit ' + String(data.commit).slice(0, 7));
          statusEl.textContent = parts.join(' ') + '. They follow this page’s light or dark theme.';
        }
      })
      .catch(function () { /* keep the previews */ });
  }

  var year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());
})();
