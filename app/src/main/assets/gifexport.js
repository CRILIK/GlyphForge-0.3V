/* Original small, standards-compatible GIF89a exporter for GlyphForge (13x13 grayscale). */
(function(){'use strict';
window.GlyphGIFExport=function(frames){
 const bytes=[];const b=n=>bytes.push(n&255),word=n=>{b(n);b(n>>8)},ascii=s=>{for(const c of s)b(c.charCodeAt(0))};
 ascii('GIF89a');word(13);word(13);b(0xf7);b(0);b(0); // 256-color global palette
 for(let i=0;i<256;i++){b(i);b(i);b(i)}
 // Application extension: animation loops forever
 b(0x21);b(0xff);b(11);ascii('NETSCAPE2.0');b(3);b(1);word(0);b(0);
 const packCodes=indices=>{
   const data=[];let buffer=0,bits=0;
   const writeCode=c=>{buffer|=c<<bits;bits+=9;while(bits>=8){data.push(buffer&255);buffer>>>=8;bits-=8}};
   // Correct but intentionally simple LZW stream: clear dictionary between pixels.
   writeCode(256);
   indices.forEach((c,i)=>{if(i)writeCode(256);writeCode(c)});
   writeCode(257);if(bits)data.push(buffer&255);
   return data;
 };
 for(const frame of frames.slice(0,200)){
  const delay=Math.max(2,Math.min(300,Math.round((frame.duration||180)/10)));
  b(0x21);b(0xf9);b(4);b(0x08);word(delay);b(0);b(0);
  b(0x2c);word(0);word(0);word(13);word(13);b(0);
  b(8);const data=packCodes(frame.pixels.map(v=>Math.max(0,Math.min(255,Math.round(+v||0)))));
  for(let i=0;i<data.length;i+=255){const s=data.slice(i,i+255);b(s.length);for(let v of s)b(v)}b(0);
 }
 b(0x3b);return new Blob([new Uint8Array(bytes)],{type:'image/gif'});
};
})();
