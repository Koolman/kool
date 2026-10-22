package kr.oganeungil.watch;
import androidx.wear.tiles.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Tile only launches the app. Tile rendering never accesses location or transit APIs. */
public class CommuteTileService extends TileService {
 @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request){
  ActionBuilders.LaunchAction launch=new ActionBuilders.LaunchAction.Builder().setAndroidActivity(new ActionBuilders.AndroidActivity.Builder().setPackageName(getPackageName()).setClassName(MainActivity.class.getName()).build()).build();
  ModifiersBuilders.Clickable click=new ModifiersBuilders.Clickable.Builder().setId("open_commute").setOnClick(launch).build();
  LayoutElementBuilders.Column column=new LayoutElementBuilders.Column.Builder()
    .addContent(new LayoutElementBuilders.Text.Builder().setText("오가는 길").setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(23)).setColor(ColorBuilders.argb(0xFFFFFFFF)).build()).build())
    .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(14)).build())
    .addContent(new LayoutElementBuilders.Text.Builder().setText("눌러서 출퇴근 확인").setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14)).setColor(ColorBuilders.argb(0xFFCCCCCC)).build()).build()).build();
  LayoutElementBuilders.Box root=new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand()).setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(click).setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription("오가는 길 실행. 실행할 때 위치를 확인합니다.").build()).build()).addContent(column).build();
  TileBuilders.Tile tile=new TileBuilders.Tile.Builder().setResourcesVersion("1").setTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder().setLayout(new LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build()).build()).build();
  return Futures.immediateFuture(tile);
 }
 @Override protected ListenableFuture<ResourceBuilders.Resources> onResourcesRequest(RequestBuilders.ResourcesRequest request){return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion("1").build());}
}
