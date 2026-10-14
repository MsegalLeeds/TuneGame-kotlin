/* Private rooms reuse TuneGame's screens and CSS; solo Kotlin gameplay stays unchanged. */
(() => {
  if (!location.pathname.startsWith('/rooms')) return;
  const style = document.createElement('style');
  style.textContent = '[hidden]{display:none!important}.lb-row{flex-wrap:wrap;gap:8px}';
  document.head.appendChild(style);
  const byId = id => document.getElementById(id);
  const text = (id, value) => { byId(id).textContent = value; };
  let room = null, busy = false, typed = false, questionKey = '', deadline = 0;
  let code = sessionStorage.getItem('tunegame-room') || '';
  const start = byId('screen-start');
  start.innerHTML = `<div class="start-card">
    <section class="setup-section setup-section-wide"><h1 style="font-size:22px;margin-bottom:12px">Play with friends</h1>
    <p class="playlist-status">Built-in music quiz · Same questions · Three lives each</p>
    <label class="field-label" for="room-name">Your name</label><input class="typed-input" id="room-name" maxlength="20" autocomplete="nickname" style="width:100%">
    <div style="display:flex;gap:12px;flex-wrap:wrap;margin-top:16px"><button class="submit-btn" id="room-create">Create private room</button></div>
    <label class="field-label" for="room-code" style="margin-top:20px">Room code from a friend</label>
    <div class="playlist-add-row"><input class="typed-input" id="room-code" maxlength="8" autocapitalize="characters" autocomplete="off" spellcheck="false"><button class="submit-btn" id="room-join">Join room</button></div></section>
    <section class="setup-section setup-section-wide"><h2 class="section-title">Answer mode</h2><div class="mode-toggle"><button class="mode-btn active" id="room-choice" aria-pressed="true">Multiple choice</button><button class="mode-btn" id="room-typed" aria-pressed="false">Type answers</button></div></section>
  </div>`;
  const status = document.createElement('p'); status.id = 'room-error'; status.setAttribute('role', 'alert'); status.style.cssText = 'color:var(--red);margin:16px 0';
  document.querySelector('.brand').after(status);
  const lobby = document.createElement('section'); lobby.id = 'room-lobby'; lobby.className = 'screen';
  lobby.innerHTML = `<div class="setup-section"><h1 style="font-size:24px">Private room <span id="room-label"></span></h1><p id="room-help" class="playlist-status" style="margin-top:12px"></p><div id="room-players"></div><button class="start-btn" id="room-start" style="margin-top:20px">Start game</button></div>`;
  start.after(lobby);
  const tools = document.createElement('div'); tools.hidden = true; tools.style.cssText='display:flex;align-items:center;justify-content:space-between;gap:12px;margin:16px 0';
  tools.innerHTML = '<span id="room-round"></span><button class="secondary-action" id="room-leave">Leave room</button>';
  status.after(tools);
  const board = document.createElement('section'); board.hidden = true; board.className = 'setup-section'; board.style.marginTop='20px';
  board.innerHTML = '<h2 class="section-title">Room scoreboard</h2><div id="room-board"></div>';
  document.querySelector('.app').appendChild(board);
  text('now-playing', 'Built-in quiz');
  byId('now-playing').style.display = 'none';
  byId('save-name').parentElement.hidden = true;
  byId('album-placeholder').setAttribute('aria-hidden', 'true');
  byId('room-choice').onclick = () => setMode(false);
  byId('room-typed').onclick = () => setMode(true);
  function setMode(value) {
    typed = value;
    ['room-choice','room-typed'].forEach((id,i)=>{ const active = Boolean(i)===typed; byId(id).classList.toggle('active',active);byId(id).setAttribute('aria-pressed',String(active)); });
  }
  function screen(id) { document.querySelectorAll('.screen').forEach(el=>el.classList.toggle('active',el.id===id)); }
  async function request(path, body) {
    const controller = new AbortController(); const timeout = setTimeout(()=>controller.abort(),8000);
    try {
      const response = await fetch('/api/rooms'+path,{method:body===undefined?'GET':'POST',credentials:'same-origin',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:controller.signal});
      const result = await response.json();
      if(!response.ok) { const error = new Error(result.error || 'Unable to update room.'); error.status=response.status; throw error; }
      return result;
    } finally { clearTimeout(timeout); }
  }
  function controlsDisabled(value) { document.querySelectorAll('#screen-start button, #room-start, #next-btn, #room-leave').forEach(b=>b.disabled=value); }
  async function action(path, body, leaving=false) {
    if(busy) return; busy=true; controlsDisabled(true); text('room-error','');
    try { const result = await request(path,body); if(leaving) reset(); else { code=result.code;sessionStorage.setItem('tunegame-room',code);render(result); } }
    catch(e) { text('room-error', e.name==='AbortError'?'Connection timed out. Try again.':e.message); }
    finally { busy=false;controlsDisabled(false); if(room) render(room,false); }
  }
  byId('room-create').onclick = () => action('',{name:byId('room-name').value});
  byId('room-join').onclick = () => {
    const entered = byId('room-code').value.trim().toUpperCase();
    if(!/^[A-Z2-9]{8}$/.test(entered)) {text('room-error','Enter the eight-character room code.');return;}
    action('/'+entered+'/join',{name:byId('room-name').value});
  };
  byId('room-start').onclick = () => action('/'+code+'/start',{});
  byId('next-btn').onclick = () => action('/'+code+'/next',{round:room.round});
  byId('room-leave').onclick = () => action('/'+code+'/leave',{},true);
  byId('play-again-btn').onclick = () => action('/'+code+'/leave',{},true);
  text('play-again-btn','Back to rooms');
  function reset(){room=null;code='';questionKey='';sessionStorage.removeItem('tunegame-room');tools.hidden=true;board.hidden=true;screen('screen-start');}
  function players(container, list) {
    container.replaceChildren();
    [...list].sort((a,b)=>b.score-a.score).forEach(p=>{
      const row=document.createElement('div');row.className='lb-row';
      const name=document.createElement('span');name.className='lb-name';name.textContent=p.name+(p.id===room.you?' (you)':'')+(p.id===room.host?' · host':'');
      const points=document.createElement('span');points.className='lb-pts';points.textContent=p.score+' pts · '+p.lives+' lives'+(room.phase==='QUESTION'&&p.answered?' · answered':'');
      row.append(name,points);container.appendChild(row);
    });
  }
  function render(state, syncClock=true) {
    room=state; if(syncClock) deadline=performance.now()+state.remainingMs;
    const me=state.players.find(p=>p.id===state.you), host=state.you===state.host;
    tools.hidden=false; text('room-round','Room '+state.code+(state.round?' · Question '+state.round:''));
    text('room-label',state.code);players(byId('room-players'),state.players);players(byId('room-board'),state.players);
    if(state.phase==='WAITING') {
      screen('room-lobby');board.hidden=true;
      text('room-help',host?'Share this code with your friends. Start when everyone has joined.':'Waiting for the host to start.');
      byId('room-start').hidden=!host;byId('room-start').disabled=busy||state.players.length<2;return;
    }
    board.hidden=false;
    if(state.phase==='FINISHED') {
      screen('screen-gameover');text('final-score',me.score+'pts');text('final-sub','All players are out of lives.');players(byId('leaderboard-list'),state.players);board.hidden=true;return;
    }
    screen('screen-game');text('stat-score',me.score);text('stat-streak',me.streak);text('lives-dots',me.lives+' lives');
    text('question-text',state.question.text);
    const key=[state.round,state.phase,me.answered,me.lives].join(':');
    if(key!==questionKey){
      questionKey=key; const choices=byId('choices-wrap');choices.replaceChildren();
      if(state.phase==='QUESTION'&&!me.answered&&me.lives>0){
        if(typed){
          const form=document.createElement('form');form.className='typed-answer';
          const input=document.createElement('input');input.className='typed-input';input.maxLength=500;input.setAttribute('aria-label','Your answer');input.placeholder='Type your answer';
          const submit=document.createElement('button');submit.className='submit-btn';submit.textContent='Submit';submit.type='submit';
          form.append(input,submit);form.onsubmit=e=>{e.preventDefault();if(input.value.trim())answer(input.value);};choices.appendChild(form);
        }else state.question.choices.forEach((choice,i)=>{const b=document.createElement('button');b.className='choice-btn';b.textContent=String.fromCharCode(65+i)+' · '+choice;b.onclick=()=>answer(choice);choices.appendChild(b);});
        const skip=document.createElement('button');skip.className='secondary-action';skip.textContent='Skip question';skip.onclick=()=>answer('');choices.appendChild(skip);
      }
    }
    byId('choices-wrap').querySelectorAll('button,input').forEach(b=>b.disabled=busy);
    const results=state.phase==='RESULTS';byId('result-banner').className='result-banner'+(results||me.answered||me.lives===0?' show':'');
    text('result-title',results?'Round complete':me.lives===0?'Out of lives':'Answer submitted');
    text('result-sub',results?'Correct answer: '+state.correctAnswer:me.lives===0?'Watch the room scoreboard while your friends play.':'Waiting for the other players…');
    byId('next-btn').classList.toggle('show',results&&host);byId('next-btn').disabled=busy;
    if(results&&!host) text('result-sub','Correct answer: '+state.correctAnswer+' · Waiting for the host.');
    updateTimer();
  }
  async function answer(value) {
    if(busy) return;
    byId('choices-wrap').querySelectorAll('button,input').forEach(b=>b.disabled=true);
    await action('/'+code+'/answer',{round:room.round,answer:value});
  }
  function updateTimer(){const remaining=room?.phase==='QUESTION'?Math.max(0,deadline-performance.now()):0;text('timer-label',Math.ceil(remaining/1000)+'s');byId('timer-bar').style.width=(remaining/300)+'%';}
  async function refresh(){
    if(!code||busy)return;
    busy=true;
    try{render(await request('/'+code));text('room-error','');}
    catch(e){if(e.status===404)reset();text('room-error',e.status===404?e.message:'Connection interrupted. Retrying…');}
    finally{busy=false;if(room)render(room,false);}
  }
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
  setInterval(()=>{if(!document.hidden)refresh();},3000);setInterval(updateTimer,250);refresh();
})();
