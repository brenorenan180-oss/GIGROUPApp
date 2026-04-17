// content.js — GIGROUP Link Opener v7.0
// ════════════════════════════════════════════════════════════════════════════
// MECÂNICAS EXISTENTES (INTOCÁVEIS): Original, FB1, FB2, FB3, FB4, FB5
// NOVAS MECÂNICAS: FB6, FB7, FB8, FB9, FB10
// ════════════════════════════════════════════════════════════════════════════
"use strict";

(function () {

  const TARGET_GROUP = "GIGROUP SIMULAÇÃO";
  const SPAM_GAP_MS  = 3000;
  const LOG = function () {
    var a = Array.prototype.slice.call(arguments);
    a.unshift("[GIGROUP]");
    console.log.apply(console, a);
  };

  const URL_RE = /https?:\/\/(www\.)?[-\w@:%.\+~#=]{2,256}\.[a-z]{2,6}\b([-\w@:%\+.~#?&\/=]*)/gi;

  let enabled        = true;
  let lastActionAt   = 0;
  const processedIds = new Set();

  chrome.runtime.sendMessage({ type: "GET_STATE" }, function (res) {
    if (res) enabled = res.enabled !== false;
  });
  chrome.runtime.onMessage.addListener(function (msg) {
    if (msg.type === "ENABLED_CHANGED") enabled = msg.enabled;
  });

  var observer = new MutationObserver(function (mutations) {
    if (!enabled) return;
    for (var i = 0; i < mutations.length; i++) {
      var nodes = mutations[i].addedNodes;
      for (var j = 0; j < nodes.length; j++) {
        if (nodes[j].nodeType === 1) scanNode(nodes[j]);
      }
    }
  });

  observer.observe(document.documentElement, { childList: true, subtree: true });
  LOG("v7.0 ativo.");

  // ── LOG DE DIAGNÓSTICO — revela formato real do data-id ──────────────────
  var _diagDone = false;
  var _diagObs = new MutationObserver(function(mutations) {
    if (_diagDone) return;
    for (var i = 0; i < mutations.length; i++) {
      var nodes = mutations[i].addedNodes;
      for (var j = 0; j < nodes.length; j++) {
        var n = nodes[j];
        if (n.nodeType !== 1) continue;
        var candidates = n.hasAttribute && n.hasAttribute("data-id")
          ? [n]
          : (n.querySelectorAll ? Array.from(n.querySelectorAll("[data-id]")) : []);
        for (var k = 0; k < candidates.length; k++) {
          var did = candidates[k].getAttribute("data-id");
          if (did) {
            LOG("🔍 DIAG data-id encontrado:", did.slice(0, 40),
                "| classes:", candidates[k].className.slice(0, 60));
            _diagDone = true;
            break;
          }
        }
        if (_diagDone) break;
      }
      if (_diagDone) break;
    }
  });
  _diagObs.observe(document.documentElement, { childList: true, subtree: true });

  // ════════════════════════════════════════════════════════════════════════════
  // SCAN PRINCIPAL — chama todas as mecânicas
  // ════════════════════════════════════════════════════════════════════════════

  function scanNode(node) {
    if (isMsgContainer(node)) {
      tryHandle(node);            // mecânica original (false_ + CLIENTE)
      tryHandleFallback1(node);   // FB1: mensagem própria + gigroup
      tryHandleFallback2(node);   // FB2: keyword vaga + gigroup (sem CLIENTE)
      tryHandleFallback3(node);   // FB3: qualquer data-id + CLIENTE (sem prefixo)
      tryHandleFallback4(node);   // FB4: <a href> gigroup direto
      tryHandleFallback6(node);   // FB6: card de preview de link
      tryHandleFallback8(node);   // FB8: mensagens encaminhadas
      tryHandleFallback9(node);   // FB9: URL gigroup em texto puro (sem <a>)
      return;
    }
    if (!node.querySelectorAll) return;
    var cs = node.querySelectorAll("[data-id]");
    for (var i = 0; i < cs.length; i++) {
      tryHandle(cs[i]);
      tryHandleFallback1(cs[i]);
      tryHandleFallback2(cs[i]);
      tryHandleFallback3(cs[i]);
      tryHandleFallback4(cs[i]);
      tryHandleFallback6(cs[i]);
      tryHandleFallback8(cs[i]);
      tryHandleFallback9(cs[i]);
    }
    tryHandleFallback4(node);
    tryHandleFallback6(node);
    tryHandleFallback9(node);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // MECÂNICAS ORIGINAIS — INTOCÁVEIS
  // ════════════════════════════════════════════════════════════════════════════

  function tryHandle(el) {
    var dataId = el.getAttribute("data-id");
    if (!dataId || dataId.indexOf("false_") !== 0) return;
    if (processedIds.has(dataId)) return;
    processedIds.add(dataId);
    if (!isTargetGroup()) return;

    var text = getText(el);
    if (text.toUpperCase().indexOf("CLIENTE") === -1) return;

    var url = getFirstUrl(el, text);
    if (!url) return;

    LOG("🔗 CLIENTE + link:", url);

    var now = Date.now();
    if (now - lastActionAt < SPAM_GAP_MS) { LOG("anti-spam, ignorado."); return; }
    lastActionAt = now;

    chrome.runtime.sendMessage({
      type: "OPEN_LINK", url: url,
      groupName: TARGET_GROUP, messageSnippet: text.slice(0, 80)
    }, function (res) {
      if (chrome.runtime.lastError) { LOG("BG error:", chrome.runtime.lastError.message); return; }
      if (res && res.success) LOG("✅ aba aberta. hoje:", res.todayCount);
      else if (res)           LOG("⚠️ não aberto:", res.reason);
    });
  }

  // ── FALLBACK 1 — mensagem própria (true_) com domínio gigroup no link ──────
  function tryHandleFallback1(el) {
    var dataId = el.getAttribute("data-id");
    if (!dataId || dataId.indexOf("true_") !== 0) return;
    if (processedIds.has(dataId)) return;
    if (!isTargetGroup()) return;

    var text = getText(el);
    var url  = getFirstUrl(el, text);
    if (!url) return;
    if (!isGigroupUrl(url)) return;

    processedIds.add(dataId);
    LOG("🔗 [FB1] mensagem própria + link gigroup:", url);

    openLink(url, text, "FB1-própria");
  }

  // ── FALLBACK 2 — mensagem recebida sem CLIENTE mas com gigroup + keywords ──
  var FB2_KEYWORDS_RE = /\b(vaga|oportunidade|candidato|formul[aá]rio|cadastro|inscri[cç][aã]o|shopee|logis|auxiliar|cliente\s*:|servi[cç]o\s*:)/i;

  function tryHandleFallback2(el) {
    var dataId = el.getAttribute("data-id");
    if (!dataId || dataId.indexOf("false_") !== 0) return;
    if (processedIds.has(dataId)) return;
    if (!isTargetGroup()) return;

    var text = getText(el);
    if (!FB2_KEYWORDS_RE.test(text)) return;

    var url = getFirstUrl(el, text);
    if (!url) return;
    if (!isGigroupUrl(url) && !isFormUrl(url)) return;

    processedIds.add(dataId);
    LOG("🔗 [FB2] keyword vaga + link gigroup (sem CLIENTE):", url);

    openLink(url, text, "FB2-keyword");
  }

  // ── FALLBACK 3 — qualquer data-id (sem checar prefixo) + CLIENTE + link ──
  var fb3ProcessedIds = new Set();
  function tryHandleFallback3(el) {
    var dataId = el.getAttribute("data-id");
    if (!dataId) return;
    if (processedIds.has(dataId) || fb3ProcessedIds.has(dataId)) return;
    if (!isTargetGroup()) return;

    var text = getText(el);
    if (!text) return;
    if (text.toUpperCase().indexOf("CLIENTE") === -1) return;

    var url = getFirstUrl(el, text);
    if (!url) return;

    fb3ProcessedIds.add(dataId);
    LOG("🔗 [FB3] data-id qualquer + CLIENTE + link:", url, "| id:", dataId.slice(0,30));
    openLink(url, text, "FB3-anyid");
  }

  // ── FALLBACK 4 — scan por <a href> de gigroup em qualquer mensagem do grupo ─
  var fb4ProcessedHrefs = new Set();
  function tryHandleFallback4(el) {
    if (!isTargetGroup()) return;
    var links = el.querySelectorAll ? el.querySelectorAll("a[href*='gigroup'], a[href*='eventuais']") : [];
    for (var i = 0; i < links.length; i++) {
      var url = links[i].href;
      if (!isValidUrl(url)) continue;
      if (fb4ProcessedHrefs.has(url)) continue;
      fb4ProcessedHrefs.add(url);
      var text = getText(el.closest ? (el.closest("[data-id]") || el) : el) || url;
      LOG("🔗 [FB4] link gigroup detectado via <a href>:", url);
      openLink(url, text, "FB4-href");
    }
  }

  // ── FALLBACK 5 — varredura periódica do DOM visível (polling) ─────────────
  var fb5LastScan = 0;
  var fb5ProcessedHrefs = new Set();
  function fb5ScanVisible() {
    if (!enabled || !isTargetGroup()) return;
    var now = Date.now();
    if (now - fb5LastScan < 2000) return;
    fb5LastScan = now;

    var links = document.querySelectorAll(
      "#main a[href*='gigroup'], #main a[href*='eventuais'], " +
      "[data-testid='conversation-panel-messages'] a[href*='gigroup'], " +
      "[data-testid='conversation-panel-messages'] a[href*='eventuais']"
    );
    for (var i = 0; i < links.length; i++) {
      var url = links[i].href;
      if (!isValidUrl(url)) continue;
      if (fb5ProcessedHrefs.has(url)) continue;
      fb5ProcessedHrefs.add(url);
      var container = links[i].closest ? links[i].closest("[data-id]") : null;
      var text = container ? getText(container) : (links[i].textContent || url);
      LOG("🔗 [FB5] polling DOM — link gigroup:", url);
      openLink(url, text, "FB5-poll");
    }
  }
  setInterval(fb5ScanVisible, 2500);

  // ════════════════════════════════════════════════════════════════════════════
  // NOVAS MECÂNICAS — FB6, FB7, FB8, FB9, FB10
  // ════════════════════════════════════════════════════════════════════════════

  // ── FALLBACK 6 — Card de pré-visualização de link (Link Preview) ───────────
  // WhatsApp renderiza um card rico para links com imagem, título e descrição.
  // O card pode ter href oculto em data-* ou em elementos filhos específicos.
  // Seletores que identificam cards de preview no WhatsApp Web:
  //   [data-testid="link-preview"], .linked-preview, ._amim, [class*="preview"]
  var fb6ProcessedHrefs = new Set();
  function tryHandleFallback6(el) {
    if (!isTargetGroup()) return;
    if (!el.querySelectorAll) return;

    // Seletores de cards de preview do WhatsApp Web
    var previewSels = [
      "[data-testid='link-preview']",
      "[data-testid='media-url-preview']",
      "a[href*='gigroup'][class*='preview']",
      "a[href*='eventuais'][class*='preview']",
      "._amim",          // classe interna de preview card
      "[class*='link-prev']",
      "[class*='linked-prev']"
    ];

    for (var s = 0; s < previewSels.length; s++) {
      var cards;
      try { cards = el.querySelectorAll(previewSels[s]); } catch(e) { continue; }
      for (var i = 0; i < cards.length; i++) {
        var card = cards[i];

        // Tenta extrair URL do card: href direto, data-url, ou primeiro link filho
        var url = null;
        if (card.tagName === "A" && card.href) url = card.href;
        if (!url) url = card.getAttribute("data-url") || card.getAttribute("data-href");
        if (!url) {
          var aChild = card.querySelector("a[href]");
          if (aChild) url = aChild.href;
        }
        if (!url) {
          // Fallback: extrai via regex no textContent
          URL_RE.lastIndex = 0;
          var m = URL_RE.exec(card.textContent || "");
          if (m) url = m[0];
        }

        if (!url || !isValidUrl(url)) continue;
        if (!isGigroupUrl(url) && !isFormUrl(url)) continue;
        if (fb6ProcessedHrefs.has(url)) continue;
        fb6ProcessedHrefs.add(url);

        var container = card.closest ? (card.closest("[data-id]") || card) : card;
        var text = getText(container) || url;
        LOG("🔗 [FB6] card de preview detectado:", url);
        openLink(url, text, "FB6-preview");
      }
    }
  }

  // ── FALLBACK 7 — Interceptação de clipboard (URL copiada pelo usuário) ─────
  // Quando o usuário copia um link do gigroup para o clipboard na aba do WA,
  // a extensão captura via evento 'copy' e dispara a abertura.
  // Só age se o grupo alvo estiver ativo.
  var fb7ProcessedUrls = new Set();
  document.addEventListener("copy", function() {
    if (!enabled || !isTargetGroup()) return;

    // navigator.clipboard.readText exige foco — usamos setTimeout para pegar o valor
    setTimeout(function() {
      if (!navigator.clipboard || !navigator.clipboard.readText) return;
      navigator.clipboard.readText().then(function(txt) {
        if (!txt) return;
        URL_RE.lastIndex = 0;
        var m = URL_RE.exec(txt.trim());
        if (!m) return;
        var url = m[0].replace(/[.,;!?)]+$/, "");
        if (!isValidUrl(url)) return;
        if (!isGigroupUrl(url) && !isFormUrl(url)) return;
        if (fb7ProcessedUrls.has(url)) return;
        fb7ProcessedUrls.add(url);
        LOG("🔗 [FB7] URL gigroup detectada no clipboard:", url);
        openLink(url, "[clipboard] " + url, "FB7-clipboard");
      }).catch(function() {});
    }, 100);
  }, true);

  // ── FALLBACK 8 — Mensagens encaminhadas (forwarded) ───────────────────────
  // WhatsApp marca mensagens encaminhadas com ícone/label específico.
  // O data-id dessas mensagens pode ter qualquer prefixo.
  // Seletores: [data-testid="forwarded"], span com texto "Encaminhada", ._amk6
  var fb8ProcessedIds = new Set();
  var FB8_FORWARD_SELS = [
    "[data-testid='forwarded-label']",
    "span[data-icon='forwarded']",
    "._amk6",                         // classe interna de "Encaminhada"
    "[class*='forward']",
    "i[data-testid='forwarded']"
  ];

  function tryHandleFallback8(el) {
    if (!isTargetGroup()) return;
    if (!el.querySelectorAll) return;

    // Verifica se este container tem indicador de encaminhamento
    var isForwarded = false;
    for (var s = 0; s < FB8_FORWARD_SELS.length; s++) {
      try {
        if (el.querySelector(FB8_FORWARD_SELS[s])) { isForwarded = true; break; }
      } catch(e) {}
    }

    // Também verifica se o próprio el tem o atributo/classe de forward
    if (!isForwarded) {
      var cls = (el.className || "");
      if (cls.indexOf("forward") !== -1) isForwarded = true;
    }

    if (!isForwarded) return;

    var dataId = el.getAttribute("data-id");
    if (dataId) {
      if (processedIds.has(dataId) || fb3ProcessedIds.has(dataId) || fb8ProcessedIds.has(dataId)) return;
    }

    var url = getFirstUrl(el, getText(el));
    if (!url || !isValidUrl(url)) return;

    var key = dataId || url;
    if (fb8ProcessedIds.has(key)) return;
    fb8ProcessedIds.add(key);
    if (dataId) processedIds.add(dataId);

    var text = getText(el);
    LOG("🔗 [FB8] mensagem encaminhada com link:", url);
    openLink(url, text, "FB8-forwarded");
  }

  // ── FALLBACK 9 — URL gigroup em texto puro sem <a> ────────────────────────
  // Às vezes o WhatsApp não renderiza um hyperlink clicável para certos domínios.
  // Faz scan agressivo por regex em spans de texto visíveis.
  var fb9ProcessedUrls = new Set();
  var FB9_GIGROUP_RE = /https?:\/\/[^\s"'<>)]+(?:gigroup|eventuais)[^\s"'<>)]+/gi;

  function tryHandleFallback9(el) {
    if (!isTargetGroup()) return;
    if (!el.querySelectorAll) return;

    // Coleta todos os spans/divs de texto da mensagem
    var textNodes = el.querySelectorAll(
      "span.copyable-text, .selectable-text span, span[dir='ltr'], span[dir='auto'], " +
      "[data-testid='msg-text']"
    );

    for (var i = 0; i < textNodes.length; i++) {
      var raw = textNodes[i].textContent || "";
      if (!raw) continue;

      FB9_GIGROUP_RE.lastIndex = 0;
      var match;
      while ((match = FB9_GIGROUP_RE.exec(raw)) !== null) {
        var url = match[0].replace(/[.,;!?)\]>]+$/, "");
        if (!isValidUrl(url)) continue;
        if (fb9ProcessedUrls.has(url)) continue;

        // Evita re-processar URLs já pegas por outras mecânicas
        if (fb4ProcessedHrefs.has(url) || fb5ProcessedHrefs.has(url) ||
            fb6ProcessedHrefs.has(url) || fb7ProcessedUrls.has(url)) continue;

        fb9ProcessedUrls.add(url);
        var container = el.closest ? (el.closest("[data-id]") || el) : el;
        var text = getText(container) || raw;
        LOG("🔗 [FB9] URL gigroup em texto puro:", url);
        openLink(url, text, "FB9-plaintext");
      }
    }
  }

  // ── FALLBACK 10 — Interceptação de fetch/XHR da API do WhatsApp Web ────────
  // WhatsApp Web realiza chamadas internas para carregar histórico de mensagens.
  // Esta mecânica intercepta as respostas e extrai URLs antes do DOM renderizar,
  // garantindo captura even se o MutationObserver perder a mutação.
  // ATENÇÃO: só intercepta respostas que contenham "gigroup" ou "eventuais"
  //           para minimizar overhead.
  var fb10ProcessedUrls = new Set();

  (function installFB10Interceptors() {
    // ── Intercept fetch ────────────────────────────────────────────────────
    var originalFetch = window.fetch;
    window.fetch = function() {
      var args = arguments;
      return originalFetch.apply(this, args).then(function(response) {
        if (!enabled || !isTargetGroup()) return response;
        // Clone para não consumir o body original
        var clone = response.clone();
        clone.text().then(function(body) {
          fb10ScanPayload(body);
        }).catch(function() {});
        return response;
      });
    };

    // ── Intercept XMLHttpRequest ───────────────────────────────────────────
    var XHROpen = XMLHttpRequest.prototype.open;
    var XHRSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function() {
      this._gg_url = arguments[1] || "";
      return XHROpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function() {
      var xhr = this;
      var originalOnLoad = xhr.onload;
      xhr.addEventListener("load", function() {
        if (!enabled || !isTargetGroup()) return;
        try {
          fb10ScanPayload(xhr.responseText || "");
        } catch(e) {}
      });
      return XHRSend.apply(xhr, arguments);
    };

    LOG("🔌 [FB10] interceptores fetch/XHR instalados.");
  })();

  function fb10ScanPayload(body) {
    if (!body) return;
    // Filtra rápido: só processa se tiver keyword gigroup/eventuais
    if (body.indexOf("gigroup") === -1 && body.indexOf("eventuais") === -1) return;

    URL_RE.lastIndex = 0;
    var match;
    while ((match = URL_RE.exec(body)) !== null) {
      var url = match[0].replace(/[\\",;!?)\]]+$/, "");
      if (!isValidUrl(url)) continue;
      if (!isGigroupUrl(url) && !isFormUrl(url)) continue;
      if (fb10ProcessedUrls.has(url)) continue;

      // Verifica contra todas as outras mecânicas para evitar duplicação
      if (fb4ProcessedHrefs.has(url) || fb5ProcessedHrefs.has(url) ||
          fb6ProcessedHrefs.has(url) || fb7ProcessedUrls.has(url)  ||
          fb9ProcessedUrls.has(url)) continue;

      fb10ProcessedUrls.add(url);
      LOG("🔗 [FB10] URL gigroup interceptada via fetch/XHR:", url);
      openLink(url, "[payload-intercept] " + url, "FB10-xhr");
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // HELPERS COMPARTILHADOS
  // ════════════════════════════════════════════════════════════════════════════

  function isGigroupUrl(u) {
    try { return new URL(u).hostname.toLowerCase().indexOf("gigroup") !== -1; }
    catch (e) { return false; }
  }

  function isFormUrl(u) {
    try {
      var h = new URL(u).hostname.toLowerCase();
      return h.indexOf("eventuais") !== -1 || h.indexOf("oportunidade") !== -1;
    } catch (e) { return false; }
  }

  function openLink(url, text, source) {
    var now = Date.now();
    if (now - lastActionAt < SPAM_GAP_MS) { LOG("anti-spam [" + source + "], ignorado."); return; }
    lastActionAt = now;

    chrome.runtime.sendMessage({
      type: "OPEN_LINK", url: url,
      groupName: TARGET_GROUP, messageSnippet: ("[" + source + "] " + text).slice(0, 80)
    }, function (res) {
      if (chrome.runtime.lastError) { LOG("BG error:", chrome.runtime.lastError.message); return; }
      if (res && res.success) LOG("✅ [" + source + "] aba aberta. hoje:", res.todayCount);
      else if (res)           LOG("⚠️ [" + source + "] não aberto:", res.reason);
    });
  }

  function isTargetGroup() {
    var sels = [
      "[data-testid='conversation-info-header-chat-title'] span",
      "#main header span[dir='auto']",
      "header span[title]",
      "header ._amig span"
    ];
    for (var i = 0; i < sels.length; i++) {
      var el = document.querySelector(sels[i]);
      if (!el) continue;
      var n = (el.getAttribute("title") || el.textContent || "").trim();
      if (n.toLowerCase() === TARGET_GROUP.toLowerCase()) return true;
    }
    return false;
  }

  function isMsgContainer(el) { return el.hasAttribute && el.hasAttribute("data-id"); }

  function getText(el) {
    var c = el.querySelector("span.copyable-text")
         || el.querySelector(".selectable-text span")
         || el.querySelector("span[dir]");
    return (c || el).textContent.trim() || "";
  }

  function getFirstUrl(el, text) {
    var a = el.querySelector("a[href^='http']");
    if (a && isValidUrl(a.href)) return a.href;
    URL_RE.lastIndex = 0;
    var m = URL_RE.exec(text);
    if (m) { var u = m[0].replace(/[.,;!?)]+$/, ""); if (isValidUrl(u)) return u; }
    return null;
  }

  function isValidUrl(u) {
    try { var x = new URL(u); return x.protocol === "http:" || x.protocol === "https:"; }
    catch (e) { return false; }
  }

})();
