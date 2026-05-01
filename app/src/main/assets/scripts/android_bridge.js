/**
 * android_bridge.js — GIGROUP Monitor v3.0
 * Substitui chrome.runtime + chrome.storage para Android WebView.
 * Detectores N1-N6 ativos no WhatsApp Web.
 * Fluxo: detectar → salvar estado → abrir FormActivity → voltar ao WhatsApp.
 */
(function(){
'use strict';
if(window.__GIGROUP_BRIDGE_V3__)return;
window.__GIGROUP_BRIDGE_V3__=true;
const LOG=(...a)=>console.log('%c[GIGROUP]','color:#25d366;font-weight:700',...a);

// Estado persistente
const state={
  returnUrl:sessionStorage.getItem('gg_ret')||null,
  pendingForm:sessionStorage.getItem('gg_pf')==='true',
};
function saveState(){
  if(state.returnUrl)sessionStorage.setItem('gg_ret',state.returnUrl);
  sessionStorage.setItem('gg_pf',String(state.pendingForm));
}
function clearState(){
  sessionStorage.removeItem('gg_ret');sessionStorage.removeItem('gg_pf');
  state.returnUrl=null;state.pendingForm=false;
}

// chrome.runtime shim
if(!window.chrome)window.chrome={};
if(!window.chrome.runtime)window.chrome.runtime={};
window.chrome.runtime.sendMessage=function(msg,cb){
  if(!msg||!msg.type)return;
  switch(msg.type){
    case'OPEN_LINK':
      state.returnUrl=window.location.href;state.pendingForm=true;saveState();
      if(window.AndroidBridge)window.AndroidBridge.postMessage(JSON.stringify(msg));
      if(typeof cb==='function')setTimeout(()=>cb({success:true}),0);
      break;
    case'FORM_PREENCHIDO':
      if(window.AndroidBridge)window.AndroidBridge.postMessage(JSON.stringify(msg));
      const ret=state.returnUrl||'https://web.whatsapp.com/';
      setTimeout(()=>{clearState();window.location.href=ret;},8000);
      if(typeof cb==='function')setTimeout(()=>cb({ok:true}),0);
      break;
    case'GET_STATE':
      try{const s=JSON.parse(window.AndroidBridge.getStateJson());if(typeof cb==='function')setTimeout(()=>cb(s),0);}
      catch(e){if(typeof cb==='function')setTimeout(()=>cb({}),0);}
      break;
    case'GET_CONFIG':
      try{const c=JSON.parse(window.AndroidBridge.getConfigJson());if(typeof cb==='function')setTimeout(()=>cb(c),0);}
      catch(e){if(typeof cb==='function')setTimeout(()=>cb({}),0);}
      break;
    default:
      if(window.AndroidBridge)window.AndroidBridge.postMessage(JSON.stringify(msg));
      if(typeof cb==='function')setTimeout(()=>cb({ok:true}),0);
  }
};
Object.defineProperty(window.chrome.runtime,'lastError',{get:()=>null,configurable:true});
if(!window.chrome.runtime.onMessage)window.chrome.runtime.onMessage={
  _l:[],addListener(f){this._l.push(f);},removeListener(f){this._l=this._l.filter(x=>x!==f);},
  dispatch(m){this._l.forEach(f=>{try{f(m,null,()=>{});}catch(e){}});}
};
if(!window.chrome.storage)window.chrome.storage={};
window.chrome.storage.local={
  get(k,cb){try{const s=JSON.parse(window.AndroidBridge.getStateJson());if(typeof cb==='function')setTimeout(()=>cb(s),0);}catch(e){if(typeof cb==='function')setTimeout(()=>cb({}),0);}},
  set(o,cb){if(window.AndroidBridge)window.AndroidBridge.postMessage(JSON.stringify({type:'STORAGE_SET',data:o}));if(typeof cb==='function')setTimeout(cb,0);}
};

// Helpers de detecção — só no WhatsApp Web
if(!location.hostname.includes('whatsapp.com')){LOG('Bridge OK (sem detectores)');return;}

const TARGET=['gigroup','shopee','juazeiro','simulação','simulacao'];
const isTarget=t=>t&&TARGET.some(k=>t.toLowerCase().includes(k));
const extractUrl=t=>{if(!t)return null;const m=t.match(/https?:\/\/[^\s"'<>]+/);return m?m[0]:null;};
const isVagaUrl=u=>u&&(u.includes('gigroup')||u.includes('eventuais'));
let opening=false;

function dispatch(url,group,snippet){
  LOG('🔗 Link:',url);
  window.chrome.runtime.sendMessage({type:'OPEN_LINK',url,groupName:group||'GIGROUP',messageSnippet:(snippet||'').slice(0,80)});
}

function openChatExtract(row,group){
  if(opening)return;opening=true;
  row.click();
  setTimeout(()=>{
    const msgs=document.querySelectorAll('[data-id],[data-testid="msg-container"]');
    Array.from(msgs).slice(-5).forEach(m=>{
      const t=m.textContent||'';const u=extractUrl(t);
      if(u&&isVagaUrl(u)){opening=false;dispatch(u,group,t);return;}
      m.querySelectorAll('a[href]').forEach(a=>{if(isVagaUrl(a.href)){opening=false;dispatch(a.href,group,t);}});
    });
    opening=false;
  },1500);
}

function checkUnread(){
  document.querySelectorAll('[role="row"],[role="listitem"]').forEach(row=>{
    const title=row.querySelector('span[title],._ao3e');
    const g=title?.title||title?.textContent||'';
    if(!isTarget(g))return;
    const badge=row.querySelector('[data-testid="icon-unread-count"]');
    if(badge&&parseInt(badge.textContent)>0)openChatExtract(row,g);
  });
}

// N1 — MutationObserver lista de chats
function N1(){
  const attach=()=>{
    const el=document.querySelector('[data-testid="chat-list"],#pane-side');
    if(!el)return false;
    new MutationObserver(muts=>{
      muts.forEach(m=>m.addedNodes.forEach(n=>{
        if(n.nodeType!==1)return;
        const t=n.querySelector('span[title],._ao3e');
        const g=t?.title||t?.textContent||'';
        if(!isTarget(g)&&!isTarget(n.textContent))return;
        const u=extractUrl(n.textContent);
        if(u&&isVagaUrl(u))dispatch(u,g,n.textContent);
      }));
    }).observe(el,{childList:true,subtree:true});
    LOG('N1 ✅');return true;
  };
  if(!attach()){let i=setInterval(()=>{if(attach()||--_t<0)clearInterval(i);},1000);let _t=30;}
}

// N2 — Badge não lidos
function N2(){
  new MutationObserver(()=>{
    document.querySelectorAll('[data-testid="icon-unread-count"]').forEach(b=>{
      if(parseInt(b.textContent)<=0)return;
      const row=b.closest('[role="row"],[role="listitem"]');if(!row)return;
      const t=row.querySelector('span[title],._ao3e');
      const g=t?.title||t?.textContent||'';
      if(isTarget(g))openChatExtract(row,g);
    });
  }).observe(document.body,{childList:true,subtree:true,characterData:true});
  LOG('N2 ✅');
}

// N3 — Polling mensagens abertas
let n3id='';
function N3(){
  setInterval(()=>{
    const hdr=document.querySelector('[data-testid="conversation-header"] span[title],#main header span[title]');
    const g=hdr?.title||hdr?.textContent||'';if(!isTarget(g))return;
    const msgs=document.querySelectorAll('[data-id],[data-testid="msg-container"]');if(!msgs.length)return;
    const last=msgs[msgs.length-1];
    const id=last.getAttribute('data-id')||String(last.innerHTML.length);
    if(id===n3id)return;n3id=id;
    const t=last.textContent||'';const u=extractUrl(t);
    if(u&&isVagaUrl(u))dispatch(u,g,t);
  },1500);LOG('N3 ✅');
}

// N4 — Título da página
let n4title=document.title,n4cnt=0;
function N4(){
  setInterval(()=>{
    const t=document.title;if(t===n4title)return;n4title=t;
    const m=t.match(/\((\d+)\)/);if(!m)return;
    const c=parseInt(m[1]);if(c<=n4cnt)return;n4cnt=c;
    LOG('N4: novas mensagens',c);checkUnread();
  },500);LOG('N4 ✅');
}

// N5 — visibilitychange
function N5(){
  document.addEventListener('visibilitychange',()=>{
    if(!document.hidden)setTimeout(checkUnread,800);
  });LOG('N5 ✅');
}

// N6 — Observer de classes
function N6(){
  const attach=()=>{
    const pane=document.querySelector('#pane-side,[data-testid="chat-list"]');if(!pane)return false;
    new MutationObserver(muts=>{
      muts.forEach(m=>{
        if(m.type!=='attributes')return;
        const el=m.target;
        if(!(el.className||'').includes('unread'))return;
        const t=el.querySelector('span[title],._ao3e');
        const g=t?.title||t?.textContent||'';
        if(isTarget(g))openChatExtract(el,g);
      });
    }).observe(pane,{attributes:true,subtree:true,attributeFilter:['class','style']});
    LOG('N6 ✅');return true;
  };
  if(!attach()){let i=setInterval(()=>{if(attach()||--_t<0)clearInterval(i);},1000);let _t=30;}
}

function startAll(){N1();N2();N3();N4();N5();N6();LOG('🚀 N1-N6 ativos');}

// Aguarda WhatsApp carregar
function waitWA(){
  if(document.querySelector('[data-testid="chat-list"],#pane-side')){startAll();}
  else{
    new MutationObserver((m,obs)=>{
      if(document.querySelector('[data-testid="chat-list"],#pane-side')){obs.disconnect();setTimeout(startAll,500);}
    }).observe(document.documentElement,{childList:true,subtree:true});
  }
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',waitWA);
else waitWA();

window.__GIGROUP_dispatchEnabled=function(e){window.chrome.runtime.onMessage.dispatch({type:'ENABLED_CHANGED',enabled:e});};
LOG('✅ Bridge v3 instalada');
})();
