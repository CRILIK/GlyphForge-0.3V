/* GlyphForge format helpers. Nothing/Glyph Museum LED order: row major, 137 active pixels. */
(function(root){'use strict';
const ROWS13=[5,9,11,11,13,13,13,13,13,11,11,9,5];
const ROWS25=[7,11,15,17,19,21,21,23,23,25,25,25,25,25,25,25,23,23,21,21,19,17,15,11,7];
const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
const mask=(n,rows)=>{let indices=[];for(let y=0;y<n;y++){let start=(n-rows[y])/2;for(let x=0;x<rows[y];x++)indices.push(y*n+start+x)}return indices};
const IND13=mask(13,ROWS13),IND25=mask(25,ROWS25);
function square(arr){if(!Array.isArray(arr))throw Error('Кадр не содержит массива яркости');let out=new Array(169).fill(0);if(arr.length===137){IND13.forEach((i,k)=>out[i]=clamp(Math.round(+arr[k]||0),0,255));}
 else if(arr.length===169){IND13.forEach(i=>out[i]=clamp(Math.round(+arr[i]||0),0,255))}
 else if(arr.length===489 || arr.length===625){let full=new Array(625).fill(0);if(arr.length===489)IND25.forEach((i,k)=>full[i]=+arr[k]||0);else full=arr;
  // Downsample 25x25 -->13x13 without transposing, nearest source LED for sharp pixel art.
  IND13.forEach(i=>{const y=i/13|0,x=i%13;const sy=Math.round(y*24/12),sx=Math.round(x*24/12);out[i]=clamp(Math.round(full[sy*25+sx]||0),0,255)});
 }else throw Error('Неверная матрица: '+arr.length+' значений, ожидается 137, 169 или 489');return out;}
function safeMeta(raw){if(!raw || typeof raw!=='object' || Array.isArray(raw))return null;let m={};if(typeof raw.author==='string')m.author=raw.author.slice(0,100);if(Number.isSafeInteger(raw.postId)&&raw.postId>0)m.postId=raw.postId;
 if(typeof raw.url==='string'&&/^https:\/\/(app\.glyphmuseum\.com|glyphmuseum\.com)\//.test(raw.url))m.url=raw.url.slice(0,500);
 if(m.postId)m.url='https://app.glyphmuseum.com/post/'+m.postId;return Object.keys(m).length?m:null;}
function normalizeDesign(input){const raw=input&&input.design&&Array.isArray(input.design.frames)?input.design:input;if(!raw||!Array.isArray(raw.frames)||raw.frames.length===0)throw Error('Не найдены кадры');
 let frames=raw.frames.slice(0,400).map(f=>{let p=Array.isArray(f)?f:(f?.pixels||f?.p);return {pixels:square(p),duration:clamp(Math.round(Number(f?.duration??f?.d??f?.ms??100)||100),60,3000)}});
 const meta=safeMeta(raw.meta||input.meta);return {title:String(raw.title||raw.name||'Импортированный глиф').slice(0,80),source:String(raw.source||((raw.v===4||raw.v===1)?'Glyph Museum':'Импорт')).slice(0,100),frames, ...(meta?{meta}:{}),...(raw.id?{id:String(raw.id).slice(0,100)}:{})};}
function exportMuseum(design){const n=normalizeDesign(design);return {v:4,frames:n.frames.map(f=>({d:f.duration,p:IND13.map(i=>f.pixels[i])})),...(n.meta?{meta:n.meta}:{})};}
function getRuns(flags){let s=-1,r=[];for(let i=0;i<=flags.length;i++){if(i<flags.length&&flags[i]&&s<0)s=i;else if((i===flags.length||!flags[i])&&s>=0){r.push([s,i-1]);s=-1}}return r;}
function dotGeometry(image){let w=image.displayWidth||image.videoWidth||image.width,h=image.displayHeight||image.videoHeight||image.height;
 if(!Number.isFinite(w)||!Number.isFinite(h)||w<130||h<130||Math.abs(w/h-1)>.14||w>1500||h>1500)return null;
 const c=document.createElement('canvas');c.width=w;c.height=h;const g=c.getContext('2d',{willReadFrequently:true});g.clearRect(0,0,w,h);g.drawImage(image,0,0,w,h);
 const data=g.getImageData(0,0,w,h).data;let hor=new Uint8Array(w),ver=new Uint8Array(h);
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4;if(data[i+3]<128)continue;const v=Math.max(data[i],data[i+1],data[i+2]);if(v>34){hor[x]=1;ver[y]=1}}
 const rx=getRuns(hor),ry=getRuns(ver);if(rx.length<6||ry.length<3||rx.length>15||ry.length>15)return null;
 const centers=r=>r.map(([a,b])=>(a+b)/2);
 const xs=centers(rx),ys=centers(ry);
 const pitch=axis=>{const diffs=axis.slice(1).map((v,i)=>v-axis[i]).filter(v=>v>3).sort((a,b)=>a-b);if(!diffs.length)return 0;const low=diffs[0];const accepted=diffs.filter(v=>Math.abs(v-low)<low*.19);return accepted.length?accepted.reduce((a,b)=>a+b,0)/accepted.length:low};
 const px=pitch(xs),py=pitch(ys),p=(px+py)/2;
 if(!p||Math.abs(px-py)/p>.10||p<w/19||p>w/11)return null;
 const originX=(w-12*p)/2,originY=(h-12*p)/2;
 const align=(values,origin)=>values.every(v=>{const k=Math.round((v-origin)/p);return k>=0&&k<13&&Math.abs(v-(origin+k*p))<p*.13});
 if(!align(xs,originX)||!align(ys,originY))return null;
 return {w,h,p,originX,originY,canvas:c,confidence:1};
}
function rasterToPixels(image,options={}){let mode=options.mode||'auto',contrast=+options.contrast||1.4,invert=!!options.invert,bw=!!options.bw;
 const w=image.displayWidth||image.videoWidth||image.width,h=image.displayHeight||image.videoHeight||image.height;
 if(!w||!h)throw Error('Невозможно прочитать размеры');
 const dot=mode==='fit'||mode==='crop'?null:dotGeometry(image);
 if(mode==='dots'&&!dot)throw Error('На изображении не найдена точная 13×13 сетка светодиодов');
 let out=new Array(169).fill(0);
 if(dot){const g=dot.canvas.getContext('2d',{willReadFrequently:true}),data=g.getImageData(0,0,dot.w,dot.h).data;
  IND13.forEach(i=>{const x=i%13,y=i/13|0,cx=Math.round(dot.originX+x*dot.p),cy=Math.round(dot.originY+y*dot.p);let samples=[];for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
    let X=clamp(cx+dx,0,dot.w-1),Y=clamp(cy+dy,0,dot.h-1),p=(Y*dot.w+X)*4;
    samples.push((.2126*data[p]+.7152*data[p+1]+.0722*data[p+2])*(data[p+3]/255));}
    samples.sort((a,b)=>a-b);let v=samples[4];v=v<18?0:clamp(Math.round(v),0,255);if(invert)v=255-v;if(bw)v=v>=125?255:0;out[i]=v;
  });return out;}
 const c=document.createElement('canvas');c.width=c.height=13;const g=c.getContext('2d',{willReadFrequently:true});g.fillStyle='#000';g.fillRect(0,0,13,13);
 const scale=(mode==='fit'?Math.min:Math.max)(13/w,13/h);g.drawImage(image,(13-w*scale)/2,(13-h*scale)/2,w*scale,h*scale);
 const d=g.getImageData(0,0,13,13).data;IND13.forEach(i=>{const n=i*4,lum=(.2126*d[n]+.7152*d[n+1]+.0722*d[n+2]);let v=clamp(Math.round((lum-128)*contrast+128),0,255);if(invert)v=255-v;if(bw)v=v>=135?255:0;out[i]=v});return out;
}
root.GlyphFormat={ROWS13,IND13,normalizeDesign,exportMuseum,rasterToPixels,dotGeometry,square};
})(window);
