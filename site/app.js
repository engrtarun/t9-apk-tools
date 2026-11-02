/* T9 APK Tools — landing page behaviour.
   No dependencies, no analytics. Optional metadata degrades quietly. */
(() => {
  'use strict';

  const doc = document;
  const GITHUB = 'https://github.com/engrtarun/t9-apk-tools';
  const reduceMotion = window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)')
    : null;

  /* ---------- header state ---------- */

  const header = doc.querySelector('.site-header');
  if (header) {
    const syncHeader = () => header.classList.toggle('is-scrolled', window.scrollY > 6);
    syncHeader();
    window.addEventListener('scroll', syncHeader, { passive: true });
  }

  /* ---------- mobile navigation ---------- */

  const navToggle = doc.querySelector('.nav-toggle');
  const nav = doc.getElementById('site-nav');

  if (navToggle && nav) {
    const setOpen = (open) => {
      navToggle.setAttribute('aria-expanded', String(open));
      navToggle.setAttribute('aria-label', open ? 'Close navigation menu' : 'Open navigation menu');
      nav.classList.toggle('is-open', open);
    };

    setOpen(false);

    navToggle.addEventListener('click', () => {
      setOpen(navToggle.getAttribute('aria-expanded') !== 'true');
    });

    nav.addEventListener('click', (event) => {
      if (event.target.closest('a')) setOpen(false);
    });

    doc.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && navToggle.getAttribute('aria-expanded') === 'true') {
        setOpen(false);
        navToggle.focus();
      }
    });

    if (window.matchMedia) {
      const desktop = window.matchMedia('(min-width: 901px)');
      const onChange = (event) => {
        if (event.matches) setOpen(false);
      };
      if (typeof desktop.addEventListener === 'function') desktop.addEventListener('change', onChange);
      else if (typeof desktop.addListener === 'function') desktop.addListener(onChange);
    }
  }

  /* ---------- current year ---------- */

  const year = doc.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());

  /* ---------- copy to clipboard ---------- */

  const copyStatus = doc.getElementById('copy-status');
  const copyButtons = Array.from(doc.querySelectorAll('[data-copy-value], [data-copy-target]'));
  const copyTimers = new WeakMap();
  let announceTimer = 0;

  const announce = (message) => {
    if (!copyStatus) return;
    window.clearTimeout(announceTimer);
    copyStatus.textContent = '';
    announceTimer = window.setTimeout(() => {
      copyStatus.textContent = message;
    }, 60);
  };

  const legacyCopy = (text) => {
    const area = doc.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.top = '-2000px';
    area.style.opacity = '0';
    doc.body.appendChild(area);
    area.select();
    let copied = false;
    try {
      copied = doc.execCommand('copy');
    } catch (error) {
      copied = false;
    }
    area.remove();
    return copied;
  };

  const copyText = async (text) => {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
    return legacyCopy(text);
  };

  const syncCopyButtons = () => {
    copyButtons.forEach((button) => {
      if (!button.dataset.copyTarget) return;
      const target = doc.getElementById(button.dataset.copyTarget);
      const value = target ? target.textContent.trim() : '';
      button.disabled = !value || value === '—';
    });
  };

  copyButtons.forEach((button) => {
    const label = button.querySelector('.copy-btn-label');
    const defaultLabel = label ? label.textContent : '';

    button.addEventListener('click', async () => {
      const target = button.dataset.copyTarget ? doc.getElementById(button.dataset.copyTarget) : null;
      const value = button.dataset.copyValue || (target ? target.textContent.trim() : '');
      if (!value || value === '—') return;

      try {
        const copied = await copyText(value);
        if (!copied) throw new Error('clipboard unavailable');

        button.classList.add('is-copied');
        if (label) label.textContent = 'Copied';

        const described = (button.getAttribute('aria-label') || 'Text').replace(/^Copy\s+/i, '');
        announce(`${described} copied to clipboard.`);

        window.clearTimeout(copyTimers.get(button));
        copyTimers.set(button, window.setTimeout(() => {
          button.classList.remove('is-copied');
          if (label) label.textContent = defaultLabel;
        }, 1900));
      } catch (error) {
        announce('Copy failed. Select the text and copy it manually.');
      }
    });
  });

  syncCopyButtons();

  /* ---------- build metadata ---------- */

  const clean = (value) => (typeof value === 'string' ? value.trim() : '');

  const setMeta = (name, value) => {
    if (!value) return;
    doc.querySelectorAll(`[data-meta="${name}"]`).forEach((node) => {
      node.textContent = value;
    });
  };

  const formatBytes = (bytes) => {
    const size = Number(bytes);
    if (!Number.isFinite(size) || size <= 0) return '';
    const mb = size / (1024 * 1024);
    if (mb >= 0.1) return `${mb.toFixed(1)} MB`;
    return `${Math.max(1, Math.round(size / 1024))} KB`;
  };

  const formatBuilt = (value) => {
    const raw = clean(value);
    if (!raw) return '';
    const date = new Date(raw);
    if (Number.isNaN(date.getTime())) return '';
    try {
      const formatter = new Intl.DateTimeFormat('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: 'UTC'
      });
      return `${formatter.format(date)} UTC`;
    } catch (error) {
      return '';
    }
  };

  const safeGithubUrl = (value) => {
    const raw = clean(value);
    if (!raw) return '';
    try {
      const url = new URL(raw);
      if (url.protocol !== 'https:') return '';
      if (url.hostname !== 'github.com' && url.hostname !== 'www.github.com') return '';
      return url.href;
    } catch (error) {
      return '';
    }
  };

  const applyMetadata = (data) => {
    if (!data || typeof data !== 'object' || Array.isArray(data)) return;

    const version = clean(data.version);
    const versionCode = clean(String(data.versionCode == null ? '' : data.versionCode));
    const sha = clean(data.sha256);
    const signer = clean(data.signerSha256);
    const commit = clean(data.commit);

    if (version) setMeta('version', version);
    if (versionCode) setMeta('version-code', ` · code ${versionCode}`);
    if (sha) setMeta('sha256', sha);
    if (signer) setMeta('signer', signer);

    const size = formatBytes(data.sizeBytes);
    if (size) setMeta('size', size);

    const built = formatBuilt(data.builtAt);
    if (built) setMeta('built', built);

    if (/^[0-9a-f]{7,40}$/i.test(commit)) {
      setMeta('commit', commit.slice(0, 7));
      const commitLink = doc.getElementById('commit-link');
      if (commitLink) commitLink.href = `${GITHUB}/commit/${commit}`;
    }

    const runUrl = safeGithubUrl(data.runUrl);
    if (runUrl) {
      doc.querySelectorAll('[data-build-link="run"]').forEach((link) => {
        link.href = runUrl;
      });
    }

    const releaseUrl = safeGithubUrl(data.releaseUrl);
    if (releaseUrl) {
      doc.querySelectorAll('[data-build-link="release"]').forEach((link) => {
        link.href = releaseUrl;
      });
    }

    const apkLink = doc.getElementById('apk-link');
    if (apkLink) {
      const token = version || versionCode || (sha ? sha.slice(0, 12) : '');
      if (token) apkLink.href = `download/t9-apk-tools.apk?v=${encodeURIComponent(token)}`;
    }

    const fallback = doc.querySelector('.download-fallback');
    if (fallback) fallback.hidden = true;

    const note = doc.querySelector('[data-meta="built-note"]');
    if (note) note.textContent = 'Read from version.json at page load. The APK link points at the current workflow artifact.';

    syncCopyButtons();
  };

  const loadMetadata = async () => {
    try {
      const response = await fetch('download/version.json', { cache: 'no-store' });
      if (!response.ok) return;
      const data = await response.json();
      applyMetadata(data);
    } catch (error) {
      /* No metadata published yet. Keep the honest fallback copy. */
    }
  };

  loadMetadata();

  /* ---------- reveal on scroll ---------- */

  const revealItems = Array.from(doc.querySelectorAll('[data-reveal]'));

  const revealAll = () => {
    revealItems.forEach((item) => item.classList.add('is-visible'));
  };

  if (revealItems.length && !(reduceMotion && reduceMotion.matches) && 'IntersectionObserver' in window) {
    try {
      revealItems.forEach((item, index) => {
        item.classList.add('reveal');
        item.style.setProperty('--reveal-index', String(index % 4));
      });

      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        });
      }, { rootMargin: '0px 0px -7% 0px', threshold: 0.08 });

      revealItems.forEach((item) => observer.observe(item));
    } catch (error) {
      revealAll();
    }
  }

  if (reduceMotion && typeof reduceMotion.addEventListener === 'function') {
    reduceMotion.addEventListener('change', (event) => {
      if (event.matches) revealAll();
    });
  }
})();
