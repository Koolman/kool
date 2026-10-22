package kr.oganeungil.watch;
import com.google.android.gms.wearable.*;
import org.json.JSONObject;
public class ConfigListener extends WearableListenerService {
 public static final String PATH="/commute/config";
 @Override public void onDataChanged(DataEventBuffer events){for(DataEvent event:events)if(event.getType()==DataEvent.TYPE_CHANGED&&PATH.equals(event.getDataItem().getUri().getPath()))store(event.getDataItem());}
 void store(DataItem item){try{String raw=DataMapItem.fromDataItem(item).getDataMap().getString("json");if(raw==null||raw.length()>500_000)return;new JSONObject(raw);getSharedPreferences("commute",0).edit().putString("config",raw).apply();}catch(Exception ignored){}}
}
