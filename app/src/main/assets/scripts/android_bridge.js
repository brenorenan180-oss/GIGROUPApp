/**
 * android_bridge.js — GIGROUP Monitor v2.0
 * ─────────────────────────────────────────
 * Substitui chrome.runtime e chrome.storage para funcionar no Android WebView.
 * Implementa N1–N6 de detecção de novas mensagens no grupo alvo.
 * Gerencia o fluxo: detectar → abrir link → preencher formulário → voltar ao WhatsApp.
 */
(function () {
  'use strict';

  if (window.__GIGROUP_BRIDGE_V2__) return;
  window.__GIGROUP_BRIDGE_V2__ = true;

  const LOG = (...a) => console.log('%c[GIGROUP-Bridge]', 'color:#25d366;font-weight:700', ...a);

  // ═══════════════════════════════════════════════════════════════════════════
  // ESTADO PERSISTENTE (sessionStorage + memória)
  // ═══════════════════════════════════════════════════════════════════════════
  const state = {
    returnToWhatsApp: sessionStorage.getItem('gg_returnUrl') || null,
    pendingForm:      sessionStorage.getItem('gg_pendingForm') === 'true',
    lastGroup:        sessionStorage.getItem('gg_lastGroup') || null,
  };

  function saveState() {
    if (state.returnToWhatsApp) sessionStorage.setItem('gg_returnUrl', state.returnToWhatsApp);
    sessionStorage.setItem('gg_pendingForm', String(state.pendingForm));
    if (state.lastGroup) sessionStorage.setItem('gg_lastGroup', state.lastGroup);
  }

  function clearState() {
    sessionStorage.removeItem('gg_returnUrl');
    sessionStorage.removeItem('gg_pendingForm');
    sessionStorage.removeItem('gg_lastGroup');
    state.returnToWhatsApp = null;
    state.pendingForm = false;
    state.lastGroup = null;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CHROME.RUNTIME SHIM
  // ═══════════════════════════════════════════════════════════════════════════
  if (!window.chrome) window.chrome = {};
  if (!window.chrome.runtime) window.chrome.runtime = {};

  window.chrome.runtime.sendMessage = function (msg, callback) {
    if (!msg || !msg.type) return;
    LOG('sendMessage →', msg.type, msg.url || '');

    switch (msg.type) {

      case 'OPEN_LINK': {
        // Salva URL atual do WhatsApp para retorno depois do formulário
        state.returnToWhatsApp = window.location.href;
        state.pendingForm = true;
        state.lastGroup = msg.groupName || '';
        saveState();

        // Informa o Java para registrar e abrir a URL
        if (window.AndroidBridge) {
          window.AndroidBridge.postMessage(JSON.stringify(msg));
        }
        if (typeof callback === 'function') setTimeout(() => callback({ success: true }), 0);
        break;
      }

      case 'FORM_PREENCHIDO': {
        LOG('Formulário preenchido! Voltando ao WhatsApp em 8s...');
        if (window.AndroidBridge) {
          window.AndroidBridge.postMessage(JSON.stringify(msg));
        }
        // Retorno automático após 8 segundos
        const retUrl = state.returnToWhatsApp || 'https://web.whatsapp.com/';
        setTimeout(() => {
          clearState();
          window.location.href = retUrl;
        }, 8000);
        if (typeof callback === 'function') setTimeout(() => callback({ ok: true }), 0);
        break;
      }

      case 'GET_STATE': {
        try {
          const s = JSON.parse(window.AndroidBridge.getStateJson());
          if (typeof callback === 'function') setTimeout(() => callback(s), 0);
        } catch (e) {
          if (typeof callback === 'function') setTimeout(() => callback({}), 0);
        }
        break;
      }

      case 'GET_CONFIG': {
        try {
          const c = JSON.parse(window.AndroidBridge.getConfigJson());
          if (typeof callback === 'function') setTimeout(() => callback(c), 0);
        } catch (e) {
          if (typeof callback === 'function') setTimeout(() => callback({}), 0);
        }
        break;
      }

      default:
        if (window.AndroidBridge) {
          window.AndroidBridge.postMessage(JSON.stringify(msg));
        }
        if (typeof callback === 'function') setTimeout(() => callback({ ok: true }), 0);
    }
  };

  Object.defineProperty(window.chrome.runtime, 'lastError', {
    get: () => null, configurable: true
  });

  if (!window.chrome.runtime.onMessage) {
    window.chrome.runtime.onMessage = {
      _listeners: [],
      addListener(fn) { this._listeners.push(fn); },
      removeListener(fn) { this._listeners = this._listeners.filter(f => f !== fn); },
      dispatch(msg) {
        this._listeners.forEach(fn => { try { fn(msg, null, () => {}); } catch (e) {} });
      }
    };
  }

  // chrome.storage.local shim
  if (!window.chrome.storage) window.chrome.storage = {};
  window.chrome.storage.local = {
    get(keys, cb) {
      try {
        const s = JSON.parse(window.AndroidBridge.getStateJson());
        if (typeof cb === 'function') setTimeout(() => cb(s), 0);
      } catch (e) {
        if (typeof cb === 'function') setTimeout(() => cb({}), 0);
      }
    },
    set(obj, cb) {
      if (window.AndroidBridge) {
        window.AndroidBridge.postMessage(JSON.stringify({ type: 'STORAGE_SET', data: obj }));
      }
      if (typeof cb === 'function') setTimeout(cb, 0);
    }
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // DETECÇÃO DE MENSAGENS — só ativa no WhatsApp Web
  // ═══════════════════════════════════════════════════════════════════════════

  const TARGET_GROUP_NAMES = [
    'gigroup - shopee',
    'gigroup simulação',
    'gigroup',
    'shopee juazeiro'
  ];

  function isTargetGroup(text) {
    if (!text) return false;
    const t = text.toLowerCase();
    return TARGET_GROUP_NAMES.some(name => t.includes(name));
  }

  function extractUrl(text) {
    if (!text) return null;
    const m = text.match(/https?:\/\/[^\s"'<>]+/);
    return m ? m[0] : null;
  }

  function isGigroupUrl(url) {
    if (!url) return false;
    return url.includes('gigroup') || url.includes('eventuais');
  }

  function dispatchOpenLink(url, groupName, snippet) {
    LOG('🔗 Link detectado:', url);
    window.chrome.runtime.sendMessage({
      type: 'OPEN_LINK',
      url,
      groupName: groupName || 'GIGROUP',
      messageSnippet: (snippet || '').slice(0, 80)
    });
  }

  // ── Verifica se está no WhatsApp Web ──────────────────────────────────────
  const isWhatsApp = () => location.hostname.includes('whatsapp.com');

  if (!isWhatsApp()) {
    LOG('Não é WhatsApp Web — bridge instalada apenas (sem detecção N1-N6)');
    return;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N1 — MutationObserver na lista de chats
  // ═══════════════════════════════════════════════════════════════════════════
  function startN1() {
    const tryAttach = () => {
      const chatList = document.querySelector(
        '[data-testid="chat-list"], #pane-side, [aria-label*="Chat"], [aria-label*="chat"]'
      );
      if (!chatList) return false;

      const obs = new MutationObserver(mutations => {
        for (const mut of mutations) {
          for (const node of mut.addedNodes) {
            if (node.nodeType !== 1) continue;
            const text = node.textContent || '';
            // Verifica se é do grupo alvo
            const groupEl = node.querySelector('[data-testid="cell-frame-title"]') ||
                            node.querySelector('span[title]') ||
                            node.querySelector('._ao3e');
            const groupName = groupEl?.title || groupEl?.textContent || '';
            if (!isTargetGroup(groupName) && !isTargetGroup(text)) continue;

            // Verifica se tem mensagem nova com link
            const lastMsgEl = node.querySelector('[data-testid="last-msg-status"]~span, ._ao3e~._ao3e, [class*="message"]');
            const msgText = lastMsgEl?.textContent || text;
            const url = extractUrl(msgText);
            if (url && isGigroupUrl(url)) {
              dispatchOpenLink(url, groupName, msgText);
            }
          }
        }
      });

      obs.observe(chatList, { childList: true, subtree: true });
      LOG('N1 ✅ MutationObserver na lista de chats');
      return true;
    };

    // Tenta imediatamente, depois com retry
    if (!tryAttach()) {
      let attempts = 0;
      const interval = setInterval(() => {
        if (tryAttach() || attempts++ > 30) clearInterval(interval);
      }, 1000);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N2 — Badge de mensagens não lidas
  // ═══════════════════════════════════════════════════════════════════════════
  function startN2() {
    const obs = new MutationObserver(() => {
      const badges = document.querySelectorAll(
        '[data-testid="icon-unread-count"], [aria-label*="mensagem"], [class*="unread-count"]'
      );
      badges.forEach(badge => {
        const count = parseInt(badge.textContent) || 0;
        if (count <= 0) return;

        const row = badge.closest('[role="row"], [role="listitem"], [data-testid*="cell"]');
        if (!row) return;

        const titleEl = row.querySelector('[data-testid="cell-frame-title"], span[title], ._ao3e');
        const groupName = titleEl?.title || titleEl?.textContent || '';
        if (!isTargetGroup(groupName)) return;

        // Abre o chat para capturar o link
        openChatAndExtractLink(row, groupName);
      });
    });

    obs.observe(document.body, { childList: true, subtree: true, characterData: true });
    LOG('N2 ✅ Observador de badges de não lidos');
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N3 — Polling de mensagens abertas (verifica mensagens dentro do chat)
  // ═══════════════════════════════════════════════════════════════════════════
  let n3LastMsgId = '';
  function startN3() {
    setInterval(() => {
      // Verifica se o chat atual é do grupo alvo
      const headerTitle = document.querySelector(
        '[data-testid="conversation-header"] span[title], #main header span[title], ._ao3e[title]'
      );
      const groupName = headerTitle?.title || headerTitle?.textContent || '';
      if (!isTargetGroup(groupName)) return;

      // Pega últimas mensagens visíveis
      const msgs = document.querySelectorAll(
        '[data-id], [data-testid="msg-container"], .message-in, ._ao3e[data-id]'
      );
      if (!msgs.length) return;

      const last = msgs[msgs.length - 1];
      const msgId = last.getAttribute('data-id') || last.innerHTML.length.toString();
      if (msgId === n3LastMsgId) return;
      n3LastMsgId = msgId;

      const text = last.textContent || '';
      const url = extractUrl(text);
      if (url && isGigroupUrl(url)) {
        dispatchOpenLink(url, groupName, text);
      }
    }, 1500);
    LOG('N3 ✅ Polling de mensagens (1.5s)');
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N4 — Monitoramento do título da página (contador de notificações)
  // ═══════════════════════════════════════════════════════════════════════════
  let n4LastTitle = document.title;
  let n4LastCount = 0;
  function startN4() {
    setInterval(() => {
      const title = document.title;
      if (title === n4LastTitle) return;
      n4LastTitle = title;

      // WhatsApp Web: "(3) WhatsApp" quando tem 3 mensagens não lidas
      const match = title.match(/\((\d+)\)/);
      if (!match) return;
      const count = parseInt(match[1]);
      if (count <= n4LastCount) return;
      n4LastCount = count;

      LOG('N4: Título mudou, novas mensagens:', count);
      // Verifica o chat do grupo alvo
      checkTargetGroupUnread();
    }, 500);
    LOG('N4 ✅ Monitor de título da página');
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N5 — visibilitychange (quando usuário volta ao app)
  // ═══════════════════════════════════════════════════════════════════════════
  function startN5() {
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) return;
      LOG('N5: Página ficou visível — verificando mensagens não lidas');
      setTimeout(checkTargetGroupUnread, 800);
    });
    LOG('N5 ✅ visibilitychange listener');
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // N6 — Observer de estilo/classe (fundo diferente = não lida)
  // ═══════════════════════════════════════════════════════════════════════════
  function startN6() {
    const tryAttach = () => {
      const pane = document.querySelector('#pane-side, [data-testid="chat-list"]');
      if (!pane) return false;

      const obs = new MutationObserver(mutations => {
        for (const mut of mutations) {
          if (mut.type !== 'attributes') continue;
          const el = mut.target;
          const classes = el.className || '';
          // Detecta elemento que ficou "highlighted" ou "unread"
          if (!classes.includes('unread') && !classes.includes('highlighted')) continue;
          const titleEl = el.querySelector('span[title], ._ao3e');
          const groupName = titleEl?.title || titleEl?.textContent || '';
          if (isTargetGroup(groupName)) {
            openChatAndExtractLink(el, groupName);
          }
        }
      });

      obs.observe(pane, { attributes: true, subtree: true, attributeFilter: ['class', 'style'] });
      LOG('N6 ✅ Observer de estilo/classe');
      return true;
    };

    if (!tryAttach()) {
      let attempts = 0;
      const interval = setInterval(() => {
        if (tryAttach() || attempts++ > 30) clearInterval(interval);
      }, 1000);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS DE AÇÃO
  // ═══════════════════════════════════════════════════════════════════════════

  function checkTargetGroupUnread() {
    // Procura o item do grupo alvo na lista de chats
    const rows = document.querySelectorAll(
      '[role="row"], [role="listitem"], [data-testid*="cell"]'
    );
    for (const row of rows) {
      const titleEl = row.querySelector('span[title], ._ao3e');
      const groupName = titleEl?.title || titleEl?.textContent || '';
      if (!isTargetGroup(groupName)) continue;

      // Tem badge de não lido?
      const badge = row.querySelector('[data-testid="icon-unread-count"], [aria-label*="mensagem"]');
      if (badge && parseInt(badge.textContent) > 0) {
        openChatAndExtractLink(row, groupName);
      }
    }
  }

  let openingChat = false;
  function openChatAndExtractLink(chatRow, groupName) {
    if (openingChat) return;
    openingChat = true;

    LOG('Clicando no grupo para extrair link:', groupName);
    chatRow.click();

    setTimeout(() => {
      // Pega as últimas 5 mensagens visíveis
      const msgs = document.querySelectorAll('[data-id], [data-testid="msg-container"]');
      const lastFive = Array.from(msgs).slice(-5);

      for (const msg of lastFive) {
        const text = msg.textContent || '';
        const url = extractUrl(text);
        if (url && isGigroupUrl(url)) {
          openingChat = false;
          dispatchOpenLink(url, groupName, text);
          return;
        }
        // Também verifica links <a> dentro da mensagem
        const anchors = msg.querySelectorAll('a[href]');
        for (const a of anchors) {
          const href = a.href;
          if (isGigroupUrl(href)) {
            openingChat = false;
            dispatchOpenLink(href, groupName, text);
            return;
          }
        }
      }
      openingChat = false;
    }, 1500);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // INICIALIZAÇÃO — espera o DOM do WhatsApp carregar
  // ═══════════════════════════════════════════════════════════════════════════
  function startAllDetectors() {
    startN1();
    startN2();
    startN3();
    startN4();
    startN5();
    startN6();
    LOG('🚀 Todos os detectores N1-N6 ativos');
  }

  // Aguarda o WhatsApp Web carregar completamente
  function waitForWhatsApp() {
    const ready = document.querySelector(
      '[data-testid="chat-list"], #pane-side, [data-asset-intro-image]'
    );
    if (ready) {
      startAllDetectors();
    } else {
      const obs = new MutationObserver(() => {
        if (document.querySelector('[data-testid="chat-list"], #pane-side')) {
          obs.disconnect();
          setTimeout(startAllDetectors, 500);
        }
      });
      obs.observe(document.body || document.documentElement, { childList: true, subtree: true });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', waitForWhatsApp);
  } else {
    waitForWhatsApp();
  }

  // Expõe função para o MainActivity disparar eventos de volta
  window.__GIGROUP_dispatchEnabled = function (enabled) {
    window.chrome.runtime.onMessage.dispatch({ type: 'ENABLED_CHANGED', enabled });
  };

  LOG('✅ Bridge v2.0 instalada');
})();
