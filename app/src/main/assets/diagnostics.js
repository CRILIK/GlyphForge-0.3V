/* Report v2: explicit PASS / FAIL / BLOCKED / UNTESTED; only synthetic data and counts. */
(()=>{'use strict';
const $=id=>document.getElementById(id), native=()=>window.GlyphNative;
let lastReport='',active=null,seq=0,completedFull=false;
const labels={pass:'✓ ПРОЙДЕНО',fail:'✕ ОШИБКА',blocked:'! ЗАБЛОКИРОВАНО',untested:'— НЕ ПРОВЕРЕНО',pending:'… ВЫПОЛНЯЕТСЯ'};
function row(name,status,detail){return {name,status,pass:status==='pass'?true:status==='fail'?false:null,detail:String(detail??'').slice(0,240)}}
const ok=(name,pass,detail)=>row(name,pass?'pass':'fail',detail);
function guarded(tests,name,fn){try{let result=fn();tests.push(ok(name,!!result,result?'Успешно':'Результат не совпадает с ожидаемым'))}catch(e){tests.push(row(name,'fail',e.name))}}
function snapshot(){
 const tests=[], state=window.GlyphDiagnosticsReadState?.(), format=window.GlyphFormat;
 const indices=Array.from({length:169},(_,i)=>i).filter(i=>{let x=i%13-6,y=Math.floor(i/13)-6;return x*x+y*y<=41});
 tests.push(ok('Матрица 13×13 / 137 LED',indices.length===137&&JSON.stringify(format?.IND13)===JSON.stringify(indices),indices.length+' активных точек; сверка порядка LED'));
 const designs=Array.isArray(state?.designs)?state.designs:[];
 tests.push(ok('Библиотека доступна',Array.isArray(state?.designs),designs.length+' проектов'));
 let frames=0,bad=0;for(const d of designs){if(!Array.isArray(d.frames)||!d.frames.length){bad++;continue}for(const f of d.frames){frames++;if(!Array.isArray(f.pixels)||f.pixels.length!==169||!f.pixels.every((x,i)=>Number.isFinite(x)&&x>=0&&x<=255&&(indices.includes(i)||x===0))||!Number.isFinite(f.duration)||f.duration<=0)bad++}}
 tests.push(row('Структура сохранённых кадров',bad?'fail':frames?'pass':'untested',frames+' кадров; некорректных '+bad));
 const px=Array(169).fill(0);px[84]=255;px[85]=128;
 const sample={id:'diagnostic',title:'Synthetic',frames:[{pixels:px,duration:90},{pixels:px.map(x=>x?64:0),duration:180}]};
 guarded(tests,'Glyph Museum JSON: полный цикл',()=>{const encoded=format.exportMuseum(sample),back=format.normalizeDesign(JSON.parse(JSON.stringify(encoded)));return encoded.v===4&&encoded.frames.every(f=>f.p.length===137)&&JSON.stringify(back.frames)===JSON.stringify(sample.frames)});
 guarded(tests,'PNG: кодирование и пиксели',()=>{let c=document.createElement('canvas');c.width=c.height=13;let g=c.getContext('2d');g.fillStyle='#fff';g.fillRect(6,6,1,1);return c.toDataURL('image/png').startsWith('data:image/png;base64,')&&g.getImageData(6,6,1,1).data[0]===255});
 guarded(tests,'Преобразование изображения 13×13',()=>{const c=document.createElement('canvas');c.width=c.height=13;let g=c.getContext('2d');g.fillStyle='#000';g.fillRect(0,0,13,13);g.fillStyle='#fff';g.fillRect(6,6,1,1);const p=format.rasterToPixels(c,{mode:'fit',contrast:1,bw:true});return p.length===169&&p[84]===255&&p.filter(Boolean).length===1});
 guarded(tests,'Экспорт и импорт JSON',()=>{const back=JSON.parse(JSON.stringify({kind:'glyphforge-library',version:1,designs:[sample],rules:[],scenes:[]}));return JSON.stringify(back.designs[0].frames)===JSON.stringify(sample.frames)});
 guarded(tests,'Локальные настройки',()=>{const k='glyphforge-selftest-'+Date.now();try{localStorage.setItem(k,'ok');return localStorage.getItem(k)==='ok'}finally{localStorage.removeItem(k)}});
 const rules=Array.isArray(state?.rules)?state.rules:[],scenes=Array.isArray(state?.scenes)?state.scenes:[];
 const known=new Set(designs.map(d=>'d:'+d.id).concat(scenes.map(s=>'s:'+s.id)));
 const broken=rules.filter(r=>!known.has(r.target)).length+scenes.reduce((n,s)=>n+(Array.isArray(s.steps)?s.steps.filter(t=>!known.has('d:'+t.assetId)).length:1),0);
 tests.push(row('Сохранённые правила и сценарии',broken?'fail':rules.length+scenes.length?'pass':'untested',rules.length+' правил; '+scenes.length+' сценариев; недействительных ссылок '+broken));
 tests.push(row('Импорт и экспорт GIF','untested','Нажми «Проверить всё» для реального цикла кодирования и декодирования'));
 tests.push(row('Импорт видео','untested','Нужен фактический медиафайл и декодер устройства'));
 const nav=[...document.querySelectorAll('.nav')],view={width:innerWidth,height:innerHeight,dpr:devicePixelRatio,scrollWidth:document.documentElement.scrollWidth};
 const visible=nav.filter(b=>{let r=b.getBoundingClientRect();return r.width>=32&&r.height>=32&&r.top>=-1&&r.left>=-1&&r.bottom<=innerHeight+1&&r.right<=innerWidth+1}).length;
 tests.push(row('Навигация и размер экрана',view.scrollWidth>view.width+2?'fail':visible===nav.length&&nav.length?'pass':'fail',view.width+'×'+view.height+' CSS px; видимых кнопок '+visible+'/'+nav.length+'; горизонтальный размер '+view.scrollWidth));
 let device={mode:'browser',hardwareUnverified:true};
 try{if(native()?.getDiagnostics){device=JSON.parse(native().getDiagnostics());tests.push(ok('Android Native API',!device.error,'Ответ получен'));tests.push(ok('Nothing SDK загружен',device.sdkInstalled,'Наличие классов, не подключение'));tests.push(ok('Сохранение в Downloads',device.downloadsWritable,'Запись, чтение и удаление проверочного файла'));tests.push(ok('Хранилище Android',device.savedLibraryValid,'Структура сохранённой библиотеки'));
 for(const [name,key] of [['Доступ к уведомлениям','notificationAccess'],['Разрешение на аудио','audioPermission'],['Состояние звонков','callPermission'],['Разрешение публиковать тестовое уведомление','postNotificationPermission']])tests.push(row(name,device[key]?'pass':'blocked',device[key]?'Разрешено':'Не предоставлено'));
 for(const t of device.checks||[])tests.push(row(t.name,t.status,t.detail));
 const saved=(device.journal||[]).filter(x=>x.code==='community_blob_saved').length;tests.push(row('Скачивание из сообщества: журнал',saved?'pass':'untested',saved?saved+' успешных сохранений blob в журнале':'Успешных сохранений blob ещё не зарегистрировано'));
 }else tests.push(row('Android и аппаратные функции','untested','Браузерный режим'))}catch(e){tests.push(row('Android Native API','fail',e.name))}
 tests.push(row('Физическое свечение Glyph Matrix','untested','SDK не подтверждает оптически работу светодиодов'));
 tests.push(row('Событие входящего звонка','untested','Без реального входящего вызова не проверяется'));
 tests.push(row('Захват музыки других приложений','untested','MediaProjection требует согласия Android и разрешённого источника'));
 return {app:'GlyphForge',appVersion:device.appVersion||'0.4.5',reportVersion:2,createdAt:new Date().toISOString(),tests,native:device,viewport:view,limitations:['PASS означает только конкретную выполненную проверку, не работоспособность всего модуля.','Физическое свечение, реальный звонок, фоновые события других приложений и загрузки с авторизацией требуют отдельной проверки.','Никакие сообщения, контакты, аудиозаписи, адреса страниц или содержимое работ в отчёт не включаются.']};
}
function show(r){const summary={total:r.tests.length,passed:0,failed:0,blocked:0,untested:0,pending:0};for(const t of r.tests){const key={pass:'passed',fail:'failed',blocked:'blocked',untested:'untested',pending:'pending'}[t.status];if(key)summary[key]++}r.summary=summary;r.fullRun=completedFull&&!summary.pending;lastReport=JSON.stringify(r,null,2);$('diag-notification-help').hidden=r.native?.mode!=='android'||r.native?.notificationAccess===true;for(const id of ['export-diagnostics','share-diagnostics'])$(id).disabled=!r.fullRun;$('diag-export-status').textContent=summary.pending?'Выполняется проверка. Сохранение станет доступно после завершения.':r.fullRun?'Полный отчёт готов. Можно сохранить или отправить.':'Для полного отчёта нажми «Проверить всё».';$('diagnostics-output').textContent='GlyphForge '+r.appVersion+' · отчёт v2\n'+r.createdAt+'\n\n'+r.tests.map(t=>labels[t.status]+' · '+t.name+' — '+t.detail).join('\n')+'\n\nПройдено: '+summary.passed+' | Ошибки: '+summary.failed+' | Заблокировано: '+summary.blocked+' | Не проверено: '+summary.untested+' | В процессе: '+summary.pending+'\n\n'+r.limitations.join('\n');$('diagnostics-state').textContent=summary.pending?'ВЫПОЛНЯЕТСЯ':summary.failed||summary.blocked?'ТРЕБУЕТ ВНИМАНИЯ':summary.untested?'ЧАСТИЧНО ПРОВЕРЕНО':'ГОТОВО';return r}
function replace(r,name,values){let i=r.tests.findIndex(t=>t.name===name);if(i>=0)r.tests.splice(i,1,...values);else r.tests.push(...values)}
function run(){if(active)return active.report;completedFull=false;return show(snapshot())}
function finish(group,values,token){if(!active||String(active.token)!==String(token)||!active.groups.has(group))return;replace(active.report,group,values);active.groups.delete(group);if(!active.groups.size){completedFull=true;clearTimeout(active.timer);const report=active.report;active=null;$('run-diagnostics').disabled=false;show(report)}else show(active.report)}
window.GlyphDiagnosticResult=(token,group,payload)=>{try{const results=JSON.parse(payload);finish(group,results.map(t=>row(t.name,t.status,t.detail)),token)}catch(e){finish(group,[row(group,'fail','Некорректный ответ Android')],token)}};
async function full(){
 if(active)return;
 completedFull=false;
 const report=snapshot(),token=String(Date.now())+'-'+(++seq),groups=new Set(['Импорт и экспорт GIF']);
 if(native()?.runFullDiagnostics)for(const name of ['Подключение и передача Glyph SDK','Тестовое уведомление','Аудиовыход Visualizer','Сеть HTTPS и обновления'])groups.add(name);
 for(const name of groups)replace(report,name,[row(name,'pending','Ожидание результата')]);
 active={report,token,groups,timer:setTimeout(()=>{if(active?.token!==token)return;for(const group of [...active.groups])finish(group,[row(group,'untested','Истекло время ожидания результата')],token)},42000)};
 $('run-diagnostics').disabled=true;show(report);
 if(native()?.runFullDiagnostics){try{native().runFullDiagnostics(token)}catch(e){for(const name of [...groups])if(name!=='Импорт и экспорт GIF')finish(name,[row(name,'fail',e.name)],token)}}
 try{const p=Array(169).fill(0);p[84]=255;const q=p.map((x,i)=>i===85?128:0);const frames=[{pixels:p,duration:90},{pixels:q,duration:180}];const blob=window.GlyphGIFExport(frames);const decoded=window.GlyphGIF.decode(await blob.arrayBuffer(),10,(canvas,ms)=>{let d=canvas.getContext('2d').getImageData(0,0,13,13).data;return {pixels:Array.from({length:169},(_,i)=>d[i*4]),duration:ms}});finish('Импорт и экспорт GIF',[ok('Импорт и экспорт GIF',JSON.stringify(decoded)===JSON.stringify(frames),'2 синтетических кадра, проверка всех пикселей и длительности')],token)}catch(e){finish('Импорт и экспорт GIF',[row('Импорт и экспорт GIF','fail',e.name)],token)}
}
function reportFile(){if(active||!completedFull)return;const blob=new Blob([lastReport],{type:'application/json'});if(native()?.saveBase64){blob.arrayBuffer().then(buffer=>{let b='',a=new Uint8Array(buffer);for(let i=0;i<a.length;i+=8192)b+=String.fromCharCode(...a.subarray(i,i+8192));native().saveBase64(btoa(b),'GlyphForge_Diagnostics.json','application/json')});return}const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='GlyphForge_Diagnostics.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),3000)}
$('run-diagnostics').onclick=full;$('export-diagnostics').onclick=reportFile;$('share-diagnostics').onclick=()=>{if(active||!completedFull)return;if(native()?.shareReport)native().shareReport(lastReport);else reportFile()};
for(const [id,method] of [['diag-perm-notifications','openNotificationSettings'],['diag-app-settings','openAppSettings'],['diag-perm-audio','requestDiagnosticAudioPermission'],['diag-perm-calls','requestCallPermission'],['diag-perm-post','requestDiagnosticNotificationPermission']]){const b=$(id);if(b){b.disabled=!native()?.[method];b.onclick=()=>native()?.[method]?.()}}
window.GlyphForgeDiagnostics={run,full,getReport:()=>lastReport};
const releases='https://github.com/CRILIK/GlyphForge-0.3V/releases';
$('open-releases').onclick=()=>window.GlyphNative?.openExternal?window.GlyphNative.openExternal(releases):window.open(releases,'_blank','noopener,noreferrer');
window.GlyphUpdateResult=r=>{const status=$('update-status');try{const o=typeof r==='string'?JSON.parse(r):r;status.textContent=o.available?'Доступна '+o.version+' · '+o.message:(o.message||'Новых выпусков пока нет');if(o.available&&o.url){let b=$('install-update');if(!b){b=document.createElement('button');b.className='primary';b.id='install-update';$('check-updates').parentNode.appendChild(b)}b.textContent=o.apkUrl?'Скачать и установить '+o.version:'Открыть обновление '+o.version;b.onclick=()=>{if(o.apkUrl&&native()?.installUpdate){status.textContent='Загрузка и проверка подписи…';native().installUpdate(o.apkUrl)}else native()?.openExternal?.(o.url)};if(!document.getElementById('update-banner')){const banner=document.createElement('button');banner.id='update-banner';banner.className='primary';banner.textContent='↑ Новая версия '+o.version+' — открыть обновление';banner.onclick=()=>b.click();document.querySelector('.header')?.appendChild(banner)}}}catch(e){status.textContent='Ошибка ответа обновлений: '+e.message}};
window.GlyphInstallStatus=message=>{$('update-status').textContent=message};
$('check-updates').onclick=()=>{if(window.GlyphNative?.checkForUpdates){$('update-status').textContent='Проверяю GitHub Releases…';window.GlyphNative.checkForUpdates()}else $('update-status').textContent='Обновления APK доступны только в Android. Проверь GitHub Releases.'};

setTimeout(()=>{if(!active&&!lastReport)run()},400);
if(native()?.checkForUpdates)setTimeout(()=>native().checkForUpdates(),1200);
})();