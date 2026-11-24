/* Diagnostic report: no personal messages, photos, passwords, media or package names. */
(()=>{'use strict';
const $=id=>document.getElementById(id);let lastReport='';
const test=(name,pass,detail)=>({name,pass:!!pass,detail:String(detail??'').slice(0,180)});
function diagnostics(){
 const tests=[], state=window.GlyphDiagnosticsReadState?.()||{designs:[],rules:[],scenes:[]};
 const mask=Array.from({length:169},(_,i)=>{let x=i%13-6,y=Math.floor(i/13)-6;return x*x+y*y<=41});
 tests.push(test('Матрица 13×13 / 137 LED',mask.filter(Boolean).length===137,mask.filter(Boolean).length+' активных точек'));
 const designs=Array.isArray(state.designs)?state.designs:[];
 tests.push(test('Библиотека доступна',Array.isArray(state.designs),designs.length+' проектов'));
 const exportsReady=typeof window.GlyphFormat?.exportMuseum==='function';
 tests.push(test('Конвертер Glyph Museum JSON',exportsReady,exportsReady?'Экспортёр доступен':'Экспортёр не загружен'));
 tests.push(test('Импорт GIF',typeof window.GlyphGIF==='function'||!!window.GlyphGIF,'Модуль декодирования '+(window.GlyphGIF?'доступен':'не найден')));
 tests.push(test('Экспорт GIF',typeof window.GlyphGIFExport==='function',window.GlyphGIFExport?'Кодировщик доступен':'Кодировщик не найден'));

 let frames=0,bad=0;for(const d of designs){if(!Array.isArray(d.frames)||d.frames.length===0){bad++;continue}for(const f of d.frames){frames++;if(!Array.isArray(f.pixels)||f.pixels.length!==169||!f.pixels.every(x=>Number.isFinite(+x)&&+x>=0&&+x<=255)||!(+f.duration>=1))bad++}}
 tests.push(test('Структура кадров',bad===0,frames+' кадров; некорректных '+bad));
 tests.push(test('Правила и сценарии',Array.isArray(state.rules)&&Array.isArray(state.scenes),(state.rules?.length||0)+' правил; '+(state.scenes?.length||0)+' сценариев'));
 try {let sample=JSON.stringify({kind:'glyphforge-library',version:1,designs:designs,rules:[],scenes:[]});let back=JSON.parse(sample);tests.push(test('Экспорт и импорт JSON',back.designs.length===designs.length,'Локальная сериализация успешна'))}catch(e){tests.push(test('Экспорт и импорт JSON',false,e.message))}
 try{const k='glyphforge-selftest-temp';localStorage.setItem(k,'ok');let ok=localStorage.getItem(k)==='ok';localStorage.removeItem(k);tests.push(test('Локальные настройки',ok,ok?'Чтение/запись доступна':'Чтение/запись недоступна'))}catch(e){tests.push(test('Локальные настройки',false,e.name))}
 let native={mode:'browser',hardware_unverified:true};try{if(window.GlyphNative?.getDiagnostics){native=JSON.parse(window.GlyphNative.getDiagnostics());tests.push(test('Android диагностика',!native.error,'Native API доступен'));if(native.sdkInstalled!==undefined)tests.push(test('Nothing SDK загружен',native.sdkInstalled,native.sdkInstalled?'Классы SDK найдены':'SDK не найден'));if(native.downloadsWritable!==undefined)tests.push(test('Сохранение в Downloads',native.downloadsWritable,native.downloadsWritable?'Проверочный файл создан и удалён':'Не удалось создать тестовый файл'))}}catch(e){tests.push(test('Android диагностика',false,e.message))}
 const failed=tests.filter(t=>!t.pass).length;
 const report={app:'GlyphForge',reportVersion:1,createdAt:new Date().toISOString(),tests,summary:{total:tests.length,passed:tests.length-failed,failed},native,limitations:['Физическое свечение Glyph Matrix требует проверки глазами','События других приложений не симулируются','Аудиозахват без разрешений не запускается']};
 return report;
}
function show(r){lastReport=JSON.stringify(r,null,2);const output=$('diagnostics-output');output.textContent='GlyphForge · отчёт самопроверки\n'+r.createdAt+'\n\n'+r.tests.map(t=>(t.pass?'✓':'✕')+' '+t.name+' — '+t.detail).join('\n')+'\n\nПройдено: '+r.summary.passed+'/'+r.summary.total+'\n\n'+r.limitations.join('\n');$('diagnostics-state').textContent=r.summary.failed?'ТРЕБУЕТ ВНИМАНИЯ':'ГОТОВО';return r}
function run(){return show(diagnostics())}
function reportFile(){if(!lastReport)run();const blob=new Blob([lastReport],{type:'application/json'});if(window.GlyphNative?.saveBase64){blob.arrayBuffer().then(buffer=>{let b='',a=new Uint8Array(buffer);for(let i=0;i<a.length;i+=8192)b+=String.fromCharCode(...a.subarray(i,i+8192));window.GlyphNative.saveBase64(btoa(b),'GlyphForge_Diagnostics.json','application/json')});return}const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='GlyphForge_Diagnostics.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),3000)}
$('run-diagnostics').onclick=run;$('export-diagnostics').onclick=reportFile;$('share-diagnostics').onclick=()=>{if(!lastReport)run();if(window.GlyphNative?.shareReport)window.GlyphNative.shareReport(lastReport);else reportFile()};
window.GlyphForgeDiagnostics={run,getReport:()=>lastReport};
const releases='https://github.com/CRILIK/GlyphForge-0.3V/releases';
$('open-releases').onclick=()=>window.GlyphNative?.openExternal?window.GlyphNative.openExternal(releases):window.open(releases,'_blank','noopener,noreferrer');
window.GlyphUpdateResult=r=>{const status=$('update-status');try{const o=typeof r==='string'?JSON.parse(r):r;status.textContent=o.available?'Доступна '+o.version+' · '+o.message:(o.message||'Новых выпусков пока нет');if(o.available&&o.url){let b=$('install-update');if(!b){b=document.createElement('button');b.className='primary';b.id='install-update';$('check-updates').parentNode.appendChild(b)}b.textContent='Открыть обновление '+o.version;b.onclick=()=>window.GlyphNative?.openExternal?.(o.url);if(!document.getElementById('update-banner')){const banner=document.createElement('button');banner.id='update-banner';banner.className='primary';banner.textContent='↑ Новая версия '+o.version+' — открыть обновление';banner.onclick=()=>window.GlyphNative?.openExternal?.(o.url);document.querySelector('.header')?.appendChild(banner)}}}catch(e){status.textContent='Ошибка ответа обновлений: '+e.message}};
$('check-updates').onclick=()=>{if(window.GlyphNative?.checkForUpdates){$('update-status').textContent='Проверяю GitHub Releases…';window.GlyphNative.checkForUpdates()}else $('update-status').textContent='Обновления APK доступны только в Android. Проверь GitHub Releases.'};
// Run a non-invasive local test after first render, without asking for any permissions.
setTimeout(run,400);
// Automatic background update check is non-blocking and only queries the official release endpoint.
if(window.GlyphNative?.checkForUpdates){setTimeout(()=>window.GlyphNative.checkForUpdates(),1200)}
})();