/*
 * GlyphForge standalone GIF89a/GIF87a decoder, original implementation.
 * Supports indexed palettes, LZW, transparency, interlace, and disposal.
 * The caller converts each composited image immediately to 13x13 pixels.
 */
(function(){'use strict';
function decode(buffer,limit,convert){
 const a=new Uint8Array(buffer);let off=0;
 const u8=()=>{if(off>=a.length)throw Error('GIF обрезан');return a[off++]};
 const u16=()=>u8()|(u8()<<8);
 const bytes=n=>{if(off+n>a.length)throw Error('GIF обрезан');let r=a.subarray(off,off+n);off+=n;return r};
 const string=n=>String.fromCharCode(...bytes(n));
 function colors(n){const p=[];for(let i=0;i<n;i++)p.push([u8(),u8(),u8()]);return p}
 function blocks(){const chunks=[];let total=0;while(true){const n=u8();if(!n)break;chunks.push(bytes(n));total+=n;if(total>80e6)throw Error('Слишком большой GIF')}let result=new Uint8Array(total),pos=0;for(const c of chunks){result.set(c,pos);pos+=c.length}return result}
 function lzw(minCode,raw,expected){
  if(minCode<2||minCode>8)throw Error('Неверный LZW');
  const clear=1<<minCode,stop=clear+1;
  let table=[],codeSize=minCode+1,nextCode=stop+1,bit=0,previous=null;
  function reset(){table=[];for(let i=0;i<clear;i++)table.push([i]);table.push(null,null);codeSize=minCode+1;nextCode=stop+1;previous=null}
  function readCode(){if(bit+codeSize>raw.length*8)return -1;let code=0;for(let i=0;i<codeSize;i++){code|=((raw[(bit+i)>>3]>>((bit+i)&7))&1)<<i}bit+=codeSize;return code}
  reset();const out=new Uint8Array(expected);let count=0;
  while(true){const code=readCode();if(code<0||code===stop)break;if(code===clear){reset();continue}
   let entry;if(table[code])entry=table[code];else if(code===nextCode&&previous)entry=previous.concat(previous[0]);else break;
   for(const index of entry)if(count<expected)out[count++]=index;
   if(previous&&nextCode<4096){table[nextCode++]=previous.concat(entry[0]);if(nextCode===(1<<codeSize)&&codeSize<12)codeSize++}
   previous=entry;if(count>=expected)break;
  }
  return out;
 }
 const sig=string(6);if(sig!=='GIF89a'&&sig!=='GIF87a')throw Error('Это не GIF');
 const width=u16(),height=u16(),packed=u8(),bgIndex=u8();u8();
 if(width<1||height<1||width*height>4_000_000)throw Error('Слишком большой размер GIF');
 const global=(packed&128)?colors(1<<((packed&7)+1)):null;
 const pixels=new Uint8ClampedArray(width*height*4);let gce={delay:160,transparent:-1,disposal:0};
 const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;const ctx=canvas.getContext('2d',{willReadFrequently:true});
 let frames=[];
 while(off<a.length){const sep=u8();if(sep===0x3b)break;
  if(sep===0x21){const label=u8();if(label===0xf9){let length=u8();if(length<4)throw Error('Битый graphic-control');let p=u8(),delay=u16(),trans=u8();if(length>4)bytes(length-4);u8();gce={delay:Math.max(60,delay*10||160),transparent:(p&1)?trans:-1,disposal:(p>>2)&7};}
   else blocks();continue;}
  if(sep!==0x2c)throw Error('Неизвестный GIF блок '+sep);
  let left=u16(),top=u16(),fw=u16(),fh=u16(),fp=u8();
  if(fw<1||fh<1||fw*fh>4_000_000)throw Error('Недопустимый размер кадра');
  let palette=(fp&128)?colors(1<<((fp&7)+1)):global;
  let minCode=u8(),indices=lzw(minCode,blocks(),fw*fh);
  const before=gce.disposal===3?pixels.slice():null;
  let rows=[];if(fp&64){for(const [start,step] of [[0,8],[4,8],[2,4],[1,2]])for(let y=start;y<fh;y+=step)rows.push(y)}else{for(let y=0;y<fh;y++)rows.push(y)}
  for(let ri=0;ri<fh;ri++){let y=top+rows[ri];if(y<0||y>=height)continue;
   for(let x=0;x<fw;x++){let dx=x+left;if(dx>=width)continue;const idx=indices[ri*fw+x];if(idx===gce.transparent)continue;const color=palette?.[idx]||[0,0,0],di=(y*width+dx)*4;
    pixels[di]=color[0];pixels[di+1]=color[1];pixels[di+2]=color[2];pixels[di+3]=255;
   }
  }
  const image=new ImageData(pixels.slice(),width,height);ctx.putImageData(image,0,0);
  frames.push(convert(canvas,gce.delay));
  if(frames.length>=limit)break;
  if(gce.disposal===2){for(let y=top;y<top+fh&&y<height;y++)for(let x=left;x<left+fw&&x<width;x++){let i=(y*width+x)*4;pixels[i]=0;pixels[i+1]=0;pixels[i+2]=0;pixels[i+3]=0;}}
  else if(gce.disposal===3&&before)pixels.set(before);
  gce={delay:160,transparent:-1,disposal:0};
 }
 return frames;
}
window.GlyphGIF={decode};
})();
