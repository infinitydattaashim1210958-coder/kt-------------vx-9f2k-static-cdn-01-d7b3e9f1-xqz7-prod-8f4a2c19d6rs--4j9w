/* Swadhyay agent — UI + Puter.js + Android bridge. Logic lives in core.js. */
(function () {
  'use strict';
  var C = window.AgentCore;
  var Bridge = window.SwadhyayAgent;            // injected by AgentActivity (addJavascriptInterface)
  var $ = function (id) { return document.getElementById(id); };

  // ── theme from native app (accent colours) ──
  (function theme() {
    var p = new URLSearchParams(location.search), r = document.documentElement.style;
    if (/^#[0-9a-f]{6}$/i.test(p.get('gold') || '')) r.setProperty('--gold', p.get('gold'));
    if (/^#[0-9a-f]{6}$/i.test(p.get('gold2') || '')) r.setProperty('--gold2', p.get('gold2'));
  })();

  // ── settings (per-device, localStorage; every access guarded) ──
  var MODELS = {
    saver:    { plan: 'gpt-5.4-nano', answer: 'gpt-5.4-nano',  verify: 'gpt-5.4-nano' },
    balanced: { plan: 'gpt-5.4-nano', answer: 'claude-sonnet-5', verify: 'claude-sonnet-5' }
  };
  var settings = { mode: 'balanced', custom: '', verify: true };
  try { var s = JSON.parse(localStorage.getItem('agent.settings') || 'null'); if (s) settings = Object.assign(settings, s); } catch (e) {}
  function saveSettings() { try { localStorage.setItem('agent.settings', JSON.stringify(settings)); } catch (e) {} }
  function models() {
    if (settings.mode === 'custom' && settings.custom.trim()) { var m = settings.custom.trim(); return { plan: MODELS.saver.plan, answer: m, verify: m }; }
    return MODELS[settings.mode] || MODELS.balanced;
  }

  var history = [];      // [{q, a}] last turns, used only to resolve follow-ups
  var busy = false;

  // ── tiny DOM helpers (all model/DB text goes through textContent — never innerHTML) ──
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function scrollDown() { var l = $('log'); l.scrollTop = l.scrollHeight; }
  function addMsg(cls, text) { var d = el('div', 'msg ' + cls, text); $('log').appendChild(d); scrollDown(); return d; }
  function setBusy(b) { busy = b; $('bar').className = b ? 'on' : ''; $('send').disabled = b; }

  // ── Puter ──
  function loadPuter() {
    return new Promise(function (resolve) {
      if (window.puter) return resolve(true);
      var done = false, fin = function (v) { if (!done) { done = true; resolve(v); } };
      var sc = document.createElement('script'); sc.src = 'https://js.puter.com/v2/';
      sc.onload = function () { fin(!!window.puter); }; sc.onerror = function () { fin(false); };
      document.head.appendChild(sc); setTimeout(function () { fin(!!window.puter); }, 8000);
    });
  }
  function errMsg(e) {
    if (!e) return 'অজানা ত্রুটি';
    if (typeof e === 'string') return e;
    return e.message || (e.error && (e.error.message || e.error)) || e.code || JSON.stringify(e).slice(0, 200);
  }
  async function llm(messages, model) {
    var base = { model: model, normalize: true };
    var resp;
    try { resp = await puter.ai.chat(messages, Object.assign({ temperature: 0 }, base)); }
    catch (e1) { resp = await puter.ai.chat(messages, base); }   // some models reject sampling options
    return C.toText(resp);
  }

  // ── native retrieval ──
  function bridgeCall(fn, arg) {
    if (!Bridge) throw new Error('Android bridge পাওয়া যায়নি (অ্যাপের ভেতর থেকে খুলুন)');
    var raw = arg === undefined ? Bridge[fn]() : Bridge[fn](arg);
    var j = JSON.parse(raw);
    if (j && j.error) throw new Error(j.error);
    return j;
  }
  function tick() { return new Promise(function (r) { setTimeout(r, 30); }); }

  var MAX_PASSAGES = 14, MAX_CHARS = 20000;
  function trimForModel(passages) {
    var out = [], used = 0;
    for (var i = 0; i < passages.length && out.length < MAX_PASSAGES; i++) {
      var len = passages[i].text.length + passages[i].label.length + 20;
      if (used + len > MAX_CHARS && out.length >= 3) break;
      out.push(passages[i]); used += len;
    }
    return out;
  }

  // ── rendering ──
  function passageDetails(p, open) {
    var d = el('details'); if (open) d.open = true;
    d.id = 'src-' + p.id;
    d.appendChild(el('summary', null, p.label));
    d.appendChild(el('div', 'src', p.text));
    return d;
  }
  function renderResult(r) {
    var box = el('div', 'msg bot');
    var cls = { ok: 'ok', part: 'part', no: 'no', off: 'off' }[r.kind];
    box.appendChild(el('div', 'badge ' + cls, r.badge));
    var order = [];                                   // citation numbering by first appearance
    r.claims.forEach(function (c) {
      var p = el('p', 'claim', c.text + ' ');
      c.cites.forEach(function (id) {
        var n = order.indexOf(id); if (n === -1) { order.push(id); n = order.length - 1; }
        var sup = el('sup'), a = el('a', null, '[' + (n + 1) + ']');
        a.href = '#'; a.onclick = function (ev) { ev.preventDefault(); var t = $('src-' + id); if (t) { t.open = true; t.scrollIntoView({ block: 'center' }); } };
        sup.appendChild(a); p.appendChild(sup);
      });
      box.appendChild(p);
    });
    if (r.missing) box.appendChild(el('p', 'note warn', 'ডাটাবেসে যা নেই: ' + r.missing));
    (r.notes || []).forEach(function (n) { box.appendChild(el('p', 'note', n)); });
    if (order.length) {
      box.appendChild(el('div', 'group-title', '📚 সূত্র (ডাটাবেসের মূল পাঠ)'));
      order.forEach(function (id, i) { var p = r.byId[id]; if (p) { var d = passageDetails(p, false); d.firstChild.textContent = '[' + (i + 1) + '] ' + p.label; box.appendChild(d); } });
    }
    var rest = r.passages.filter(function (p) { return order.indexOf(p.id) === -1; });
    if (rest.length) {
      box.appendChild(el('div', 'group-title', 'আরও প্রাসঙ্গিক অংশ (' + rest.length + ')'));
      rest.slice(0, 12).forEach(function (p) { box.appendChild(passageDetails(p, false)); });
    }
    $('log').appendChild(box); scrollDown();
  }

  // ── the agent ──
  async function ask(question) {
    var status = addMsg('status', '…');
    var say = function (t) { status.textContent = t; scrollDown(); };
    var notes = [];
    try {
      // 0. AI availability. signIn() opens a popup, so it must start from the tap that called ask().
      var aiReady = navigator.onLine !== false && await loadPuter();
      if (aiReady) {
        try { if (!(await Promise.resolve(puter.auth.isSignedIn()))) { say('Puter-এ সাইন-ইন করুন…'); await puter.auth.signIn(); } }
        catch (e) { aiReady = false; notes.push('Puter সাইন-ইন হয়নি (' + errMsg(e) + ') — AI ছাড়া শুধু স্থানীয় অনুসন্ধান দেখানো হলো।'); }
      } else notes.push('ইন্টারনেট/Puter পাওয়া যায়নি — AI ছাড়া শুধু স্থানীয় অনুসন্ধান।');

      await tick();
      var cov = bridgeCall('coverage');
      var M = models();

      // 1. plan search terms (Bangla question -> Sanskrit/Bengali keywords)
      var terms = [];
      if (aiReady) {
        say('প্রশ্ন বিশ্লেষণ হচ্ছে…');
        var plan = C.extractJson(await llm(C.buildPlanMessages(question, cov, history), M.plan));
        if (plan && plan.needs_database === false) {
          status.remove();
          renderResult({ kind: 'no', badge: 'ডাটাবেসে খোঁজার মতো প্রশ্ন নয়', claims: [{ text: 'আমি শুধু অ্যাপে থাকা শাস্ত্র-ডাটাবেস (বেদ, রামায়ণ, মহাভারত, গীতা, লাইব্রেরি) থেকে উত্তর দিই। শাস্ত্র নিয়ে প্রশ্ন করুন।', cites: [] }], passages: [], byId: {}, notes: notes });
          return;
        }
        if (plan && Array.isArray(plan.terms)) terms = plan.terms.filter(function (t) { return typeof t === 'string'; }).slice(0, 8);
      }
      C.fallbackTerms(question).slice(0, 4).forEach(function (t) { if (terms.indexOf(t) === -1) terms.push(t); });
      var gitaRefs = C.parseGitaRefs(question);

      // 2. retrieve from the on-device databases (+ one expansion round if thin)
      say('ডাটাবেসে খোঁজা হচ্ছে…'); await tick();
      var res = bridgeCall('search', JSON.stringify({ terms: terms, gita_refs: gitaRefs }));
      var passages = res.passages || [];
      var warnings = res.warnings || [];
      if (aiReady && passages.length < 3 && !gitaRefs.length) {
        say('আরও শব্দে খোঁজা হচ্ছে…');
        var more = C.extractJson(await llm(C.buildPlanMessages(
          question + '\n\n(Previous terms returned too few results: ' + terms.join(', ') + '. Give 8 DIFFERENT terms: root forms, alternate spellings, synonyms, in Devanagari and Bengali.)', cov, history), M.plan));
        var t2 = more && Array.isArray(more.terms) ? more.terms.filter(function (t) { return typeof t === 'string' && terms.indexOf(t) === -1; }).slice(0, 8) : [];
        if (t2.length) {
          await tick();
          var res2 = bridgeCall('search', JSON.stringify({ terms: t2, gita_refs: [] }));
          var seen = {}; passages.forEach(function (p) { seen[p.id] = 1; });
          (res2.passages || []).forEach(function (p) { if (!seen[p.id]) passages.push(p); });
          warnings = warnings.concat(res2.warnings || []);
        }
      }
      warnings.forEach(function (w) { notes.push('⚠ ' + w); });

      if (!passages.length) {
        status.remove();
        renderResult({ kind: 'no', badge: 'ডাটাবেসে উত্তর পাওয়া যায়নি', claims: [], missing: 'এই প্রশ্নের সাথে মেলে এমন কোনো অংশ ইনস্টল করা শাস্ত্রে মেলেনি। লাইব্রেরি থেকে আরও গ্রন্থ/ভাষ্য ডাউনলোড করে আবার চেষ্টা করুন।', passages: [], byId: {}, notes: notes });
        return;
      }
      var forModel = trimForModel(passages);
      var byId = {}; passages.forEach(function (p) { byId[p.id] = p; });

      if (!aiReady) {
        status.remove();
        renderResult({ kind: 'off', badge: '📴 AI ছাড়া — স্থানীয় অনুসন্ধান', claims: [], passages: passages, byId: byId, notes: notes });
        return;
      }

      // 3. answer strictly from passages
      say('উত্তর তৈরি হচ্ছে…');
      var msgs = C.buildAnswerMessages(question, forModel, cov, history);
      var parsed = C.extractJson(await llm(msgs, M.answer));
      if (!parsed) {
        msgs = msgs.concat([{ role: 'user', content: 'Return ONLY the JSON object described in the rules, nothing else.' }]);
        parsed = C.extractJson(await llm(msgs, M.answer));
      }
      if (!parsed) throw new Error('AI-এর উত্তর JSON আকারে আসেনি');

      // 4. verify: citations, verbatim quotes, numbers (code) ...
      say('যাচাই হচ্ছে…');
      var forModelById = {}; forModel.forEach(function (p) { forModelById[p.id] = p; });
      var v = C.verifyClaims(parsed, forModelById, question);
      var claims = v.verified, droppedN = v.dropped.length, checked = false;

      // ... and an independent model fact-check of every remaining claim
      if (settings.verify && claims.length) {
        try {
          var verdict = C.extractJson(await llm(C.buildVerifyMessages(claims, forModelById), M.verify));
          var a = C.applyModelVerdicts(claims, verdict);
          if (a.checked) { claims = a.kept; droppedN += a.dropped.length; checked = true; }
          else notes.push('দ্বিতীয় AI-যাচাই সম্পন্ন হয়নি; শুধু কোড-যাচাই হয়েছে।');
        } catch (e) { notes.push('দ্বিতীয় AI-যাচাই ব্যর্থ (' + errMsg(e) + '); শুধু কোড-যাচাই হয়েছে।'); }
      }
      if (droppedN) notes.push(droppedN + 'টি বাক্য যাচাই উত্তীর্ণ না হওয়ায় বাদ দেওয়া হয়েছে।');
      notes.push('প্রতিটি বাক্য ডাটাবেসের মূল পাঠের সাথে মিলিয়ে দেখা হয়েছে। গুরুত্বপূর্ণ সিদ্ধান্তের আগে নিচের মূল পাঠ পড়ুন।');

      status.remove();
      var missing = typeof parsed.missing === 'string' ? parsed.missing.trim() : '';
      if (!claims.length) {
        renderResult({ kind: 'no', badge: 'যাচাইযোগ্য উত্তর পাওয়া যায়নি', claims: [], missing: missing || 'পাওয়া অংশগুলো প্রশ্নের সরাসরি উত্তর দেয় না।', passages: passages, byId: byId, notes: notes });
        return;
      }
      var full = parsed.status === 'answered' && !droppedN && !missing;
      renderResult({
        kind: full ? 'ok' : 'part',
        badge: full ? (checked ? '✓ যাচাইকৃত উত্তর (কোড + AI-যাচাই)' : '✓ ডাটাবেস-ভিত্তিক উত্তর (কোড-যাচাই)') : '⚠ আংশিক উত্তর',
        claims: claims, missing: missing, passages: passages, byId: byId, notes: notes
      });
      history.push({ q: question, a: claims.map(function (c) { return c.text; }).join(' ') });
      if (history.length > 4) history.shift();
    } catch (e) {
      status.remove();
      var m = errMsg(e);
      addMsg('bot', '⚠ ত্রুটি: ' + m + '\n\nPuter-এর মাসিক অ্যালাউন্স শেষ হলে Puter অ্যাকাউন্টে টপ-আপ করতে হতে পারে।');
    }
  }

  function send() {
    if (busy) return;
    var q = $('q').value.trim(); if (!q) return;
    $('q').value = ''; $('q').style.height = 'auto';
    addMsg('user', q); setBusy(true);
    ask(q).then(function () { setBusy(false); }, function () { setBusy(false); });
  }

  // ── wiring ──
  $('send').onclick = send;
  $('q').addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });
  $('q').addEventListener('input', function () { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 120) + 'px'; });
  $('gear').onclick = function () { $('panel').classList.toggle('open'); };
  $('clear').onclick = function () { history = []; $('log').textContent = ''; greet(); };
  $('signout').onclick = function () { try { if (window.puter) puter.auth.signOut(); } catch (e) {} addMsg('status', 'সাইন-আউট হয়েছে।'); };
  document.querySelectorAll('input[name=mode]').forEach(function (r) {
    r.checked = r.value === settings.mode;
    r.onchange = function () { settings.mode = r.value; saveSettings(); };
  });
  $('custom').value = settings.custom; $('custom').oninput = function () { settings.custom = this.value; saveSettings(); };
  $('verify').checked = !!settings.verify; $('verify').onchange = function () { settings.verify = this.checked; saveSettings(); };

  function greet() {
    addMsg('bot', 'নমস্কার 🙏 আমি স্বাধ্যায় শাস্ত্র-সহায়ক।\n\nআমি শুধু অ্যাপের ডাটাবেসে থাকা পাঠ থেকে উত্তর দিই, প্রতিটি বাক্যের সূত্র সহ। ডাটাবেসে না থাকলে স্পষ্ট বলি যে নেই — অনুমান করি না।\n\nবাংলা, সংস্কৃত বা ইংরেজিতে প্রশ্ন করুন।');
  }
  greet();
})();
