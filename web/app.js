// e-Challan landing page: the screenshot gallery. The theme toggle, card spotlight and scroll
// reveal come from sb-kit.js (the portfolio's design kit), which fires "sb:theme" on every change.
(function () {
  'use strict';

  var root = document.documentElement;
  function pageTheme() { return root.dataset.theme === 'light' ? 'light' : 'dark'; }

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
  var variant = pageTheme(); // which capture (light or dark) the gallery phone shows
  var galleryEl = document.getElementById('gallery');
  var tabsEl = document.getElementById('gallery-tabs');
  var imgEl = document.getElementById('gallery-shot');
  var heroEl = document.getElementById('hero-shot');
  var heroVisualEl = heroEl ? heroEl.closest('.hero-visual') : null;
  var captionEl = document.getElementById('gallery-caption');
  var statusEl = document.getElementById('shots-status');
  var variantBtns = document.querySelectorAll('[data-variant]');

  function entryFor(id) { return manifest.screens && manifest.screens[id]; }

  function shotUrl(id, theme) {
    var entry = entryFor(id);
    if (entry) {
      var file = entry[theme] || entry.light || entry.dark;
      if (file) return 'screenshots/' + file;
    }
    return 'assets/placeholders/' + id + '.svg';
  }

  function setImage(img, url, alt) {
    if (!img) return;
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
    var real = !!entryFor(screen.id);
    setImage(imgEl, shotUrl(screen.id, variant), screen.title + ' screen of the e-Challan app' + (real ? ' (' + variant + ' theme)' : ', illustrative preview'));
    setImage(heroEl, shotUrl('dashboard', pageTheme()), "Dashboard screen of the e-Challan app showing today's challans, fines and recent records");
    if (galleryEl) galleryEl.setAttribute('data-source', real ? 'ci' : 'placeholder');
    if (heroVisualEl) heroVisualEl.setAttribute('data-source', entryFor('dashboard') ? 'ci' : 'placeholder');
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
    Array.prototype.forEach.call(variantBtns, function (b) {
      b.setAttribute('aria-pressed', b.getAttribute('data-variant') === variant ? 'true' : 'false');
    });
  }

  function select(index, focus) {
    selected = (index + SCREENS.length) % SCREENS.length;
    renderShots();
    if (focus && tabsEl) tabsEl.children[selected].focus();
  }

  function preload(id) {
    if (!entryFor(id)) return;
    var img = new Image();
    img.src = shotUrl(id, variant);
  }

  if (tabsEl) {
    SCREENS.forEach(function (screen, i) {
      var tab = document.createElement('button');
      tab.type = 'button';
      tab.className = 'gallery-tab';
      tab.setAttribute('role', 'tab');
      tab.id = 'tab-' + screen.id;
      tab.setAttribute('aria-controls', 'gallery-shot');
      var num = document.createElement('span');
      num.className = 'gallery-tab__num';
      num.textContent = (i < 9 ? '0' : '') + (i + 1);
      num.setAttribute('aria-hidden', 'true');
      tab.appendChild(num);
      var label = document.createElement('span');
      label.className = 'gallery-tab__text';
      label.appendChild(document.createTextNode(screen.title));
      var small = document.createElement('small');
      small.textContent = screen.hint;
      label.appendChild(small);
      tab.appendChild(label);
      tab.addEventListener('click', function () { select(i, false); });
      tab.addEventListener('pointerenter', function () { preload(screen.id); });
      tab.addEventListener('focus', function () { preload(screen.id); });
      tab.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') { e.preventDefault(); select(selected + 1, true); }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); select(selected - 1, true); }
        if (e.key === 'Home') { e.preventDefault(); select(0, true); }
        if (e.key === 'End') { e.preventDefault(); select(SCREENS.length - 1, true); }
      });
      tabsEl.appendChild(tab);
    });
  }

  // Light / dark capture switch for the gallery phone; a page theme change also switches it.
  Array.prototype.forEach.call(variantBtns, function (b) {
    b.addEventListener('click', function () {
      variant = b.getAttribute('data-variant') === 'light' ? 'light' : 'dark';
      renderShots();
    });
  });
  document.addEventListener('sb:theme', function (e) {
    variant = e.detail === 'light' ? 'light' : 'dark';
    renderShots();
  });

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
          statusEl.textContent = parts.join(' ') + '. Each screen has a light and a dark capture; they follow this page’s theme, or pick one below.';
        }
      })
      .catch(function () { /* keep the previews */ });
  }

  var year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());
})();
