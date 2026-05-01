/**
 * auto_click_fallbacks.js — GIGROUP Auto-Click Fallbacks J1-J10
 * ──────────────────────────────────────────────────────────────
 * NOVO ARQUIVO — não modifica nenhum script existente.
 * Injetado ADICIONALMENTE após android_bridge.js e content.js.
 * Tenta clicar automaticamente em links de vagas usando 10 estratégias.
 */
(function () {
  'use strict';
  if (window.__GIGROUP_FALLBACKS__) return;
  window.__GIGROUP_FALLBACKS__ = true;

  const LOG = (...a) => console.log('%c[GG-Fallbacks]', 'color:#ffc107;font-weight:700', ...a);

  const isVaga = u => u && (u.includes('gigroup') || u.includes('eventuais'));

  // ── J1: Clique direto em links <a> visíveis na tela ─────────────────────
  function J1() {
    const anchors = document.querySelectorAll('a[href]');
    for (const a of anchors) {
      if (isVaga(a.href)) {
        LOG('J1: clicando link <a>:', a.href);
        a.click();
        return true;
      }
    }
    return false;
  }

  // ── J2: dispatchEvent MouseEvent em elementos com data-url ──────────────
  function J2() {
    const els = document.querySelectorAll('[data-url],[data-href],[data-link]');
    for (const el of els) {
      const u = el.dataset.url || el.dataset.href || el.dataset.link;
      if (isVaga(u)) {
        LOG('J2: clicando elemento com data-url:', u);
        el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
        return true;
      }
    }
    return false;
  }

  // ── J3: Intercepta window.open ──────────────────────────────────────────
  function J3() {
    const orig = window.open;
    window.open = function (url, ...args) {
      if (isVaga(url)) {
        LOG('J3: window.open interceptado:', url);
        if (window.AndroidBridge) {
          window.AndroidBridge.postMessage(JSON.stringify({
            type: 'OPEN_LINK', url,
            groupName: 'GIGROUP', messageSnippet: 'via window.open'
          }));
        }
        return null;
      }
      return orig.call(this, url, ...args);
    };
    LOG('J3: window.open monitorado');
  }

  // ── J4: Intercepta location.href assignments ────────────────────────────
  function J4() {
    try {
      const desc = Object.getOwnPropertyDescriptor(window.location, 'href') ||
                   Object.getOwnPropertyDescriptor(Location.prototype, 'href');
      if (!desc || !desc.set) return;
      const origSet = desc.set;
      Object.defineProperty(window.location, 'href', {
        set(url) {
          if (isVaga(url)) {
            LOG('J4: location.href interceptado:', url);
            if (window.AndroidBridge) {
              window.AndroidBridge.postMessage(JSON.stringify({
                type: 'OPEN_LINK', url, groupName: 'GIGROUP', messageSnippet: 'via location.href'
              }));
            }
          }
          origSet.call(this, url);
        },
        get: desc.get,
        configurable: true
      });
      LOG('J4: location.href monitorado');
    } catch (e) { LOG('J4: não suportado'); }
  }

  // ── J5: Observer de novos links adicionados ao DOM ──────────────────────
  function J5() {
    new MutationObserver(muts => {
      for (const mut of muts) {
        for (const node of mut.addedNodes) {
          if (node.nodeType !== 1) continue;
          const links = [node, ...node.querySelectorAll('a[href],[data-url]')];
          for (const el of links) {
            const u = el.href || el.dataset?.url || '';
            if (isVaga(u)) {
              LOG('J5: novo link de vaga detectado no DOM:', u);
              // Envia via bridge para processamento com dedup + limites
              if (window.chrome && window.chrome.runtime) {
                window.chrome.runtime.sendMessage({
                  type: 'OPEN_LINK', url: u,
                  groupName: 'GIGROUP', messageSnippet: el.textContent?.slice(0, 80) || ''
                });
              }
            }
          }
        }
      }
    }).observe(document.body || document.documentElement, { childList: true, subtree: true });
    LOG('J5: MutationObserver em links ativos');
  }

  // ── J6: Polling de texto visível com URL de vaga ────────────────────────
  let j6last = '';
  function J6() {
    setInterval(() => {
      const text = document.body?.innerText || '';
      const match = text.match(/https?:\/\/[^\s"'<>]*gigroup[^\s"'<>]*/i) ||
                    text.match(/https?:\/\/[^\s"'<>]*eventuais[^\s"'<>]*/i);
      if (match && match[0] !== j6last) {
        j6last = match[0];
        LOG('J6: URL encontrada no texto visível:', match[0]);
        if (window.chrome && window.chrome.runtime) {
          window.chrome.runtime.sendMessage({
            type: 'OPEN_LINK', url: match[0],
            groupName: 'GIGROUP', messageSnippet: 'via J6 text scan'
          });
        }
      }
    }, 2000);
    LOG('J6: polling de texto ativo (2s)');
  }

  // ── J7: Intercepta fetch para capturar URLs de resposta ─────────────────
  function J7() {
    const origFetch = window.fetch;
    window.fetch = function (...args) {
      const url = typeof args[0] === 'string' ? args[0] : args[0]?.url || '';
      return origFetch.apply(this, args).then(res => {
        // Clona e analisa resposta JSON em busca de links de vagas
        res.clone().json().then(data => {
          const str = JSON.stringify(data);
          const m = str.match(/https?:\\\/\\\/[^"\\]*gigroup[^"\\]*/g) ||
                    str.match(/https?:\\\/\\\/[^"\\]*eventuais[^"\\]*/g);
          if (m) {
            m.forEach(u => {
              const clean = u.replace(/\\/g, '');
              LOG('J7: URL detectada em resposta fetch:', clean);
              if (window.chrome && window.chrome.runtime) {
                window.chrome.runtime.sendMessage({
                  type: 'OPEN_LINK', url: clean,
                  groupName: 'GIGROUP', messageSnippet: 'via J7 fetch intercept'
                });
              }
            });
          }
        }).catch(() => {});
        return res;
      });
    };
    LOG('J7: fetch monitorado');
  }

  // ── J8: Intercepta XMLHttpRequest ───────────────────────────────────────
  function J8() {
    const origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url, ...rest) {
      this.addEventListener('load', function () {
        try {
          const text = this.responseText || '';
          const m = text.match(/https?:\/\/[^\s"',}]*gigroup[^\s"',}]*/g) ||
                    text.match(/https?:\/\/[^\s"',}]*eventuais[^\s"',}]*/g);
          if (m) {
            m.forEach(u => {
              LOG('J8: URL detectada em XHR:', u);
              if (window.chrome && window.chrome.runtime) {
                window.chrome.runtime.sendMessage({
                  type: 'OPEN_LINK', url: u,
                  groupName: 'GIGROUP', messageSnippet: 'via J8 XHR'
                });
              }
            });
          }
        } catch (e) {}
      });
      return origOpen.apply(this, [method, url, ...rest]);
    };
    LOG('J8: XHR monitorado');
  }

  // ── J9: Simula clique em notificação do WhatsApp Web ────────────────────
  function J9() {
    // Detecta botão/área de notificação do grupo alvo e simula clique
    setInterval(() => {
      const TARGET = ['gigroup', 'shopee', 'juazeiro'];
      const rows = document.querySelectorAll('[role="row"],[role="listitem"],[data-testid*="cell"]');
      for (const row of rows) {
        const title = row.querySelector('span[title],._ao3e');
        const name = (title?.title || title?.textContent || '').toLowerCase();
        if (!TARGET.some(k => name.includes(k))) continue;
        const badge = row.querySelector('[data-testid="icon-unread-count"]');
        if (badge && parseInt(badge.textContent) > 0) {
          LOG('J9: badge detectado, clicando no grupo:', name);
          row.click();
          break;
        }
      }
    }, 3000);
    LOG('J9: badge watcher ativo (3s)');
  }

  // ── J10: Tenta notificação push nativa via Notification API ─────────────
  function J10() {
    if (!('Notification' in window)) return;
    if (Notification.permission === 'granted') {
      LOG('J10: Notification API disponível');
    }
    // Intercepta criação de notificações para capturar dados
    const origNotif = window.Notification;
    window.Notification = function (title, opts) {
      if (opts && opts.body) {
        const text = (title + ' ' + opts.body);
        const m = text.match(/https?:\/\/[^\s"'<>]*(?:gigroup|eventuais)[^\s"'<>]*/i);
        if (m) {
          LOG('J10: URL em Notification:', m[0]);
          if (window.chrome && window.chrome.runtime) {
            window.chrome.runtime.sendMessage({
              type: 'OPEN_LINK', url: m[0],
              groupName: title || 'GIGROUP', messageSnippet: opts.body?.slice(0, 80) || ''
            });
          }
        }
      }
      return new origNotif(title, opts);
    };
    Object.assign(window.Notification, origNotif);
    LOG('J10: Notification interceptada');
  }

  // ── Inicializa todos os fallbacks ────────────────────────────────────────
  function init() {
    J3(); J4(); J5(); J6(); J7(); J8(); J9(); J10();
    // J1 e J2 são chamados pontualmente quando necessário
    LOG('🚀 Fallbacks J1-J10 inicializados');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Expõe J1/J2 para uso manual se necessário
  window.__GG_clickLinks = J1;
  window.__GG_clickDataUrls = J2;
})();
