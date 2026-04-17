// content-form.js — GiGroup AutoForm v3.2
// Preenche o formulário em eventuais.gigroup.com.br/oportunidade/*
// Aguarda o formulário carregar com loop robusto (até 2s)

(function () {
  'use strict';

  let cfg     = {};
  let jaRodou = false;

  // ── LOG VISÍVEL NA ABA DO FORMULÁRIO ─────────────────────────────────────
  function log(...a) {
    console.log('%c[GiGroup Form]', 'color:#25d366;font-weight:700', ...a);
  }

  // ── HELPERS ───────────────────────────────────────────────────────────────

  function delay(ms) {
    return new Promise(r => setTimeout(r, ms));
  }

  function qs(...sels) {
    for (const s of sels) {
      try { const el = document.querySelector(s); if (el) return el; } catch (_) {}
    }
    return null;
  }

  function setInput(el, valor) {
    if (!el) return;
    el.focus();
    el.click();
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    if (setter) setter.call(el, valor);
    else el.value = valor;
    el.dispatchEvent(new InputEvent('input',  { bubbles: true, data: String(valor) }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur',   { bubbles: true }));
  }

  function clicarRadio(el) {
    if (!el) return;
    el.focus();
    el.click();
    el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    el.dispatchEvent(new MouseEvent('mouseup',   { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fmtCPF(cpf = '') {
    const n = cpf.replace(/\D/g, '').slice(0, 11);
    if (n.length !== 11) return cpf;
    return n.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
  }

  function fmtData(d = '') {
    if (!d) return '';
    // Já está no formato correto
    if (/^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
    // DD/MM/YYYY → YYYY-MM-DD
    const m = d.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    if (m) return `${m[3]}-${m[2]}-${m[1]}`;
    // Tenta extrair dígitos e montar YYYY-MM-DD
    const nums = d.replace(/\D/g, '');
    if (nums.length === 8) {
      return `${nums.slice(4,8)}-${nums.slice(2,4)}-${nums.slice(0,2)}`;
    }
    return d;
  }

  function setDateInput(el, valor) {
    if (!el) return;
    // Para input[type="date"] o valor deve ser YYYY-MM-DD
    // Usa nativeSetter para garantir que frameworks SPA detectem a mudança
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    el.focus();
    if (nativeSetter) nativeSetter.call(el, valor);
    else el.value = valor;
    el.dispatchEvent(new Event('input',  { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur',   { bubbles: true }));
    log('setDateInput → valor final no campo:', el.value);
  }

  // ── OVERLAY ───────────────────────────────────────────────────────────────

  function criarOverlay() {
    document.getElementById('gg-overlay')?.remove();
    if (!document.getElementById('gg-ov-style')) {
      const s = document.createElement('style');
      s.id = 'gg-ov-style';
      s.textContent = `
        #gg-overlay{position:fixed;top:16px;right:16px;z-index:2147483647;font-family:-apple-system,sans-serif;animation:ggOvIn .35s cubic-bezier(.22,1,.36,1)}
        @keyframes ggOvIn{from{transform:translateX(110%);opacity:0}to{transform:translateX(0);opacity:1}}
        #gg-panel{background:#0b1f14;border:1.5px solid #25d366;border-radius:14px;padding:13px 17px 11px;min-width:260px;max-width:320px;box-shadow:0 8px 28px rgba(37,211,102,.3)}
        #gg-head{display:flex;align-items:center;gap:8px;color:#25d366;font-weight:700;font-size:13px;margin-bottom:8px}
        #gg-head span{font-size:20px}
        #gg-close{margin-left:auto;background:none;border:none;color:#4a7a5a;cursor:pointer;font-size:15px;padding:0}
        #gg-close:hover{color:#fff}
        #gg-msg{font-size:12px;line-height:1.65;color:#c8e8cc}
        #gg-log{margin-top:7px;max-height:120px;overflow-y:auto;font-size:11px;border-top:1px solid #1a3a24;padding-top:5px}
        .gg-ok{color:#25d366}.gg-warn{color:#f59e0b}.gg-err{color:#ef4444}
      `;
      document.head.appendChild(s);
    }
    const div = document.createElement('div');
    div.id = 'gg-overlay';
    div.innerHTML = `<div id="gg-panel"><div id="gg-head"><span>🤖</span> GiGroup AutoForm <button id="gg-close">✕</button></div><div id="gg-msg">⏳ Aguardando formulário...</div><div id="gg-log"></div></div>`;
    document.body.appendChild(div);
    document.getElementById('gg-close').onclick = () => div.remove();
  }

  function setMsg(html) {
    const el = document.getElementById('gg-msg');
    if (el) el.innerHTML = html;
  }

  function addLog(txt, cls = '') {
    const el = document.getElementById('gg-log');
    if (!el) return;
    const d = document.createElement('div');
    d.className = cls; d.textContent = txt;
    el.appendChild(d);
    el.scrollTop = el.scrollHeight;
  }

  // ── AGUARDA FORMULÁRIO CARREGAR ───────────────────────────────────────────
  // Loop robusto: tenta a cada 500ms por até 30 segundos

  async function aguardarFormulario() {
    const inicio = Date.now();
    const TIMEOUT = 4000;

    while (Date.now() - inicio < TIMEOUT) {
      const el = qs(
        '#cpf',
        'input[name="cpf"]',
        'input[id*="cpf"]',
        'input[placeholder*="CPF"]',
        '#birthdate',
        'input[name="birthdate"]'
      );

      if (el) {
        log('✅ Formulário encontrado!', el);
        return el;
      }

      const segs = Math.round((Date.now() - inicio) / 1000);
      setMsg(`⏳ Aguardando formulário... ${segs}s`);
      await delay(50);
    }

    return null; // timeout
  }

  // ── PREENCHIMENTO PRINCIPAL ───────────────────────────────────────────────

  async function preencher() {
    if (jaRodou) return;
    jaRodou = true;

    log('Iniciando preenchimento em:', window.location.href);
    criarOverlay();

    const cpf      = cfg.cpf            || '';
    const dataNasc = cfg.dataNascimento || '';

    // Valida config antes de esperar
    if (!cpf) {
      setMsg('⚠️ CPF não configurado.<br><small>Popup da extensão → aba Perfil → salve seu CPF.</small>');
      addLog('✗ CPF ausente nas configurações', 'gg-err');
      jaRodou = false;
      return;
    }

    // Aguarda formulário aparecer (até 30s)
    const elCPF = await aguardarFormulario();

    if (!elCPF) {
      setMsg('⚠️ Formulário não carregou em 30s.');
      addLog('✗ Timeout aguardando formulário', 'gg-err');
      jaRodou = false;
      return;
    }

    setMsg('📋 Preenchendo...');
    await delay(120); // estabiliza DOM após detectar

    // ── CPF ───────────────────────────────────────────────────────────────────
    const campoCPF = qs('#cpf','input[name="cpf"]','input[id*="cpf"]',
                        'input[placeholder*="CPF"]','input[maxlength="14"]');
    if (campoCPF) {
      setInput(campoCPF, fmtCPF(cpf));
      addLog('✅ CPF', 'gg-ok');
      log('CPF preenchido:', fmtCPF(cpf));
      await delay(350);
    }

    // ── DATA DE NASCIMENTO ────────────────────────────────────────────────────
    const campoData = qs('#birthdate','input[name="birthdate"]','input[id*="birthdate"]',
                         'input[name*="nascimento"]','input[type="date"]');
    if (campoData && dataNasc) {
      const dataFormatada = fmtData(dataNasc);
      setDateInput(campoData, dataFormatada);
      addLog('✅ Data de nascimento: ' + dataFormatada, 'gg-ok');
      log('Data preenchida:', dataFormatada, '| campo valor:', campoData.value);
      await delay(350);
      addLog('⚠️ Data não configurada no perfil', 'gg-warn');
    }

    // ── EPI = Sim ─────────────────────────────────────────────────────────────
    const campoEPI = qs(
      "input[name='hasEPI'][value='true']",
      "input[name='epi'][value='true']",
      "input[name*='EPI'][value='true']"
    ) || [...document.querySelectorAll('input[type="radio"]')].find(r => {
      const lbl = document.querySelector(`label[for="${r.id}"]`);
      const cnt = r.closest('[class*="epi"],[id*="epi"]') || r.parentElement?.parentElement;
      const txt = ((lbl?.textContent || '') + (cnt?.textContent || '')).toLowerCase();
      return txt.includes('epi') &&
             (r.value === 'true' || r.value === 'sim' || (lbl?.textContent||'').toLowerCase().includes('sim'));
    });

    if (campoEPI) {
      clicarRadio(campoEPI);
      addLog('✅ EPI → Sim', 'gg-ok');
      await delay(350);
    } else {
      addLog('— EPI não encontrado nesta etapa', '');
    }

    // ── BOTÃO CONTINUAR ───────────────────────────────────────────────────────
    await delay(300);

    const btn =
      qs("button.btn.block", "button[type='submit']", "button.btn-primary") ||
      [...document.querySelectorAll('button')].find(b =>
        /continuar|próximo|proximo|enviar|confirmar|avançar/i.test(b.textContent)
      );

    if (btn && !btn.disabled) {
      btn.click();
      addLog('✅ "Continuar" clicado', 'gg-ok');
      setMsg('✅ <strong>Preenchido!</strong> Revise e confirme.');
      log('Botão Continuar clicado');

      const vagaId = window.location.href.match(/\/oportunidade\/(\d+)/)?.[1];
      chrome.runtime.sendMessage({ type: 'FORM_PREENCHIDO', vagaId, url: window.location.href });

      monitorarProximaEtapa();
    } else {
      addLog('⚠️ Botão Continuar não encontrado', 'gg-warn');
      setMsg('⚠️ Campos preenchidos — clique em "Continuar" manualmente.');
      log('⚠️ Botão não encontrado');
      jaRodou = false;
    }
  }

  // ── MONITORA PRÓXIMA ETAPA SPA ────────────────────────────────────────────

  function monitorarProximaEtapa() {
    let urlAnt = location.href;
    let domAnt = document.body.innerHTML.length;

    const check = setInterval(async () => {
      const urlNow = location.href;
      const domNow = document.body.innerHTML.length;
      if (urlNow === urlAnt && Math.abs(domNow - domAnt) < 600) return;
      urlAnt = urlNow;
      domAnt = domNow;
      await delay(100);

      if (/sucesso|conclu[íi]do|obrigado|inscri[çc]/i.test(document.body.textContent) &&
          !qs('#cpf','input[name="cpf"]','button.btn.block')) {
        clearInterval(check);
        setMsg('🎉 <strong>Inscrição concluída com sucesso!</strong>');
        addLog('✅ Formulário enviado!', 'gg-ok');
        setTimeout(() => document.getElementById('gg-overlay')?.remove(), 8000);
        return;
      }

      if (qs('#cpf','input[name="cpf"]','input[type="date"]','button.btn.block')) {
        clearInterval(check);
        jaRodou = false;
        preencher();
      }
    }, 700);

    setTimeout(() => clearInterval(check), 5 * 60 * 1000);
  }

  // ── BOOTSTRAP ─────────────────────────────────────────────────────────────
  // Carrega config e inicia — com fallback se chrome.runtime falhar

  function iniciar() {
    try {
      chrome.runtime.sendMessage({ type: 'GET_CONFIG' }, res => {
        cfg = res?.config || {};
        log('Config carregada:', cfg);
        preencher();
      });
    } catch (e) {
      log('⚠️ chrome.runtime indisponível — tentando sem config');
      preencher();
    }
  }

  // Aguarda DOM estar pronto
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', iniciar);
  } else {
    iniciar();
  }

})();
