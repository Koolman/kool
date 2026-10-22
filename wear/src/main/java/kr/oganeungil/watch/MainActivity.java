package kr.oganeungil.watch;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.Uri;
import android.widget.*;
import com.google.android.gms.wearable.*;
import kr.oganeungil.core.Commute;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements WatchFaceView.Actions {
    private WatchFaceView face;private Repository repo;private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();private Future<?> pending;
    private CancellationSignal fix;private int generation=0;private boolean active=false;private int locationGeneration=0;
    private final Runnable tick=new Runnable(){public void run(){if(active){face.invalidate();handler.postDelayed(this,1000);}}};
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if("config".equals(k))handler.post(()->{if(active)refresh();});};
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);repo=new Repository(this);face=new WatchFaceView(this,this);setContentView(face);getSharedPreferences("commute",0).registerOnSharedPreferenceChangeListener(listener);}
    @Override protected void onStart(){super.onStart();active=true;handler.post(tick);face.status="위치 확인 중";readSyncedConfig();startLocation();refresh();}
    @Override protected void onStop(){active=false;generation++;locationGeneration++;handler.removeCallbacksAndMessages(null);if(fix!=null)fix.cancel();if(pending!=null)pending.cancel(true);super.onStop();}
    @Override protected void onDestroy(){getSharedPreferences("commute",0).unregisterOnSharedPreferenceChangeListener(listener);network.shutdownNow();super.onDestroy();}
    private void readSyncedConfig(){Wearable.getDataClient(this).getDataItems().addOnSuccessListener(items->{try{for(DataItem item:items)if(ConfigListener.PATH.equals(item.getUri().getPath())){String raw=DataMapItem.fromDataItem(item).getDataMap().getString("json");if(raw!=null&&raw.length()<500_000){new JSONObject(raw);getSharedPreferences("commute",0).edit().putString("config",raw).apply();}}}catch(Exception ignored){}finally{items.release();}});}
    private void startLocation(){
        int token=++locationGeneration;
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},7);return;}
        JSONObject config=repo.config();Commute.Point home=Repository.point(config,"home"),work=Repository.point(config,"work");
        if(home==null||work==null){face.status="위치 미등록 · 시각 탭으로 출퇴근 선택";chooseDirection();return;}
        LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        String provider=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:null;
        if(provider==null){face.status="위치 꺼짐 · 시각 탭으로 선택";chooseDirection();return;}
        fix=new CancellationSignal();handler.postDelayed(()->{if(active&&token==locationGeneration){locationGeneration++;fix.cancel();face.status="위치 확인 시간 초과";chooseDirection();}},15000);
        try{manager.getCurrentLocation(provider,fix,getMainExecutor(),location->{if(!active||token!=locationGeneration)return;locationGeneration++;Commute.Direction direction=null;
            if(location!=null&&SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()<120_000_000_000L)direction=Commute.classify(new Commute.Point(location.getLatitude(),location.getLongitude()),location.getAccuracy(),home,work);
            if(direction==null){face.status="위치 불확실 · 직접 선택";chooseDirection();}else{face.direction=direction;face.status=direction==Commute.Direction.MORNING?"출근":"퇴근";refresh();}
        });}catch(SecurityException e){face.status="위치 권한 필요";chooseDirection();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==7&&active){if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation();else{face.status="위치 권한 없음 · 직접 선택";chooseDirection();}}}
    @Override public void refresh(){if(!active)return;if(pending!=null)pending.cancel(true);int token=++generation;face.loading=true;face.snapshot=null;face.trainIndex=-1;face.invalidate();Commute.Direction direction=face.direction;
        pending=network.submit(()->{Repository.Snapshot data=repo.load(direction);handler.post(()->{if(!active||generation!=token)return;face.snapshot=data;face.trainIndex=Commute.nextIndex(data.trains(),Commute.serviceMinute(ZonedDateTime.now(Commute.SEOUL)));face.loading=false;face.invalidate();updateDescription();});});}
    @Override public void previous(){if(face.snapshot==null||face.snapshot.trains().isEmpty())return;face.trainIndex=face.trainIndex<0?face.snapshot.trains().size()-1:Math.max(0,face.trainIndex-1);face.invalidate();updateDescription();}
    @Override public void next(){if(face.snapshot==null||face.snapshot.trains().isEmpty())return;if(face.trainIndex<0)return;face.trainIndex=Math.min(face.snapshot.trains().size()-1,face.trainIndex+1);face.invalidate();updateDescription();}
    private void updateDescription(){StringBuilder s=new StringBuilder(face.direction==Commute.Direction.MORNING?"출근. ":"퇴근. ");if(face.snapshot!=null){if(face.snapshot.demo())s.append("예시 데이터. ");if(face.trainIndex>=0){Commute.Train t=face.snapshot.trains().get(face.trainIndex);s.append("열차 ").append(Commute.clock(t.departure())).append("에서 ").append(Commute.clock(t.arrival())).append(". ");}for(Repository.Bus bus:face.snapshot.buses())s.append(bus.name()).append(" ").append(bus.notice()).append(". ");}s.append("상단 시각을 눌러 출퇴근 선택. 길게 눌러 설정. 하단 Refresh로 전체 갱신.");face.setContentDescription(s);}
    @Override public void chooseDirection(){if(!active||isFinishing())return;new AlertDialog.Builder(this).setTitle("출발지 선택").setItems(new String[]{"출근 · 집에서 회사로","퇴근 · 회사에서 집으로"},(d,i)->{locationGeneration++;if(fix!=null)fix.cancel();face.direction=i==0?Commute.Direction.MORNING:Commute.Direction.EVENING;face.status=i==0?"출근 · 직접 선택":"퇴근 · 직접 선택";refresh();}).setNegativeButton("닫기",null).show();}
    @Override public void settings(){
        new AlertDialog.Builder(this).setTitle("오가는 길").setItems(new String[]{"출근·퇴근 선택","현재 위치로 다시 판단","휴대폰 설정 동기화","시연 모드 켜기/끄기","사용 방법"},(d,i)->{
            if(i==0)chooseDirection();if(i==1)startLocation();if(i==2){readSyncedConfig();Toast.makeText(this,"휴대폰 설정 앱에서 워치로 보내기를 눌러주세요.",Toast.LENGTH_LONG).show();}
            if(i==3){try{JSONObject config=repo.config();config.put("demo",!config.optBoolean("demo",false));getSharedPreferences("commute",0).edit().putString("config",config.toString()).apply();}catch(Exception ignored){}}
            if(i==4)new AlertDialog.Builder(this).setMessage("시각 탭: 출근/퇴근 선택\n좌우 화살표: 이전/다음 열차\nRefresh: 현재 시각 기준으로 열차·버스 전체 갱신\n길게 누르기: 설정\n휴대폰 설정 앱에서 API 키·위치·검증된 시간표를 보내주세요. 공휴일은 휴일 운행일 선택이 필요합니다.\n3번째 버스는 공식 API 미제공 시 — 표시").setPositiveButton("확인",null).show();
        }).setNegativeButton("닫기",null).show();
    }
}
