package kr.oganeungil.watch;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ApplicationProvider;
import kr.oganeungil.core.Commute;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchUiTest {
 static class Actions implements WatchFaceView.Actions {int refresh,previous,next,settings,choose;public void refresh(){refresh++;}public void previous(){previous++;}public void next(){next++;}public void settings(){settings++;}public void chooseDirection(){choose++;}}
 private WatchFaceView create(int pixels,Actions actions){Context context=ApplicationProvider.getApplicationContext();WatchFaceView view=new WatchFaceView(context,actions);view.layout(0,0,pixels,pixels);return view;}
 @Test public void renderBothModesAndRoundSizes()throws Exception{
  for(int size:new int[]{384,432,480})for(Commute.Direction direction:Commute.Direction.values()){
   WatchFaceView view=create(size,new Actions());view.direction=direction;
   String csv=direction==Commute.Direction.MORNING?"DAY,MORNING,D1,08:12,08:44":"DAY,EVENING,D2,18:42,19:14";
   Instant now=Instant.now();List<Repository.Bus> buses=direction==Commute.Direction.MORNING?List.of(new Repository.Bus("5001-1A","29275",0,List.of(now.plusSeconds(300),now.plusSeconds(1020)),""),new Repository.Bus("30","56342",0,List.of(now.plusSeconds(180)),"")):List.of(new Repository.Bus("5001-1B","23641",0,List.of(now.plusSeconds(300),now.plusSeconds(1020)),"3번째 차량 정보 미제공"));
   view.snapshot=new Repository.Snapshot(buses,Commute.parseCsv(csv),"DAY",true,"예시 데이터",now);view.trainIndex=0;
   Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
   assertEquals(Color.BLACK,image.getPixel(size/2,size/2));
   File folder=new File(System.getProperty("user.dir"),"build/ui-preview");folder.mkdirs();
   try(FileOutputStream out=new FileOutputStream(new File(folder,direction+"-"+size+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
  }
 }
 @Test public void sharedRefreshAndTransparentTrainNavigation(){
  Actions actions=new Actions();WatchFaceView view=create(400,actions);view.draw(new Canvas(Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)));
  tap(view,200,372);assertEquals(1,actions.refresh);tap(view,48,294);assertEquals(1,actions.previous);tap(view,350,294);assertEquals(1,actions.next);tap(view,200,75);assertEquals(1,actions.choose);
  view.direction=Commute.Direction.EVENING;tap(view,48,190);assertEquals(2,actions.previous);tap(view,200,372);assertEquals(2,actions.refresh);
 }
 @Test public void missingLiveDataDoesNotDrawDemoArrivals(){Context context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("commute",0).edit().clear().commit();Repository.Snapshot snapshot=new Repository(context, "").load(Commute.Direction.EVENING);assertFalse(snapshot.demo());assertTrue(snapshot.trains().isEmpty());assertTrue(snapshot.buses().get(0).arrivals().isEmpty());assertEquals("API 키 필요",snapshot.buses().get(0).notice());}
 private void tap(WatchFaceView view,int x,int y){long time=android.os.SystemClock.uptimeMillis();view.onTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0));view.onTouchEvent(MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,x,y,0));}
}
