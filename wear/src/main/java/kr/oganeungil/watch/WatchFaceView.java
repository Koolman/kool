package kr.oganeungil.watch;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import kr.oganeungil.core.Commute;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Actual interactive Wear OS app UI. No continuous GPS, networking, or tile updates. */
final class WatchFaceView extends View {
    interface Actions { void refresh(); void previous(); void next(); void settings(); void chooseDirection(); }
    private final Paint paint=new Paint(3);private final Typeface numbers;
    private final Path refreshPath=new Path();private final Actions actions;
    private float scale=1,offsetX,offsetY;
    Commute.Direction direction=Commute.Direction.MORNING;
    Repository.Snapshot snapshot;
    int trainIndex=-1;
    boolean loading=false;
    String status="";
    WatchFaceView(Context context,Actions actions){super(context);this.actions=actions;numbers=getResources().getFont(kr.oganeungil.watch.R.font.date_numerals);setBackgroundColor(Color.BLACK);setFocusable(true);status="길게 눌러 설정";}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float size=Math.min(getWidth(),getHeight());scale=size/400;offsetX=(getWidth()-size)/2;offsetY=(getHeight()-size)/2;canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
        text(canvas,ZonedDateTime.now(Commute.SEOUL).format(DateTimeFormatter.ofPattern("HH:mm")),200,94,59,Color.WHITE,true,Paint.Align.CENTER);
        String badge=snapshot!=null&&snapshot.demo()?"예시 · ":"";
        String state=loading?"갱신 중…":snapshot!=null&&java.time.Duration.between(snapshot.fetched(),java.time.Instant.now()).getSeconds()>90?"새로고침 필요":status;
        text(canvas,badge+state,200,116,10,0xFFB8B8B8,false,Paint.Align.CENTER);
        if(direction==Commute.Direction.MORNING){
            bus(canvas,0,172);bus(canvas,1,222);line(canvas,247);train(canvas,273);
        }else{train(canvas,170);line(canvas,255);bus(canvas,0,295);}
        refresh(canvas);canvas.restore();
    }
    private void line(Canvas canvas,float y){paint.setColor(0xFF343434);paint.setStrokeWidth(.7f);canvas.drawLine(40,y,360,y,paint);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean number,Paint.Align align){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(number?numbers:Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextAlign(align);paint.setTextSize(size);c.drawText(s,x,y,paint);}
    private void fit(Canvas c,String s,float x,float y,float size,float width,int color,boolean numeric){paint.setTypeface(numeric?numbers:Typeface.DEFAULT);paint.setTextSize(size);float measured=paint.measureText(s);text(c,s,x,y,measured>width?size*width/measured:size,color,numeric,Paint.Align.LEFT);}
    private void busIcon(Canvas c,float y,int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(45,y-27,73,y+4,7,7,paint);paint.setColor(Color.WHITE);c.drawRoundRect(52,y-22,66,y-3,3,3,paint);paint.setColor(color);c.drawRect(54,y-20,64,y-12,paint);paint.setColor(color);c.drawCircle(55,y-7,1.3f,paint);c.drawCircle(63,y-7,1.3f,paint);}
    private void bus(Canvas c,int index,float y){
        boolean morning=direction==Commute.Direction.MORNING;String name=morning?(index==0?"5001-1A":"30"):"5001-1B";int count=morning?(index==0?2:1):3;
        int color=name.equals("30")?0xFFA3D83D:0xFFFF9D00;busIcon(c,y,color);fit(c,name,82,y,28,112,Color.WHITE,true);
        Repository.Bus bus=snapshot!=null&&snapshot.buses().size()>index?snapshot.buses().get(index):null;
        for(int i=0;i<count;i++){
            float x=morning?207+i*68:202+i*57;Instant at=bus!=null&&bus.arrivals().size()>i?bus.arrivals().get(i):null;
            Instant now=Instant.now();boolean valid=at!=null&&!now.isAfter(at.plusSeconds(45))&&snapshot!=null&&Duration.between(snapshot.fetched(),now).getSeconds()<=90;
            if(valid){String n=""+Commute.remaining(at,now);text(c,n,x,y,29,Color.WHITE,true,Paint.Align.LEFT);paint.setTextSize(29);paint.setTypeface(numbers);float w=paint.measureText(n);text(c,"분",x+w+1,y,12,Color.WHITE,false,Paint.Align.LEFT);
                if(!morning)text(c,Commute.arrivalClock(at),x+17,y+19,13,0xFFC1C1C1,true,Paint.Align.CENTER);
            }else text(c,"—",x,y,24,0xFFBBBBBB,true,Paint.Align.LEFT);
        }
        if(bus!=null&&!bus.notice().isBlank()&&(bus.arrivals().isEmpty()||!morning))text(c,bus.notice(),200,y+(morning?17:34),9,0xFFAAAAAA,false,Paint.Align.CENTER);
    }
    private void train(Canvas c,float y){
        boolean morning=direction==Commute.Direction.MORNING;String first=morning?"신논현":"마곡나루",second=morning?"마곡나루":"신논현";
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFC49A26);c.drawCircle(86,y-7,12,paint);text(c,"9",86,y+1,18,Color.WHITE,true,Paint.Align.CENTER);
        text(c,first,104,y,16,Color.WHITE,false,Paint.Align.LEFT);text(c,second,282,y,16,Color.WHITE,false,Paint.Align.CENTER);
        String a="—",b="—";if(snapshot!=null&&trainIndex>=0&&trainIndex<snapshot.trains().size()){Commute.Train t=snapshot.trains().get(trainIndex);a=Commute.clock(t.departure());b=Commute.clock(t.arrival());}
        text(c,a,128,y+35,32,Color.WHITE,true,Paint.Align.CENTER);text(c,b,282,y+35,32,Color.WHITE,true,Paint.Align.CENTER);
        paint.setColor(0xFFD7D7D7);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(192,y+21,215,y+21,paint);c.drawLine(209,y+15,215,y+21,paint);c.drawLine(209,y+27,215,y+21,paint);
        chevron(c,50,y+20,false);chevron(c,350,y+20,true);
        text(c,"빠른하차 "+(morning?"2-1 · 4-4":"1-1 · 4-4"),200,y+61,12,0xFFC0C0C0,false,Paint.Align.CENTER);
        if(snapshot!=null&&trainIndex<0)text(c,snapshot.trains().isEmpty()?snapshot.notice():"오늘 운행 종료",200,y+47,9,0xFFAAAAAA,false,Paint.Align.CENTER);
    }
    private void chevron(Canvas c,float x,float y,boolean right){float sign=right?1:-1;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);Path p=new Path();p.moveTo(x-sign*4,y-7);p.lineTo(x+sign*3,y);p.lineTo(x-sign*4,y+7);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}
    private void refresh(Canvas c){
        refreshPath.reset();refreshPath.moveTo(93,352);refreshPath.quadTo(200,398,307,352);refreshPath.quadTo(324,347,313,365);refreshPath.cubicTo(256,407,144,407,87,365);refreshPath.quadTo(76,347,93,352);refreshPath.close();
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF121212);c.drawPath(refreshPath,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(0xFFBDBDBD);c.drawPath(refreshPath,paint);
        Path baseline=new Path();baseline.moveTo(129,371);baseline.quadTo(200,400,271,371);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setTypeface(numbers);paint.setTextSize(19);paint.setTextAlign(Paint.Align.LEFT);String label=loading?"Updating":"Refresh";float length=new PathMeasure(baseline,false).getLength();c.drawTextOnPath(label,baseline,(length-paint.measureText(label))/2,0,paint);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){postDelayed(longPress,650);return true;}
        if(event.getAction()==MotionEvent.ACTION_CANCEL){removeCallbacks(longPress);return true;}
        if(event.getAction()==MotionEvent.ACTION_UP){removeCallbacks(longPress);if(longPressed){longPressed=false;return true;}float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
            if(y>338){actions.refresh();}else if(y<130){actions.chooseDirection();}else{float trainY=direction==Commute.Direction.MORNING?273:170;if(y>trainY-22&&y<trainY+56){if(x<90)actions.previous();else if(x>310)actions.next();}}performClick();return true;}return true;
    }
    private boolean longPressed=false;private final Runnable longPress=this::openSettings;
    private void openSettings(){longPressed=true;actions.settings();}
    @Override public boolean performClick(){super.performClick();return true;}
}
