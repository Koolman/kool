package kr.oganeungil.watch;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ApiConfigTest {
 @Before public void clear(){Context context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("commute",0).edit().clear().commit();}
 @Test public void updateAppliesPersonalKeyAndExitsOldDemo() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("commute",0).edit().putString("config","{\"busKey\":\"\",\"demo\":true,\"home\":{\"lat\":37.3,\"lng\":127.1}}").commit();
  JSONObject config=new Repository(context,"test-build-key").config();assertEquals("test-build-key",config.getString("busKey"));assertFalse(config.getBoolean("demo"));assertTrue(config.has("home"));assertEquals(2,config.getInt("apiBuildVersion"));
 }
 @Test public void explicitKeyAndNewDemoChoiceArePreserved() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("commute",0).edit().putString("config","{\"busKey\":\"own-key\",\"demo\":true,\"apiBuildVersion\":2}").commit();
  JSONObject config=new Repository(context,"test-build-key").config();assertEquals("own-key",config.getString("busKey"));assertTrue(config.getBoolean("demo"));
 }
 @Test public void officialHeaderValidationDistinguishesAuthQuotaAndNoResults()throws Exception {
  Repository.validateHeader(new JSONObject("{\"msgHeader\":{\"resultCode\":0}}"));
  Repository.validateHeader(new JSONObject("{\"msgHeader\":{\"resultCode\":4}}"));
  try{Repository.validateHeader(new JSONObject("{\"msgHeader\":{\"resultCode\":30}}"));fail();}catch(Repository.ApiException e){assertTrue(e.getMessage().contains("인증키"));}
  try{Repository.validateHeader(new JSONObject("{\"msgHeader\":{\"resultCode\":22}}"));fail();}catch(Repository.ApiException e){assertTrue(e.getMessage().contains("한도"));}
 }
}
