package com.weily.neonkartrush;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;

public class NeonKartView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random(77);
    private final ArrayList<Rival> rivals = new ArrayList<>();
    private final ArrayList<Pickup> pickups = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private long lastFrame = SystemClock.uptimeMillis();
    private boolean paused = false;
    private boolean leftHeld, rightHeld, boostHeld;
    private float speed = 0f, targetSpeed = 0f, playerX = 0f, distance = 0f;
    private float boost = 100f, shake = 0f, raceTime = 0f, countdown = 3.6f;
    private int coins = 0, lap = 1;
    private static final float TRACK_LENGTH = 5200f;
    private static final int TOTAL_LAPS = 3;
    private int state = 0; // 0 title, 1 racing, 2 finish
    private float finishPulse = 0f;

    private static class Rival {
        float x, worldD, speed;
        int color;
        Rival(float x, float d, float s, int c) { this.x=x; worldD=d; speed=s; color=c; }
    }
    private static class Pickup {
        float x, worldD; int type; boolean taken;
        Pickup(float x, float d, int type) { this.x=x; worldD=d; this.type=type; }
    }
    private static class Particle {
        float x,y,vx,vy,life,maxLife,size;
        Particle(float x,float y,float vx,float vy,float life,float size){
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;this.maxLife=life;this.size=size;
        }
    }

    public NeonKartView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        text.setTypeface(Typeface.create("sans", Typeface.BOLD));
        resetWorld();
    }

    public void setPaused(boolean v) { paused = v; lastFrame = SystemClock.uptimeMillis(); }

    private void resetWorld() {
        rivals.clear(); pickups.clear(); particles.clear();
        int[] cols = {0xffff4fa3,0xff7c4dff,0xffffd740,0xff69f0ae,0xffff6e40};
        for (int i=0;i<5;i++) rivals.add(new Rival(-.75f+i*.36f, 380f+i*550f, 155f+rng.nextFloat()*30f, cols[i]));
        for (int i=0;i<42;i++) pickups.add(new Pickup((rng.nextFloat()*1.55f)-.775f, 500f+i*250f, i%7==0?1:0));
        speed=0; targetSpeed=0; playerX=0; distance=0; boost=100; coins=0; lap=1; raceTime=0; countdown=3.6f; shake=0;
    }

    private void startRace() { resetWorld(); state=1; lastFrame=SystemClock.uptimeMillis(); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now=SystemClock.uptimeMillis();
        float dt=Math.min(.035f,(now-lastFrame)/1000f); lastFrame=now;
        if (!paused) update(dt);
        render(c);
        if (!paused) postInvalidateOnAnimation();
    }

    private void update(float dt) {
        if (state==0) { finishPulse += dt; return; }
        if (state==2) { finishPulse += dt; updateParticles(dt); return; }
        raceTime += dt;
        if (countdown>0) countdown -= dt;
        boolean active = countdown <= 0;
        targetSpeed = active ? (boostHeld && boost>0 ? 265f : 205f) : 0f;
        speed += (targetSpeed-speed)*Math.min(1f, dt*(speed<targetSpeed?2.8f:5.0f));
        if (active && boostHeld && boost>0) { boost=Math.max(0,boost-dt*29f); emitBoost(); }
        else boost=Math.min(100f,boost+dt*7f);

        float steer=0;
        if (leftHeld) steer-=1; if (rightHeld) steer+=1;
        playerX += steer*dt*(1.0f + speed/230f);
        float curve = trackCurve(distance);
        playerX -= curve*dt*.55f*(speed/210f);
        playerX = clamp(playerX,-1.08f,1.08f);
        if (Math.abs(playerX)>.88f) { speed -= dt*80f; shake=Math.max(shake,.7f); }

        if (active) distance += speed*dt*.92f;
        float raceD = (lap-1)*TRACK_LENGTH + distance;
        for (Rival r:rivals) {
            r.worldD += r.speed*dt*.92f;
            float totalTrack=TRACK_LENGTH*TOTAL_LAPS;
            if (r.worldD>totalTrack+1000) r.worldD-=totalTrack;
            float rel=r.worldD-raceD;
            if (rel>0 && rel<45 && Math.abs(r.x-playerX)<.23f) {
                speed*=.78f; shake=1f; playerX += (playerX-r.x)*.25f;
            }
        }
        for (Pickup q:pickups) {
            if (q.taken) continue;
            float qd=q.worldD;
            while (qd<raceD-100) qd += TRACK_LENGTH*TOTAL_LAPS;
            float rel=qd-raceD;
            if (rel>0 && rel<35 && Math.abs(q.x-playerX)<.18f) {
                q.taken=true;
                if (q.type==0) coins++; else { boost=Math.min(100,boost+38); speed=Math.max(speed,235); }
                shake=.25f;
                for(int k=0;k<18;k++) particles.add(new Particle(getWidth()/2f,getHeight()*.7f,(rng.nextFloat()-.5f)*260,(rng.nextFloat()-.5f)*220,.6f+rng.nextFloat()*.4f,3+rng.nextFloat()*6));
            }
        }
        if (distance>=TRACK_LENGTH) {
            distance-=TRACK_LENGTH; lap++;
            if (lap>TOTAL_LAPS) { lap=TOTAL_LAPS; state=2; finishPulse=0; }
        }
        shake=Math.max(0,shake-dt*3.2f);
        updateParticles(dt);
    }

    private void emitBoost() {
        if (rng.nextFloat()<.65f) particles.add(new Particle(getWidth()/2f+(rng.nextFloat()-.5f)*80,getHeight()*.86f,(rng.nextFloat()-.5f)*45,80+rng.nextFloat()*150,.25f+rng.nextFloat()*.3f,3+rng.nextFloat()*5));
    }
    private void updateParticles(float dt){
        for(int i=particles.size()-1;i>=0;i--){ Particle a=particles.get(i); a.life-=dt; if(a.life<=0){particles.remove(i);continue;} a.x+=a.vx*dt; a.y+=a.vy*dt; a.vy+=80*dt; }
    }

    private void render(Canvas c) {
        int w=getWidth(), h=getHeight(); if(w<=0||h<=0)return;
        float sx = shake>0 ? (rng.nextFloat()-.5f)*shake*12f : 0;
        float sy = shake>0 ? (rng.nextFloat()-.5f)*shake*8f : 0;
        c.save(); c.translate(sx,sy);
        drawSky(c,w,h);
        drawTrack(c,w,h);
        drawWorldObjects(c,w,h);
        drawPlayer(c,w,h);
        drawParticles(c);
        c.restore();
        if (state==0) drawTitle(c,w,h);
        else { drawHud(c,w,h); if(countdown>0 && state==1) drawCountdown(c,w,h); if(state==2) drawFinish(c,w,h); }
    }

    private void drawSky(Canvas c,int w,int h){
        Paint sky=new Paint(); sky.setShader(new LinearGradient(0,0,0,h*.55f,new int[]{0xff070a26,0xff1d1457,0xffe6428d},null,Shader.TileMode.CLAMP)); c.drawRect(0,0,w,h*.58f,sky);
        p.setShader(null); p.setColor(0x55ffffff);
        for(int i=0;i<38;i++){ float x=(i*197%997)/997f*w; float y=(i*83%311)/311f*h*.32f; float r=1+(i%3); c.drawCircle(x,y,r,p); }
        float sunX=w*.78f, sunY=h*.27f, sunR=h*.11f;
        p.setShader(new RadialGradient(sunX,sunY,sunR,new int[]{0xfffff59d,0xffff5aa5,0x00ff5aa5},null,Shader.TileMode.CLAMP)); c.drawCircle(sunX,sunY,sunR,p); p.setShader(null);
        Path m=new Path(); m.moveTo(0,h*.48f); for(int i=0;i<=12;i++){ float x=i*w/12f; float y=h*(.36f+((i*37)%7)*.015f); m.lineTo(x,y);} m.lineTo(w,h*.58f);m.lineTo(0,h*.58f);m.close(); p.setColor(0xff14163d);c.drawPath(m,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff7c4dff); for(int i=1;i<12;i+=2){float x=i*w/12f; c.drawLine(x,h*.45f,x+50,h*.39f,p);} p.setStyle(Paint.Style.FILL);
    }

    private float trackCurve(float d){ return (float)(Math.sin(d*.00135)*.55 + Math.sin(d*.00047+1.4)*.32); }

    private void drawTrack(Canvas c,int w,int h){
        float horizon=h*.39f;
        float curve=trackCurve((lap-1)*TRACK_LENGTH+distance);
        int segs=80;
        float prevY=h, prevHalf=w*.55f, prevCenter=w*.5f-playerX*w*.17f;
        for(int i=segs-1;i>=0;i--){
            float t=i/(float)segs;
            float y=horizon+(float)Math.pow(t,1.72)*(h-horizon);
            float half=w*(.035f+(float)Math.pow(t,1.25)*.50f);
            float perspective=(float)Math.pow(t,1.5);
            float wave=trackCurve((lap-1)*TRACK_LENGTH+distance + (1-t)*1450f);
            float center=w*.5f-playerX*w*.17f*perspective + wave*w*.14f*(1-t) + curve*w*.04f;
            int stripe=((int)(distance/55)+i)%2;
            p.setColor(stripe==0?0xff1a1c32:0xff22243f);
            Path road=quad(center-half,y,center+half,y,prevCenter+prevHalf,prevY,prevCenter-prevHalf,prevY); c.drawPath(road,p);
            float rumble=half*.08f;
            p.setColor(stripe==0?0xffff3d9a:0xff00e5ff);
            c.drawPath(quad(center-half-rumble,y,center-half,y,prevCenter-prevHalf,prevY,prevCenter-prevHalf-rumble,prevY),p);
            c.drawPath(quad(center+half,y,center+half+rumble,y,prevCenter+prevHalf+rumble,prevY,prevCenter+prevHalf,prevY),p);
            if(i%7<3){ p.setColor(0xaaffffff); float lw=Math.max(1,half*.012f); for(int lane=-1;lane<=1;lane+=2){ float x=center+lane*half*.34f; float px=prevCenter+lane*prevHalf*.34f; c.drawPath(quad(x-lw,y,x+lw,y,px+lw,prevY,px-lw,prevY),p);} }
            if(i%8==0){ p.setColor(0xff00e5ff); float lx=center-half*1.20f, rx=center+half*1.20f; float r=2+18*t; c.drawCircle(lx,y,r,p);c.drawCircle(rx,y,r,p); p.setColor(0x3300e5ff);c.drawCircle(lx,y,r*2.5f,p);c.drawCircle(rx,y,r*2.5f,p); }
            prevY=y;prevHalf=half;prevCenter=center;
        }
        p.setColor(0xff060712); c.drawRect(0,h*.965f,w,h,p);
    }

    private void drawWorldObjects(Canvas c,int w,int h){
        float raceD=(lap-1)*TRACK_LENGTH+distance;
        ArrayList<Object[]> draw=new ArrayList<>();
        for(Rival r:rivals){float rel=r.worldD-raceD; while(rel<-200) rel+=TRACK_LENGTH*TOTAL_LAPS; if(rel>35&&rel<1400) draw.add(new Object[]{rel,r});}
        for(Pickup q:pickups){ if(q.taken)continue; float qd=q.worldD; while(qd<raceD-100)qd+=TRACK_LENGTH*TOTAL_LAPS; float rel=qd-raceD;if(rel>25&&rel<1400)draw.add(new Object[]{rel,q}); }
        Collections.sort(draw,(a,b)->Float.compare((Float)b[0],(Float)a[0]));
        for(Object[] o:draw){ float rel=(Float)o[0]; float z=1f-clamp(rel/1400f,0,1); float y=h*.39f+(float)Math.pow(z,1.72)*h*.61f; float half=w*(.035f+(float)Math.pow(z,1.25)*.50f); float wave=trackCurve(raceD+rel); float center=w*.5f-playerX*w*.17f*(float)Math.pow(z,1.5)+wave*w*.14f*(1-z);
            if(o[1] instanceof Rival){ Rival r=(Rival)o[1]; float x=center+r.x*half*.72f; drawRival(c,x,y,Math.max(.16f,z),r.color); }
            else { Pickup q=(Pickup)o[1]; float x=center+q.x*half*.72f; drawPickup(c,x,y,Math.max(.15f,z),q.type); }
        }
    }

    private void drawRival(Canvas c,float x,float y,float s,int color){
        float bw=70*s,bh=42*s;
        p.setColor(0x4400e5ff);c.drawOval(x-bw*.65f,y-bh*.2f,x+bw*.65f,y+bh*.55f,p);
        p.setColor(0xff090a12);c.drawRoundRect(x-bw*.58f,y-bh*.05f,x+bw*.58f,y+bh*.48f,10*s,10*s,p);
        p.setColor(color);c.drawRoundRect(x-bw*.43f,y-bh*.38f,x+bw*.43f,y+bh*.25f,10*s,10*s,p);
        p.setColor(0xffe8faff);c.drawRoundRect(x-bw*.22f,y-bh*.31f,x+bw*.22f,y-bh*.07f,5*s,5*s,p);
        p.setColor(0xff05050a);c.drawCircle(x-bw*.48f,y+bh*.35f,10*s,p);c.drawCircle(x+bw*.48f,y+bh*.35f,10*s,p);
    }
    private void drawPickup(Canvas c,float x,float y,float s,int type){
        float r=(type==0?16:22)*s; int col=type==0?0xffffd740:0xff00e5ff; p.setColor((col&0x00ffffff)|0x33000000);c.drawCircle(x,y,r*2.3f,p);p.setColor(col);c.drawCircle(x,y,r,p);p.setColor(0xffffffff);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*1.2f);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(type==0?"$":"⚡",x,y+r*.42f,p);
    }

    private void drawPlayer(Canvas c,int w,int h){
        float x=w*.5f, y=h*.82f; float lean=(rightHeld?1:0)-(leftHeld?1:0); c.save();c.translate(x,y);c.rotate(lean*5);
        p.setColor(0x4400e5ff);c.drawOval(-w*.10f,h*.015f,w*.10f,h*.095f,p);
        p.setColor(0xff05060c);c.drawRoundRect(-w*.085f,-h*.015f,w*.085f,h*.07f,18,18,p);
        p.setColor(boostHeld&&boost>0?0xff00e5ff:0xffff3d9a);c.drawRoundRect(-w*.062f,-h*.065f,w*.062f,h*.04f,20,20,p);
        p.setColor(0xffd7f7ff);c.drawRoundRect(-w*.033f,-h*.052f,w*.033f,-h*.018f,9,9,p);
        p.setColor(0xff090a10);c.drawCircle(-w*.073f,h*.048f,h*.027f,p);c.drawCircle(w*.073f,h*.048f,h*.027f,p);
        p.setColor(0xff00e5ff);c.drawRect(-w*.068f,h*.02f,-w*.035f,h*.03f,p);c.drawRect(w*.035f,h*.02f,w*.068f,h*.03f,p);
        if(boostHeld&&boost>0){p.setShader(new LinearGradient(0,h*.04f,0,h*.16f,0xffe8ffff,0x0000e5ff,Shader.TileMode.CLAMP)); Path f=new Path();f.moveTo(-w*.027f,h*.04f);f.lineTo(0,h*.17f+rng.nextFloat()*30);f.lineTo(w*.027f,h*.04f);f.close();c.drawPath(f,p);p.setShader(null);} c.restore();
    }

    private void drawParticles(Canvas c){ for(Particle a:particles){float alpha=clamp(a.life/a.maxLife,0,1);p.setColor(Color.argb((int)(alpha*220),0,229,255));c.drawCircle(a.x,a.y,a.size,p);} }

    private void drawHud(Canvas c,int w,int h){
        text.setTextAlign(Paint.Align.LEFT); text.setTypeface(Typeface.create("sans",Typeface.BOLD));
        text.setTextSize(h*.045f); text.setColor(0xffffffff); text.setShadowLayer(8,0,2,0xaa000000); c.drawText(String.format(Locale.US,"%03d km/h",(int)speed),w*.035f,h*.085f,text);
        text.setTextSize(h*.030f);text.setColor(0xffb7eaff);c.drawText("LAP "+lap+" / "+TOTAL_LAPS,w*.038f,h*.13f,text);
        text.setTextAlign(Paint.Align.CENTER);text.setTextSize(h*.032f);text.setColor(0xffffd740);c.drawText("COINS  "+coins,w*.5f,h*.075f,text);
        text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(h*.028f);text.setColor(0xffffffff);c.drawText(formatTime(raceTime),w*.965f,h*.075f,text);
        // boost meter
        float bx=w*.77f, by=h*.90f, bw=w*.17f,bh=h*.026f; p.setColor(0x66101328);c.drawRoundRect(bx,by,bx+bw,by+bh,bh/2,bh/2,p); p.setShader(new LinearGradient(bx,0,bx+bw,0,0xffff3d9a,0xff00e5ff,Shader.TileMode.CLAMP));c.drawRoundRect(bx,by,bx+bw*(boost/100f),by+bh,bh/2,bh/2,p);p.setShader(null);text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(h*.023f);text.setColor(0xffffffff);c.drawText("BOOST",bx-10,by+bh*.85f,text);
        // controls hint/buttons
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0x55ffffff);float rr=h*.07f;c.drawCircle(w*.075f,h*.87f,rr,p);c.drawCircle(w*.19f,h*.87f,rr,p);c.drawCircle(w*.93f,h*.84f,rr*1.08f,p);p.setStyle(Paint.Style.FILL);text.setTextAlign(Paint.Align.CENTER);text.setTextSize(h*.052f);text.setColor(0x88ffffff);c.drawText("‹",w*.075f,h*.888f,text);c.drawText("›",w*.19f,h*.888f,text);text.setTextSize(h*.023f);text.setColor(0xccffffff);c.drawText("BOOST",w*.93f,h*.848f,text);
    }

    private void drawCountdown(Canvas c,int w,int h){
        String s=countdown>2.6f?"3":countdown>1.6f?"2":countdown>.6f?"1":"GO!"; float phase=(countdown%1f); text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create("sans",Typeface.BOLD));text.setTextSize(h*(.18f+.05f*(1-phase)));text.setColor(s.equals("GO!")?0xff00e5ff:0xffffffff);text.setShadowLayer(22,0,0,s.equals("GO!")?0xff00e5ff:0xffff3d9a);c.drawText(s,w*.5f,h*.46f,text);text.clearShadowLayer();
    }
    private void drawTitle(Canvas c,int w,int h){
        p.setColor(0x66000000);c.drawRect(0,0,w,h,p);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create("sans",Typeface.BOLD));text.setTextSize(h*.115f);text.setColor(0xffffffff);text.setShadowLayer(24,0,0,0xff00e5ff);c.drawText("NEON KART",w*.5f,h*.36f,text);text.setTextSize(h*.095f);text.setColor(0xffff3d9a);text.setShadowLayer(20,0,0,0xffff3d9a);c.drawText("RUSH",w*.5f,h*.47f,text);text.clearShadowLayer();text.setTypeface(Typeface.create("sans",Typeface.BOLD));text.setTextSize(h*.031f);text.setColor(0xffd9f8ff);c.drawText("3 LAPS  •  5 RIVALS  •  BOOST  •  COINS",w*.5f,h*.58f,text); float pulse=(float)(.75+.25*Math.sin(finishPulse*4));text.setTextSize(h*.040f);text.setColor(Color.argb((int)(pulse*255),255,255,255));c.drawText("TAP TO RACE",w*.5f,h*.72f,text);text.setTextSize(h*.021f);text.setColor(0xff9aa6c3);c.drawText("Original procedural arcade racer • no external art assets",w*.5f,h*.82f,text);
    }
    private void drawFinish(Canvas c,int w,int h){
        p.setColor(0xaa050514);c.drawRect(0,0,w,h,p);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create("sans",Typeface.BOLD));text.setTextSize(h*.12f);text.setColor(0xff00e5ff);text.setShadowLayer(28,0,0,0xff00e5ff);c.drawText("FINISH!",w*.5f,h*.36f,text);text.clearShadowLayer();text.setTextSize(h*.040f);text.setColor(0xffffffff);c.drawText("TIME  "+formatTime(raceTime)+"     COINS  "+coins,w*.5f,h*.52f,text);text.setTextSize(h*.032f);text.setColor(0xffffd740);c.drawText("TAP TO RACE AGAIN",w*.5f,h*.68f,text);
    }

    private String formatTime(float t){ int min=(int)(t/60), sec=(int)t%60, ms=(int)((t-(int)t)*100); return String.format(Locale.US,"%02d:%02d.%02d",min,sec,ms); }
    private Path quad(float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4){Path q=new Path();q.moveTo(x1,y1);q.lineTo(x2,y2);q.lineTo(x3,y3);q.lineTo(x4,y4);q.close();return q;}
    private float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}

    @Override
    public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN && (state==0||state==2)){ startRace(); return true; }
        if(state!=1)return true;
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){leftHeld=rightHeld=boostHeld=false;return true;}
        leftHeld=rightHeld=boostHeld=false;
        for(int i=0;i<e.getPointerCount();i++){
            float x=e.getX(i), y=e.getY(i), w=getWidth(), h=getHeight();
            if(x>w*.82f && y>h*.68f) boostHeld=true;
            else if(y>h*.63f && x<w*.14f) leftHeld=true;
            else if(y>h*.63f && x<w*.30f) rightHeld=true;
            else { if(x<w*.45f) leftHeld=true; else if(x>w*.55f) rightHeld=true; }
        }
        return true;
    }
}
