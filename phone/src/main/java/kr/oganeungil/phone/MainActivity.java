package kr.oganeungil.phone;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.text.InputType;
import android.widget.*;
import com.google.android.gms.wearable.*;
import kr.oganeungil.core.Commute;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Personal configuration companion. Bundled personal key comes from ignored build input; never log it. */
public class MainActivity extends Activity {
    private JSONObject config;private LinearLayout column;private TextView status,anchors;private Switch demo;
    private final Map<String,EditText> fields=new LinkedHashMap<>();private Spinner day;private String pendingAnchor;
    private CancellationSignal fix;
    @Override public void onCreate(Bundle bundle){super.onCreate(bundle);try{config=new JSONObject(getPreferences(0).getString("config","{}"));}catch(Exception e){config=new JSONObject();}
        try{
            if(config.optString("busKey","").isBlank()&&!BuildConfig.BUS_API_KEY.isBlank())config.put("busKey",BuildConfig.BUS_API_KEY);
            if(config.optInt("apiBuildVersion",0)<2&&!BuildConfig.BUS_API_KEY.isBlank()){config.put("demo",false);config.put("apiBuildVersion",2);}
        }catch(JSONException ignored){}
        build();}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFF111315);column=new LinearLayout(this);column.setOrientation(1);int padding=(int)(24*getResources().getDisplayMetrics().density);column.setPadding(padding,padding,padding,padding);scroll.addView(column);setContentView(scroll);
        text("오가는 길",30,Color.WHITE);text("Galaxy Watch 설정",16,0xFFCCCCCC);text("워치에서 앱을 열 때만 위치를 확인합니다. 설정을 저장한 뒤 ‘워치로 보내기’를 눌러주세요.",14,0xFFBBBBBB);
        demo=new Switch(this);demo.setText("시연 모드 · 모든 교통 정보가 예시");demo.setTextColor(Color.WHITE);demo.setChecked(config.optBoolean("demo",false));column.addView(demo);
        button("집 위치를 현재 위치로 등록",()->anchor("home"));button("회사 위치를 현재 위치로 등록",()->anchor("work"));anchors=text(anchorSummary(),13,0xFFBBBBBB);
        text("퇴근: 마곡나루 → 신논현 · 5001-1B / 23641\n출근: 5001-1A / 29275, 30 / 56342 → 신논현 → 마곡나루\n빠른하차 출근 2-1·4-4 / 퇴근 1-1·4-4",14,0xFFDDDDDD);
        field("busKey","경기버스 API 일반 인증키 (Decoding)","발급받은 키를 입력하세요",false,true);
        button("API 키 발급 안내 열기",()->open("https://www.gbis.go.kr/gbis2014/publicService.action?cmd=publickKey"));
        text("키를 저장한 뒤 ‘워치로 보내기’를 눌러주세요.",13,0xFFCCCCCC);
        text("운행일",16,Color.WHITE);day=new Spinner(this);String[] values={"자동 · 주말 판별 (공휴일 별도 등록)","평일","토요일","일요일·공휴일"};day.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));String[] ids={"AUTO","DAY","SAT","END"};for(int i=0;i<ids.length;i++)if(ids[i].equals(config.optString("day","AUTO")))day.setSelection(i);column.addView(day);
        field("holidays","공휴일 목록","예: 2026-12-25, 2027-01-01",true,false);
        field("source","급행 시간표 출처","확인한 운영기관 자료 URL 및 시행일",false,false);
        field("validUntil","시간표 사용 만료일","YYYY-MM-DD",false,false);
        field("timetable","두 역의 동일 급행 열차 시각표 CSV","day,direction,train_id,departure_arrival,destination_arrival\nDAY,MORNING,열차번호,08:12,08:44",true,false);
        text("위 입력 힌트는 형식 예시입니다. 실제 운행 시각이 아닙니다. 실제 급행만 동일 열차번호로 두 역을 연결한 CSV를 가져오세요. MORNING은 신논현→마곡나루, EVENING은 반대입니다. DAY/SAT/END를 구분합니다.",12,0xFFBBBBBB);
        button("시간표 CSV 파일 가져오기",()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,8);});
        button("9호선 공식 시간표 열기",()->open("https://www.metro9.co.kr/prog/subwayTm/kor/sub01_02/list.do"));
        text("고급 설정 · 정류소/노선 자동 검색이 실패할 때만 입력",15,Color.WHITE);
        for(String stop:new String[]{"29275","56342","23641"})field("station_"+stop,stop+" 정류소 내부 ID (선택)","5자리 정류소 번호와 다른 내부 stationId",false,false);
        for(String route:new String[]{"5001-1A","30","5001-1B"})field("route_"+route,route+" 노선 내부 ID (선택)","routeId · 같은 이름 노선 구분용",false,false);
        button("이 기기에 저장",()->{if(save())message("설정을 저장했습니다.");});
        button("워치로 보내기",this::send);
        status=text("설정은 앱 내부에만 저장됩니다. API 키·정확한 위치를 화면 공유나 채팅으로 보내지 마세요. 워치·휴대폰 APK는 같은 서명으로 설치해야 합니다.",13,0xFFBBBBBB);
    }
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextColor(color);view.setTextSize(size);view.setPadding(0,10,0,10);column.addView(view);return view;}
    private void button(String value,Runnable action){Button button=new Button(this);button.setText(value);button.setAllCaps(false);button.setOnClickListener(v->action.run());column.addView(button);}
    private void field(String key,String label,String hint,boolean multiline,boolean secret){text(label,14,0xFFDDDDDD);EditText view=new EditText(this);view.setTextColor(Color.WHITE);view.setHintTextColor(0xFF888888);view.setHint(hint);view.setText(config.optString(key,""));view.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:multiline?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_CLASS_TEXT);if(multiline){view.setMinLines(key.equals("timetable")?4:2);view.setMaxLines(8);}column.addView(view);fields.put(key,view);}
    private String anchorSummary(){return "집 "+(config.has("home")?"등록됨":"미등록")+" · 회사 "+(config.has("work")?"등록됨":"미등록");}
    private void anchor(String name){pendingAnchor=name;if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},9);return;}LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);String provider=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:null;if(provider==null){message("휴대폰 위치를 켜주세요.");return;}
        if(fix!=null)fix.cancel();fix=new CancellationSignal();CancellationSignal current=fix;message("현재 위치 확인 중…");new Handler(getMainLooper()).postDelayed(()->{if(fix==current){current.cancel();fix=null;message("위치 확인 시간이 초과되었습니다. 다시 시도해주세요.");}},20000);
        try{manager.getCurrentLocation(provider,current,getMainExecutor(),location->{if(fix!=current)return;fix=null;if(location==null||location.getAccuracy()>300||SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()>120_000_000_000L){message("정확한 현재 위치를 확인하지 못했습니다.");return;}try{config.put(name,new JSONObject().put("lat",location.getLatitude()).put("lng",location.getLongitude()));anchors.setText(anchorSummary());message((name.equals("home")?"집":"회사")+" 위치를 확인했습니다. 저장하고 워치로 보내주세요.");}catch(Exception e){message("위치 저장 실패");}});}catch(SecurityException e){message("위치 권한을 허용해주세요.");}}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] g){super.onRequestPermissionsResult(request,p,g);if(request==9&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)anchor(pendingAnchor);}
    private boolean save(){try{
        JSONObject next=new JSONObject(config.toString());for(var entry:fields.entrySet())next.put(entry.getKey(),entry.getValue().getText().toString().trim());
        next.put("demo",demo.isChecked());next.put("day",new String[]{"AUTO","DAY","SAT","END"}[day.getSelectedItemPosition()]);
        String csv=next.optString("timetable","");if(!csv.isBlank()){Commute.parseCsv(csv);LocalDate.parse(next.getString("validUntil"));if(next.getString("source").isBlank())throw new IllegalArgumentException("시간표 출처를 입력해주세요.");}
        for(String date:next.optString("holidays","").split("[\\s,]+"))if(!date.isBlank())LocalDate.parse(date);
        if(next.toString().length()>500_000)throw new IllegalArgumentException("설정 크기가 너무 큽니다.");
        config=next;getPreferences(0).edit().putString("config",config.toString()).apply();return true;
    }catch(Exception e){message("저장하지 못했습니다: "+e.getMessage());return false;}}
    private void send(){if(!save())return;try{PutDataMapRequest request=PutDataMapRequest.create("/commute/config");request.getDataMap().putString("json",config.toString());request.getDataMap().putLong("revision",System.currentTimeMillis());Wearable.getDataClient(this).putDataItem(request.asPutDataRequest().setUrgent()).addOnSuccessListener(item->message("전송을 등록했습니다. 워치가 연결되면 적용됩니다. 워치 앱을 다시 열어 확인해주세요.")).addOnFailureListener(e->message("전송 실패. Galaxy Wearable 연결과 두 앱의 서명을 확인해주세요."));}catch(Exception e){message("전송 실패");}}
    private void message(String value){if(status!=null)status.setText(value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){message("브라우저로 열 수 없습니다.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==8&&result==RESULT_OK&&data!=null){try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>400_000)throw new IOException("400KB 이하 파일만 지원");out.write(buffer,0,n);}String csv=out.toString("UTF-8");Commute.parseCsv(csv);fields.get("timetable").setText(csv);message("시간표를 가져왔습니다. 출처·만료일을 입력 후 저장해주세요.");}catch(Exception e){message("가져오기 실패: "+e.getMessage());}}}
    @Override protected void onDestroy(){if(fix!=null)fix.cancel();super.onDestroy();}
}
