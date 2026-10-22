package kr.oganeungil.watch;

import android.content.*;
import org.json.*;
import kr.oganeungil.core.Commute;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

final class Repository {
    record Bus(String name,String stop,int color,List<Instant> arrivals,String notice) {}
    record Snapshot(List<Bus> buses,List<Commute.Train> trains,String day,boolean demo,String notice,Instant fetched) {}
    private final Context context;
    private final String defaultApiKey;
    Repository(Context context){this(context, BuildConfig.BUS_API_KEY);}
    Repository(Context context,String defaultApiKey){this.context=context;this.defaultApiKey=defaultApiKey;}

    JSONObject config(){
        JSONObject value;
        try{value=new JSONObject(context.getSharedPreferences("commute",0).getString("config","{}"));}catch(JSONException e){value=new JSONObject();}
        try{
            if(value.optString("busKey", "").isBlank()&&!defaultApiKey.isBlank())value.put("busKey",defaultApiKey);
            if(value.optInt("apiBuildVersion",0)<2&&!defaultApiKey.isBlank()){value.put("demo",false);value.put("apiBuildVersion",2);}
        }catch(JSONException ignored){}
        return value;
    }
    static Commute.Point point(JSONObject config,String key){try{JSONObject p=config.getJSONObject(key);return new Commute.Point(p.getDouble("lat"),p.getDouble("lng"));}catch(Exception e){return null;}}
    Snapshot load(Commute.Direction direction) {
        JSONObject config=config();Instant now=Instant.now(); boolean demo=config.optBoolean("demo",false);
        ZonedDateTime z=now.atZone(Commute.SEOUL);Set<LocalDate> holidays=new HashSet<>();
        for(String s:config.optString("holidays","").split("[\\s,]+"))try{holidays.add(LocalDate.parse(s));}catch(Exception ignored){}
        String day=Commute.day(Commute.serviceDate(z),config.optString("day","AUTO"),holidays);
        List<Bus> buses=new ArrayList<>();
        if(direction==Commute.Direction.MORNING){buses.add(bus(config,"5001-1A","29275",0xFFFF9D00,2,now,demo));buses.add(bus(config,"30","56342",0xFFA3D83D,1,now,demo));}
        else buses.add(bus(config,"5001-1B","23641",0xFFFF9D00,3,now,demo));
        List<Commute.Train> trains=List.of();String notice="";
        if(demo){
            List<Commute.Train> entries=new ArrayList<>();int base=Commute.serviceMinute(z)+3;
            for(int i=-2;i<5;i++)if(base+i*12>=0&&base+i*12+32<=1800)entries.add(new Commute.Train("DEMO"+i,day,direction,base+i*12,base+i*12+32));
            trains=entries;notice="예시 데이터";
        }else{
            try{
                String raw=config.optString("timetable","");
                if(!raw.isBlank()){
                    LocalDate valid=LocalDate.parse(config.optString("validUntil",""));
                    if(valid.isBefore(Commute.serviceDate(z)))notice="시간표 유효기간 만료";
                    else if(config.optString("source","").isBlank())notice="시간표 출처 미등록";
                    else trains=Commute.select(Commute.parseCsv(raw),direction,day);
                }else notice="시간표 등록 필요";
            }catch(Exception e){notice="시간표 설정 확인 필요";}
        }
        return new Snapshot(List.copyOf(buses),List.copyOf(trains),day,demo,notice,now);
    }
    private Bus bus(JSONObject config,String route,String stop,int color,int count,Instant now,boolean demo){
        if(demo){int[] offsets=route.equals("30")?new int[]{3}:new int[]{5,17,29};List<Instant> times=new ArrayList<>();for(int i=0;i<Math.min(count,offsets.length);i++)times.add(now.plusSeconds(offsets[i]*60L));return new Bus(route,stop,color,times,"");}
        if(config.optString("busKey","").isBlank())return new Bus(route,stop,color,List.of(),"API 키 필요");
        try{
            String key=config.getString("busKey");String stationId=config.optString("station_"+stop,context.getSharedPreferences("stationIds",0).getString(stop,""));
            if(stationId.isBlank()){
                JSONObject data=get("busstationservice/v2/getBusStationListv2",key,"keyword",stop);
                JSONArray list=array(data.optJSONObject("msgBody"),"busStationList");
                List<String> matches=new ArrayList<>();
                for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);if(normalize(item.optString("mobileNo")).equals(normalize(stop)))matches.add(item.getString("stationId"));}
                if(matches.size()!=1)throw new IOException("정류소 ID 확인 필요");stationId=matches.get(0);
                context.getSharedPreferences("stationIds",0).edit().putString(stop,stationId).apply();
            }
            JSONObject data=get("busarrivalservice/v2/getBusArrivalListv2",key,"stationId",stationId);
            JSONArray list=array(data.optJSONObject("msgBody"),"busArrivalList");
            JSONObject target=null;String override=config.optString("route_"+route,"");
            for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);boolean match=!override.isBlank()?override.equals(item.optString("routeId")):route.equals(item.optString("routeName"));if(match){if(target!=null)throw new IOException("노선 ID 확인 필요");target=item;}}
            if(target==null)return new Bus(route,stop,color,List.of(),"해당 노선 도착정보 없음");
            List<Instant> times=new ArrayList<>();
            for(int i=1;i<=Math.min(count,2);i++){
                String value=target.optString("predictTime"+i,"");
                if(value.matches("\\d+")&&Integer.parseInt(value)>0)times.add(Instant.now().plusSeconds(Long.parseLong(value)*60));
                else if(value.equals("0")&&target.optInt("stateCd"+i,-1)==1)times.add(Instant.now());
            }
            return new Bus(route,stop,color,List.copyOf(times),count>2?"3번째 차량 정보 미제공":"");
        }catch(ApiException e){return new Bus(route,stop,color,List.of(),e.getMessage());}
        catch(java.net.SocketTimeoutException e){return new Bus(route,stop,color,List.of(),"연결 시간 초과 · 다시 갱신");}
        catch(Exception e){return new Bus(route,stop,color,List.of(),"조회 실패 · 연결 확인");}
    }
    private String normalize(String s){return s.replaceFirst("^0+(?!$)","");}
    static JSONArray array(JSONObject object,String name)throws JSONException{if(object==null)return new JSONArray();Object val=object.opt(name);if(val instanceof JSONArray)return (JSONArray)val;if(val instanceof JSONObject)return new JSONArray().put(val);return new JSONArray();}
    static final class ApiException extends IOException { ApiException(String message){super(message);} }
    static void validateHeader(JSONObject response)throws Exception {
        JSONObject header=response.optJSONObject("msgHeader");
        if(header==null)throw new ApiException("API 응답 형식 오류");
        String code=header.optString("resultCode");
        if(Set.of("0","00","4").contains(code))return;
        if(Set.of("20","30","31","32").contains(code))throw new ApiException("인증키 승인 상태 확인");
        if(code.equals("22"))throw new ApiException("API 호출 한도 초과");
        throw new ApiException("API 응답 오류 ("+code.replaceAll("[^0-9]","")+")");
    }
    private JSONObject get(String service,String key,String param,String value)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        URL url=new URL("https://apis.data.go.kr/6410000/"+service+"?format=json&serviceKey="+URLEncoder.encode(key,"UTF-8")+"&"+param+"="+URLEncoder.encode(value,"UTF-8"));
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
        try{
            int status=conn.getResponseCode();
            if(status==429)throw new ApiException("API 호출 한도 초과");
            if(status==401||status==403)throw new ApiException("인증키 승인 상태 확인");
            if(status>=500)throw new ApiException("API 일시 장애 · 다시 갱신");
            if(status!=200)throw new ApiException("API 연결 오류 ("+status+")");
            try(InputStream in=conn.getInputStream()){
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(buffer.size()+n>2_000_000)throw new IOException("응답 크기 초과");buffer.write(chunk,0,n);}byte[] bytes=buffer.toByteArray();
                JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONObject response=envelope.optJSONObject("response");if(response==null)response=envelope;
                validateHeader(response);
                return response;
            }
        }finally{conn.disconnect();}
    }
}
