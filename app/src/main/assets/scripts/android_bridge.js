/**
 * android_bridge.js — Substitui chrome.runtime.sendMessage e chrome.storage
 * por chamadas ao AndroidBridge (JavascriptInterface) no WebView Android.
 *
 * Este script é injetado ANTES do content.js para que o shim já esteja
 * disponível quando o script de detecção inicializar.
 */
(function () {
  'use strict';

  if (window.__GIGROUP_BRIDGE_INSTALLED__) return;
  window.__GIGROUP_BRIDGE_INSTALLED__ = true;

  // ── Shim do chrome.runtime ───────────────────────────────────────────────
  if (!window.chrome) window.chrome = {};
  if (!window.chrome.runtime) window.chrome.runtime = {};

  // Fila de callbacks GET_STATE
  const _stateCallbacks = [];

  window.chrome.runtime.sendMessage = function (msg, callback) {
    if (!msg || !msg.type) return;

    try {
      switch (msg.type) {

        case 'GET_STATE': {
          const stateJson = window.AndroidBridge.getStateJson();
          const state = JSON.parse(stateJson);
          if (typeof callback === 'function') {
            setTimeout(() => callback(state), 0);
          }
          break;
        }

        case 'GET_CONFIG': {
          const cfgJson = window.AndroidBridge.getConfigJson();
          const cfg = JSON.parse(cfgJson);
          if (typeof callback === 'function') {
            setTimeout(() => callback(cfg), 0);
          }
          break;
        }

        case 'OPEN_LINK':
        case 'FORM_PREENCHIDO':
        case 'SET_ENABLED':
        case 'SAVE_SETTINGS':
        case 'SAVE_CONFIG':
        case 'CLEAR_HISTORY':
          // Encaminha ao AndroidBridge como JSON
          window.AndroidBridge.postMessage(JSON.stringify(msg));
          if (typeof callback === 'function') {
            setTimeout(() => callback({ ok: true }), 0);
          }
          break;

        default:
          console.warn('[GIGROUP-Bridge] Tipo desconhecido:', msg.type);
      }
    } catch (e) {
      console.error('[GIGROUP-Bridge] Erro:', e);
    }
  };

  // chrome.runtime.lastError — sempre null no contexto Android
  Object.defineProperty(window.chrome.runtime, 'lastError', {
    get: () => null,
    configurable: true
  });

  // chrome.runtime.onMessage — stub (mensagens vêm do JS injetado, não do BG)
  if (!window.chrome.runtime.onMessage) {
    window.chrome.runtime.onMessage = {
      _listeners: [],
      addListener(fn) { this._listeners.push(fn); },
      removeListener(fn) {
        this._listeners = this._listeners.filter(f => f !== fn);
      },
      // Permite que o MainActivity dispare mensagens de volta ao JS
      dispatch(msg) {
        this._listeners.forEach(fn => {
          try { fn(msg, null, () => {}); } catch (e) {}
        });
      }
    };
  }

  // ── Shim do chrome.storage.local ─────────────────────────────────────────
  // Usa AndroidBridge.getStateJson como fonte de verdade; writes vão via postMessage
  if (!window.chrome.storage) window.chrome.storage = {};
  window.chrome.storage.local = {
    get(keys, callback) {
      try {
        const state = JSON.parse(window.AndroidBridge.getStateJson());
        if (typeof callback === 'function') {
          setTimeout(() => callback(state), 0);
        }
      } catch (e) {
        if (typeof callback === 'function') setTimeout(() => callback({}), 0);
      }
    },
    set(obj, callback) {
      // Delega ao postMessage para que o AndroidBridge processe
      window.AndroidBridge.postMessage(JSON.stringify({ type: 'STORAGE_SET', data: obj }));
      if (typeof callback === 'function') setTimeout(callback, 0);
    }
  };

  // ── Expõe função para MainActivity disparar ENABLED_CHANGED ──────────────
  window.__GIGROUP_dispatchEnabled = function (enabled) {
    if (window.chrome.runtime.onMessage) {
      window.chrome.runtime.onMessage.dispatch({ type: 'ENABLED_CHANGED', enabled });
    }
  };

  console.log('[GIGROUP-Bridge] ✅ Android bridge instalada.');
})();
