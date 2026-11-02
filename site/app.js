/* T9 APK Tools — static site behavior. No dependencies, no tracking. */
(function () {
  'use strict';

  var root = document.documentElement;
  root.classList.add('js');

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  var copyTimers = new WeakMap();
  var copyState = new WeakMap();

  function text(value) {
    if (typeof value === 'string') return value.trim();
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
    return '';
  }

  function setMeta(name, value) {
    if (!value) return false;
    var nodes = document.querySelectorAll('[data-meta="' + name + '"]');
    if (!nodes.length) return false;
    Array.prototype.forEach.call(nodes, function (node) {
      node.textContent = value;
    });
    return true;
  }

  function isSha256(value) {
    return /^[0-9a-f]{64}$/i.test(text(value));
  }

  function formatBytes(value) {
    var bytes = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(bytes) || bytes <= 0) return '';
    var units = ['B', 'KB', 'MB', 'GB'];
    var index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    var amount = bytes / Math.pow(1024, index);
    return amount.toFixed(index === 0 ? 0 : 1) + ' ' + units[index];
  }

  function formatBuiltAt(value) {
    var raw = text(value);
    if (!raw) return null;
    var date = new Date(raw);
    if (Number.isNaN(date.getTime())) return null;
    try {
      var formatted = new Intl.DateTimeFormat('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC'
      }).format(date);
      return { label: formatted + ' UTC', iso: date.toISOString() };
    } catch (error) {
      return { label: raw, iso: raw };
    }
  }

  function safeGithubUrl(value) {
    var raw = text(value);
    if (!raw) return '';
    try {
      var url = new URL(raw, window.location.href);
      if (url.protocol !== 'https:') return '';
      if (url.hostname !== 'github.com' && url.hostname !== 'www.github.com') return '';
      return url.href;
    } catch (error) {
      return '';
    }
  }

  /* Mobile navigation */
  var navToggle = document.querySelector('[data-nav-toggle]');
  var nav = document.getElementById('site-nav');
  if (navToggle && nav) {
    var desktopQuery = window.matchMedia('(min-width: 981px)');
    var setNavOpen = function (open) {
      navToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      navToggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
      nav.classList.toggle('is-open', open);
    };
    setNavOpen(false);
    navToggle.addEventListener('click', function () {
      setNavOpen(navToggle.getAttribute('aria-expanded') !== 'true');
    });
    nav.addEventListener('click', function (event) {
      if (event.target.closest('a')) setNavOpen(false);
    });
    document.addEventListener('click', function (event) {
      if (navToggle.getAttribute('aria-expanded') !== 'true') return;
      if (nav.contains(event.target) || navToggle.contains(event.target)) return;
      setNavOpen(false);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape') return;
      if (navToggle.getAttribute('aria-expanded') !== 'true') return;
      setNavOpen(false);
      navToggle.focus();
    });
    var closeOnDesktop = function (event) { if (event.matches) setNavOpen(false); };
    if (typeof desktopQuery.addEventListener === 'function') desktopQuery.addEventListener('change', closeOnDesktop);
    else if (typeof desktopQuery.addListener === 'function') desktopQuery.addListener(closeOnDesktop);
  }

  /* Copy buttons */
  var liveRegion = document.getElementById('copy-status');
  function announce(message) {
    if (!liveRegion) return;
    liveRegion.textContent = '';
    window.setTimeout(function () { liveRegion.textContent = message; }, 30);
  }
  function fallbackCopy(value) {
    var helper = document.createElement('textarea');
    helper.value = value;
    helper.setAttribute('readonly', '');
    helper.style.position = 'fixed';
    helper.style.top = '-1000px';
    helper.style.left = '-1000px';
    document.body.appendChild(helper);
    helper.select();
    helper.setSelectionRange(0, helper.value.length);
    var copied = false;
    try { copied = document.execCommand('copy'); } catch (error) { copied = false; }
    document.body.removeChild(helper);
    if (!copied) throw new Error('Copy command failed');
  }
  async function writeToClipboard(value) {
    if (navigator.clipboard && window.isSecureContext) {
      try { await navigator.clipboard.writeText(value); return; } catch (error) { /* fall through */ }
    }
    fallbackCopy(value);
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-copy-target]'), function (button) {
    var label = button.querySelector('[data-copy-label]');
    copyState.set(button, { label: label ? label.textContent : '', aria: button.getAttribute('aria-label') || '' });
  });
  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-copy-target]');
    if (!button || button.disabled || button.hidden) return;
    var target = document.getElementById(button.getAttribute('data-copy-target'));
    if (!target) return;
    var value = (target.textContent || '').trim();
    if (!value || value === '—') { announce('Nothing to copy yet.'); return; }
    var stored = copyState.get(button);
    var name = button.getAttribute('data-copy-name') || 'Text';
    var label = button.querySelector('[data-copy-label]');
    writeToClipboard(value).then(function () {
      button.setAttribute('data-copied', 'true');
      if (label) label.textContent = 'Copied';
      button.setAttribute('aria-label', 'Copied to clipboard');
      announce(name + ' copied to clipboard.');
      window.clearTimeout(copyTimers.get(button));
      copyTimers.set(button, window.setTimeout(function () {
        button.removeAttribute('data-copied');
        if (label && stored) label.textContent = stored.label;
        if (stored && stored.aria) button.setAttribute('aria-label', stored.aria);
      }, 2000));
    }).catch(function () {
      announce('Copy failed. Select the text and copy it manually.');
    });
  });

  /* Reveal: explicit hooks plus the sections that carry the page rhythm. */
  var revealTargets = [];
  function addReveal(selector) {
    Array.prototype.forEach.call(document.querySelectorAll(selector), function (node) {
      if (revealTargets.indexOf(node) === -1) revealTargets.push(node);
    });
  }
  addReveal('[data-reveal]');
  addReveal('.section-head');
  addReveal('.problem-grid');
  addReveal('.faq-item');
  addReveal('.download-provenance');

  if ('IntersectionObserver' in window && !reduceMotion.matches && revealTargets.length) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.15 });
    revealTargets.forEach(function (target) {
      target.classList.add('reveal-pending');
      var parent = target.parentElement;
      if (parent) {
        var index = 0;
        Array.prototype.forEach.call(parent.children, function (sibling, i) {
          if (sibling === target) index = i;
        });
        if (index > 0) {
          target.style.setProperty('--stagger-i', String(Math.min(index, 6)));
        }
      }
      var stagger = target.querySelector('[data-motion-stagger]');
      if (stagger) {
        Array.prototype.forEach.call(stagger.children, function (row, index) {
          row.style.setProperty('--stagger-i', String(Math.min(index, 4)));
        });
      }
      observer.observe(target);
    });
    if (typeof reduceMotion.addEventListener === 'function') {
      reduceMotion.addEventListener('change', function (event) {
        if (!event.matches) return;
        revealTargets.forEach(function (target) {
          target.classList.add('is-visible');
          observer.unobserve(target);
        });
      });
    }
  } else {
    revealTargets.forEach(function (target) { target.classList.add('is-visible'); });
  }

  /* Header state on scroll */
  var header = document.querySelector('.site-header');
  if (header) {
    var headerTick = false;
    var syncHeader = function () {
      header.classList.toggle('is-scrolled', window.scrollY > 8);
    };
    syncHeader();
    window.addEventListener('scroll', function () {
      if (headerTick) return;
      headerTick = true;
      window.requestAnimationFrame(function () {
        headerTick = false;
        syncHeader();
      });
    }, { passive: true });
  }

  /* Year */
  var year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());

  /* Metadata */
  var buildState = document.getElementById('build-state');
  var manifest = document.querySelector('.manifest');
  var fallback = document.querySelector('.download-fallback');
  var commitLink = document.getElementById('commit-link');
  var releaseLink = document.getElementById('release-link');
  var shaNode = document.getElementById('apk-sha');
  var shaCopy = document.querySelector('[data-copy-target="apk-sha"]');

  function setBuildState(value, state) {
    if (!buildState) return;
    buildState.textContent = value;
    if (state) buildState.setAttribute('data-state', state);
    else buildState.removeAttribute('data-state');
    if (fallback) fallback.hidden = state !== 'error';
  }

  function addVersionQuery(token) {
    if (!token) return;
    Array.prototype.forEach.call(document.querySelectorAll('[data-apk-link]'), function (link) {
      var base = link.getAttribute('data-base-href') || link.getAttribute('href');
      if (!base) return;
      link.setAttribute('data-base-href', base);
      link.setAttribute('href', base + (base.indexOf('?') === -1 ? '?' : '&') + 'v=' + encodeURIComponent(token));
    });
  }

  function applyMetadata(data) {
    if (!data || typeof data !== 'object' || Array.isArray(data)) return false;
    var applied = 0;
    var version = text(data.version) || text(data.versionName);
    var versionCode = text(data.versionCode);
    var size = formatBytes(data.sizeBytes);
    var sha = text(data.sha256) || text(data.sha256sum);
    var signer = text(data.signerSha256) || text(data.signer);
    var built = formatBuiltAt(data.builtAt);
    var commit = text(data.commit) || text(data.commitSha);
    var releaseUrl = safeGithubUrl(data.releaseUrl);

    if (version && setMeta('version', version)) applied++;
    if (versionCode && setMeta('version-code', 'code ' + versionCode)) applied++;
    if (size && setMeta('size', size)) applied++;
    if (signer && setMeta('signer', signer)) applied++;

    if (built) {
      var builtNodes = document.querySelectorAll('[data-meta="built"]');
      Array.prototype.forEach.call(builtNodes, function (node) {
        node.textContent = built.label;
        if (node.tagName === 'TIME') node.setAttribute('datetime', built.iso);
      });
      if (builtNodes.length) applied++;
    }

    if (/^[0-9a-f]{7,40}$/i.test(commit)) {
      if (commitLink) {
        commitLink.href = 'https://github.com/engrtarun/t9-apk-tools/commit/' + commit;
        setMeta('commit', commit.slice(0, 7));
      }
      applied++;
    }

    if (isSha256(sha)) {
      if (setMeta('sha256', sha)) applied++;
      if (shaNode) shaNode.title = sha;
      if (shaCopy) shaCopy.hidden = false;
    }

    if (releaseUrl && releaseLink) releaseLink.href = releaseUrl;

    if (manifest) manifest.setAttribute('data-loaded', 'true');

    var complete = !!version && !!versionCode && !!size && isSha256(sha)
      && /^[0-9a-f]{7,40}$/i.test(commit) && !!built;
    if (fallback) fallback.hidden = complete;
    addVersionQuery(version || (isSha256(sha) ? sha.slice(0, 12) : ''));
    setBuildState(complete ? 'SIGNED BUILD' : 'MANIFEST UNAVAILABLE', complete ? 'signed' : 'error');
    return complete;
  }

  async function loadVersion() {
    setBuildState('READING VERSION.JSON', 'loading');
    var options = {
      cache: 'no-store',
      headers: { 'Accept': 'application/json' }
    };
    if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function') {
      options.signal = AbortSignal.timeout(8000);
    }
    var response;
    try {
      response = await fetch('download/version.json', options);
    } catch (error) {
      setBuildState('MANIFEST UNAVAILABLE', 'error');
      return;
    }
    if (!response.ok) { setBuildState('MANIFEST UNAVAILABLE', 'error'); return; }
    var data;
    try { data = await response.json(); } catch (error) {
      setBuildState('MANIFEST UNAVAILABLE', 'error');
      return;
    }
    if (!applyMetadata(data)) setBuildState('MANIFEST UNAVAILABLE', 'error');
  }

  loadVersion().catch(function () {
    setBuildState('MANIFEST UNAVAILABLE', 'error');
  });
})();
