package com.example.glyphforge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;

/** Pure, deterministic 13x13 renderer. UI and native output use the same 137-LED mask. */
public final class MusicFrameRenderer {
    private MusicFrameRenderer() {}
    public static final int SIZE = 13;
    public static final int LEDS = 137;
    public static boolean active(int x, int y) { int a=x-6,b=y-6;return x>=0&&x<13&&y>=0&&y<13&&a*a+b*b<=41; }
    public static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    public static final class Levels {
        public final float[] bands=new float[8];
        public float rms,beat;
    }
    /** FFT spectrum in Android Visualizer's packed format. */
    public static Levels analyzeFft(byte[] fft) {
        Levels out=new Levels();
        if(fft==null||fft.length<16)return out;
        int bins=fft.length/2, count=0;double sum=0;
        for(int k=1;k<bins;k++) {
            int re=fft[2*k],im=fft[2*k+1];
            float mag=(float)Math.hypot(re,im)/175f;
            int band=Math.min(7,(int)(Math.log1p(k)/Math.log1p(bins-1)*8));
            out.bands[band]=Math.max(out.bands[band],clamp(mag,0,1));
            sum+=mag;count++;
        }
        out.rms=clamp((float)(sum/Math.max(1,count))*2.2f,0,1);
        return out;
    }
    /** PCM 16-bit little-endian mono: DFT of 512 samples, ~12 fps, no binary libraries. */
    public static Levels analyzePcm(short[] pcm,int count,int sampleRate){
        Levels out=new Levels();if(pcm==null||count<256)return out;
        int n=Math.min(2048,Integer.highestOneBit(count));int start=count-n;
        double rms=0;for(int i=0;i<n;i++){double v=pcm[start+i]/32768d;rms+=v*v;}
        out.rms=clamp((float)(Math.sqrt(rms/n)*3.0),0,1);
        for(int b=0;b<8;b++){
            // Logarithmically spaced bands, avoid frequencies below 30Hz.
            double f0=40*Math.pow(160, b/8.0),f1=40*Math.pow(160,(b+1)/8.0);
            int begin=Math.max(1,(int)Math.round(f0*n/sampleRate));
            int end=Math.min(n/2-1,Math.max(begin,(int)Math.round(f1*n/sampleRate)));
            float peak=0;for(int k=begin;k<=end;k+=Math.max(1,(end-begin)/4)){
                double re=0,im=0;
                for(int i=0;i<n;i++){double w=(.5-.5*Math.cos(2*Math.PI*i/(n-1)))*pcm[start+i]/32768.0;
                    double phase=2*Math.PI*k*i/n;re+=w*Math.cos(phase);im+=w*Math.sin(phase);}
                peak=Math.max(peak,clamp((float)(Math.hypot(re,im)*8/n),0,1));
            }
            out.bands[b]=peak;
        }
        return out;
    }
    public static int[] render(String mode,Levels levels,int[] artwork,float time,float sensitivity,float brightness,float beat){
        int[] p=new int[169];
        float vol=clamp(levels.rms*sensitivity,0,1), bass=clamp((levels.bands[0]+levels.bands[1])*0.55f*sensitivity,0,1);
        float onset=clamp(beat,0,1);
        for(int y=0;y<13;y++)for(int x=0;x<13;x++) {
            if(!active(x,y))continue;
            int i=y*13+x;float dx=x-6,dy=y-6,d=(float)Math.hypot(dx,dy),a=(float)Math.atan2(dy,dx);
            float v=0;
            switch(mode){
                case "bars":{
                    int band=Math.min(7,x*8/13);
                    float height=1+clamp(levels.bands[band]*sensitivity*2.6f,0,1)*11;
                    if(12-y<=height&&levels.bands[band]>.024f)v=.28f+levels.bands[band]*.85f;
                    break;}
                case "ring": {
                    float radius=2+vol*4.3f+onset*.8f;
                    v=Math.abs(d-radius)<.8f?.35f+vol*.5f+onset*.5f:0;break;}
                case "wave":{
                    float target=6+(float)Math.sin(x*.9f+time*3)*(.6f+levels.bands[2]*sensitivity*3f);
                    v=Math.abs(y-target)<.65f?.25f+vol*.8f:0;break;}
                case "spark":{
                    float phase=(float)Math.sin((x*9+y*13)*13.17+Math.floor(time*9))*0.5f+0.5f;
                    v=(bass+onset>0.32f&&phase>0.72f)?clamp((bass+onset)*.85f,0,1):0;
                    break;}
                case "orbit":{
                    float angle=time*2.7f+vol*4,theta=Math.abs((float)Math.atan2(Math.sin(a-angle),Math.cos(a-angle)));
                    v=(Math.abs(d-4.6f)<1.05&&theta<.45f)?(.3f+vol*.7f+onset*.5f):0;break;}
                case "art":{
                    int pixel=artwork!=null&&artwork.length==169?artwork[i]:0;
                    // Single artwork can react continuously (brightness) instead of restarting animation.
                    v=pixel/255f*(.10f+vol*.9f+onset*.30f);break;}
                default:break;
            }
            p[i]=(int)(clamp(v*brightness,0,1)*255);
        }
        return p;
    }
    public static int[] artworkFromState(String json,String designId,int frameIndex){
        try{
            JSONObject root=new JSONObject(json);JSONArray designs=root.optJSONArray("designs");if(designs==null)return null;
            for(int i=0;i<designs.length();i++){
                JSONObject d=designs.optJSONObject(i);if(d==null||!designId.equals(d.optString("id")))continue;
                JSONArray frames=d.optJSONArray("frames");if(frames==null||frames.length()==0)break;
                JSONArray vals=frames.getJSONObject(Math.floorMod(frameIndex,frames.length())).getJSONArray("pixels");if(vals.length()!=169)break;
                int[] pixels=new int[169];for(int j=0;j<169;j++)pixels[j]=vals.optInt(j);return pixels;
            }
        }catch(Exception ignored){}return null;
    }
}
