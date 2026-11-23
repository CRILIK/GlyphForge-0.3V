/* GlyphForge Music Reactive: local presets, portable files, Web Audio preview. No cloud, no recording. */
(()=>{'use strict';
const $=id=>document.getElementById(id), adapter=window.GlyphMusicAdapter;
if(!adapter)return;
const state=adapter.getState(),clamp=(n,a,b)=>Math.max(a,Math.min(b,n)),MASK=Array.from({length:169},(_,i)=>{const x=i%13-6,y=Math.floor(i/13)-6;return x*x+y*y<=41;});
const MODES={bars:'Эквалайзер',ring:'Круговой удар',wave:'Волна',spark:'Искры',orbit:'Орбита',art:'Свой глиф'};
const ICONS={bars:'▥',ring:'◎',wave:'〰',spark:'✦',orbit:'◉',art:'▦'};
const TEMPLATES=[
 {id:'beat-bars',title:'Bass Bars',mode:'bars',description:'Столбцы реагируют на спектр',sensitivity:1.6,smoothing:.55,brightness:.95,tags:'эквалайзер бас частоты динамика'},
 {id:'beat-night',title:'Night Bass',mode:'bars',description:'Плавный эквалайзер на низкой яркости',sensitivity:2.2,smoothing:.82,brightness:.50,tags:'ночь плавный бас'},
 {id:'beat-pulse',title:'Bass Pulse',mode:'ring',description:'Кольцо увеличивается на удар',sensitivity:2.0,smoothing:.30,brightness:1,tags:'круг удар бас'},
 {id:'beat-ripple',title:'Ripple',mode:'ring',description:'Световое кольцо на пиках',sensitivity:3.0,smoothing:.48,brightness:.85,tags:'волны круг бас'},
 {id:'beat-wave',title:'Waveform',mode:'wave',description:'Волна колеблется с музыкой',sensitivity:2.1,smoothing:.38,brightness:1,tags:'волна частоты'},
 {id:'beat-spark',title:'Strobe Sparks',mode:'spark',description:'Вспышки на сильных ударах',sensitivity:2.6,smoothing:.15,brightness:.90,tags:'вспышки искры бит'},
 {id:'beat-orbit',title:'Orbit',mode:'orbit',description:'Вращение с усилением на басе',sensitivity:1.7,smoothing:.60,brightness:.85,tags:'вращение орбита бас'},
 {id:'beat-custom',title:'Мой рисунок · Beat',mode:'art',description:'Мой глиф пульсирует и меняет кадры в такт',sensitivity:1.8,smoothing:.52,brightness:1,tags:'мой рисунок анимация гиф'}
];
if(!Array.isArray(state.musicProfiles))state.musicProfiles=[];
const clean=p=>({
 id:String(p.id||('music-'+Date.now().toString(36)+Math.random().toString(36).slice(2,6))).slice(0,80),
 title:String(p.title||'Новый эффект').slice(0,80),
 mode:Object.hasOwn(MODES,p.mode)?p.mode:'bars',
 description:String(p.description||'Собственный музыкальный глиф').slice(0,220),
 designId:String(p.designId||'').slice(0,120),
 sensitivity:clamp(Number(p.sensitivity)||1.6,.5,4.5),
 smoothing:clamp(Number.isFinite(Number(p.smoothing))?Number(p.smoothing):.55,0,.94),
 brightness:clamp(Number(p.brightness)||.95,.1,1),
 tags:String(p.tags||'').slice(0,150)
});
let current=clean(TEMPLATES[0]),audioCtx=null,analyser=null,freq=null,timeSamples=null,playerSource=null,audioURL=null,animationHandle=null,lastDraw=0,demo=false,smoothed=Array(8).fill(0),avgBass=.05,lastBeat=0,artIndex=0;
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ms=()=>performance.now()/1000;
function getDesign(){return state.designs.find(d=>d.id===current.designId)||state.designs[0]||null}
function fillDesigns(){const sel=$('music-design'),old=current.designId||sel.value;sel.replaceChildren();for(const d of state.designs){const opt=document.createElement('option');opt.value=d.id;opt.textContent=d.title;sel.appendChild(opt)}if(state.designs.some(d=>d.id===old))sel.value=old;current.designId=sel.value||'';}
function readForm(){return clean({...current,title:$('music-title').value,mode:$('music-mode').value,designId:$('music-design').value,sensitivity:Number($('music-sens').value)/100,smoothing:Number($('music-smooth').value)/100,brightness:Number($('music-bright').value)/100})}
function formFrom(p){current=clean(p);fillDesigns();$('music-title').value=current.title;$('music-mode').value=current.mode;$('music-design').value=current.designId&&state.designs.some(d=>d.id===current.designId)?current.designId:$('music-design').value;
 $('music-sens').value=Math.round(current.sensitivity*100);$('music-smooth').value=Math.round(current.smoothing*100);$('music-bright').value=Math.round(current.brightness*100);labels();adapter.notify('Музыкальный эффект выбран: '+current.title)}
function labels(){for(const [a,b] of [['sens','music-sens-value'],['smooth','music-smooth-value'],['bright','music-bright-value']])$(b).textContent=$('music-'+a).value+'%';}
['sens','smooth','bright'].forEach(s=>{$('music-'+s).addEventListener('input',()=>{labels();current=readForm();});});
$('music-mode').onchange=()=>{current=readForm();};$('music-design').onchange=()=>{current=readForm();};
function showCatalog(){const query=$('music-search').value.trim().toLocaleLowerCase('ru'),category=$('music-category').value,items=[...(category!=='user'?TEMPLATES.map(p=>({...p,source:'builtin'})):[]),...(category!=='builtin'?state.musicProfiles.map(p=>({...p,source:'user'})):[])];
 const filtered=items.filter(p=>(p.title+' '+p.description+' '+p.tags+' '+MODES[p.mode]).toLocaleLowerCase('ru').includes(query));$('music-count').textContent=filtered.length+' эффектов';const host=$('music-presets');host.replaceChildren();
 if(!filtered.length){host.innerHTML='<div class="empty">Не найдено. Измени запрос или сохрани свой эффект.</div>';return;}
 for(const p of filtered){const c=document.createElement('div');c.className='design-card music-tile';c.innerHTML=`<div class="music-cover">${ICONS[p.mode]||'♪'}</div><strong>${escape(p.title)}</strong><small>${p.source==='user'?'Моя работа · ':'Шаблон · '}${escape(MODES[p.mode])}</small><span class="hint">${escape(p.description)}</span>`;
 const actions=document.createElement('div');actions.className='music-actions';const choose=document.createElement('button');choose.className='primary';choose.textContent='Выбрать';choose.onclick=()=>formFrom(p);actions.appendChild(choose);
 if(p.source==='user'){const remove=document.createElement('button');remove.className='outline';remove.textContent='Удалить';remove.onclick=()=>{if(!confirm('Удалить этот эффект из своей библиотеки?'))return;state.musicProfiles=state.musicProfiles.filter(o=>o.id!==p.id);adapter.persist();showCatalog()};actions.appendChild(remove);}
 c.appendChild(actions);host.appendChild(c);
 }
}
$('music-search').oninput=showCatalog;$('music-category').onchange=showCatalog;
$('music-save').onclick=()=>{let p=readForm();p.id='music-'+Date.now().toString(36)+Math.random().toString(36).slice(2,7);state.musicProfiles.push(p);adapter.persist();showCatalog();adapter.notify('Музыкальный глиф сохранён в библиотеку');};
$('music-export').onclick=()=>{const p=readForm();adapter.saveJson({kind:'glyphforge-music-profile',version:1,profile:p},p.title.replace(/[^a-zа-я0-9 -]/gi,'_')+'.gforgebeat');};
$('music-import').onclick=()=>$('music-import-file').click();
$('music-import-file').onchange=async e=>{const file=e.target.files?.[0];e.target.value='';if(!file)return;try{if(file.size>100000)throw new Error('Файл слишком большой');const obj=JSON.parse(await file.text());if(obj.kind!=='glyphforge-music-profile'||!obj.profile||typeof obj.profile!=='object')throw new Error('Это не профиль GlyphForge Music');const imported=clean(obj.profile);imported.id='music-'+Date.now().toString(36)+Math.random().toString(36).slice(2,7);state.musicProfiles.push(imported);adapter.persist();formFrom(imported);showCatalog();adapter.notify('Пресет импортирован');}catch(err){adapter.notify('Ошибка импорта пресета: '+err.message);}};
$('music-community-museum').onclick=()=>adapter.openCommunity('https://app.glyphmuseum.com/');
$('music-community-playground').onclick=()=>adapter.openCommunity('https://playground.nothing.tech/toys?device=phone_4a_pro');
$('music-open-library').onclick=()=>adapter.goto('library');
const canvas=$('music-preview'),g=canvas.getContext('2d');
function render(levels,beat,elapsed){const p=readForm(),v=clamp(levels.rms*p.sensitivity,0,1),bass=clamp((levels.bands[0]+levels.bands[1])*.55*p.sensitivity,0,1),out=Array(169).fill(0);
 const d=getDesign(),frames=d?.frames||[],art=frames.length?frames[Math.floor((elapsed*1.3+beat*4)%frames.length)].pixels:null;
 for(let y=0;y<13;y++)for(let x=0;x<13;x++){
 const i=y*13+x;if(!MASK[i])continue;
 const dx=x-6,dy=y-6,dist=Math.hypot(dx,dy),angle=Math.atan2(dy,dx);let value=0;
 if(p.mode==='bars'){const band=Math.min(7,Math.floor(x*8/13));const height=1+clamp(levels.bands[band]*p.sensitivity*2.6,0,1)*11;if(12-y<=height&&levels.bands[band]>.024)value=.28+levels.bands[band]*.85;}
 else if(p.mode==='ring'){const radius=2+v*4.3+beat*.8;value=Math.abs(dist-radius)<.8?.35+v*.5+beat*.5:0;}
 else if(p.mode==='wave'){const target=6+Math.sin(x*.9+elapsed*3)*(.6+levels.bands[2]*p.sensitivity*3);value=Math.abs(y-target)<.65?.25+v*.8:0;}
 else if(p.mode==='spark'){const n=Math.sin((x*9+y*13)*13.17+Math.floor(elapsed*9))*.5+.5;value=(bass+beat>.32&&n>.72)?clamp((bass+beat)*.85,0,1):0;}
 else if(p.mode==='orbit'){const theta=Math.abs(Math.atan2(Math.sin(angle-elapsed*2.7-v*4),Math.cos(angle-elapsed*2.7-v*4)));value=(Math.abs(dist-4.6)<1.05&&theta<.45)?(.3+v*.7+beat*.5):0;}
 else if(p.mode==='art'){value=(art?.[i]||0)/255*(.1+v*.9+beat*.3);}
 out[i]=Math.round(255*clamp(value*p.brightness,0,1));
 }
 adapter.draw(canvas,out);$('music-meter-fill').style.width=Math.round(v*100)+'%';
}
let prevTime=0;
async function initAudio(){if(!audioCtx){const C=window.AudioContext||window.webkitAudioContext;if(!C)throw Error('Web Audio API недоступен');audioCtx=new C();analyser=audioCtx.createAnalyser();analyser.fftSize=1024;analyser.smoothingTimeConstant=.24;playerSource=audioCtx.createMediaElementSource($('music-audio-player'));playerSource.connect(analyser);analyser.connect(audioCtx.destination);freq=new Uint8Array(analyser.frequencyBinCount);timeSamples=new Uint8Array(analyser.fftSize);}
 await audioCtx.resume();}
function animate(now){animationHandle=requestAnimationFrame(animate);if(now-lastDraw<60)return;lastDraw=now;
 const b=Array(8).fill(0);let energy=0, rms=0;const seconds=now/1000;
 if(!demo&&analyser){analyser.getByteFrequencyData(freq);analyser.getByteTimeDomainData(timeSamples);for(const x of timeSamples){const v=(x-128)/128;rms+=v*v;}rms=clamp(Math.sqrt(rms/timeSamples.length)*3,0,1);
  for(let i=1;i<freq.length;i++){const band=Math.min(7,Math.floor(Math.log1p(i)/Math.log1p(freq.length-1)*8));b[band]=Math.max(b[band],freq[i]/255);}
 }else if(demo){rms=clamp(.28+.32*Math.pow(Math.max(0,Math.sin(seconds*8)),4)+.16*Math.sin(seconds*3),0,1);
  for(let i=0;i<8;i++)b[i]=clamp(rms*(.6+.4*Math.sin(seconds*2+i*.85)),0,1);
 }else return;
 const p=readForm();for(let i=0;i<8;i++)smoothed[i]=smoothed[i]*p.smoothing+b[i]*(1-p.smoothing);
 const bass=(smoothed[0]+smoothed[1])*.5,delta=bass-avgBass;avgBass=.94*avgBass+.06*bass;lastBeat*=.72;if(delta>.065&&bass>.11)lastBeat=.95;
 const l={rms,bands:smoothed};render(l,lastBeat,seconds);
}
function stopPreview(){if(animationHandle!==null)cancelAnimationFrame(animationHandle);animationHandle=null;demo=false;$('music-audio-player').pause();$('music-preview-state').textContent='ОЖИДАНИЕ';$('music-meter-fill').style.width='0%';}
$('music-audio-file').onchange=e=>{const f=e.target.files?.[0];if(!f)return;stopPreview();if(audioURL)URL.revokeObjectURL(audioURL);audioURL=URL.createObjectURL(f);$('music-audio-player').src=audioURL;adapter.notify('Загружено: '+f.name+' — данные остаются на устройстве');};
$('music-audio-player').onplay=async()=>{if(animationHandle!==null)cancelAnimationFrame(animationHandle);animationHandle=null;demo=false;try{await initAudio();$('music-preview-state').textContent='АУДИО LIVE';animate(performance.now());}catch(e){adapter.notify('Аудиопредпросмотр: '+e.message)}};
$('music-audio-player').onpause=()=>{if(!demo){if(animationHandle!==null)cancelAnimationFrame(animationHandle);animationHandle=null;$('music-preview-state').textContent='ПАУЗА';}};
$('music-audio-player').onended=()=>stopPreview();
$('music-demo').onclick=()=>{stopPreview();demo=true;$('music-preview-state').textContent='ДЕМО · СИНТЕТИЧЕСКИЙ РИТМ';animate(performance.now());};
$('music-preview-stop').onclick=stopPreview;
$('music-native-start').onclick=()=>{if(!window.GlyphNative){adapter.notify('Запуск системной музыки доступен в Android-приложении');return;}stopPreview();try{window.GlyphNative.startMusic(JSON.stringify(readForm()),$('music-source').value);$('music-native-status').textContent='Запрос разрешений и запуск…';}catch(e){adapter.notify(e.message)}};
$('music-native-stop').onclick=()=>{if(window.GlyphNative){window.GlyphNative.stopMusic();$('music-native-status').textContent='Остановка…'}else adapter.notify('Android-приложение не подключено');};
function refresh(){fillDesigns();showCatalog();try{if(window.GlyphNative)$('music-native-status').textContent=window.GlyphNative.musicStatus();else $('music-native-status').textContent='Браузер: режим анализа системного звука доступен после установки APK';}catch(e){}}
window.GlyphMusicRefresh=refresh;
window.GlyphMusicUseDesign=designId=>{formFrom({id:'',title:'Музыкальный глиф · '+(state.designs.find(d=>d.id===designId)?.title||'свой рисунок'),mode:'art',designId,sensitivity:1.8,smoothing:.45,brightness:1});};
formFrom({...TEMPLATES[0],designId:state.designs[0]?.id||''});refresh();render({bands:Array(8).fill(0),rms:0},0,0);
window.addEventListener('beforeunload',()=>{if(audioURL)URL.revokeObjectURL(audioURL);if(audioCtx)audioCtx.close()});
window.GlyphForgeMusicTest={templates:TEMPLATES,readForm,render,modes:MODES};
})();
