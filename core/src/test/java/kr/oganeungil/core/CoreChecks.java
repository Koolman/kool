package kr.oganeungil.core;
import java.time.*;
import java.util.*;
public class CoreChecks {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static void main(String[] args){
  var home=new Commute.Point(37.3,127.1);var work=new Commute.Point(37.566,126.827);
  check(Commute.classify(home,20,home,work)==Commute.Direction.MORNING,"home");
  check(Commute.classify(work,20,home,work)==Commute.Direction.EVENING,"work");
  check(Commute.classify(home,500,home,work)==null,"uncertain fix");
  check(Commute.classify(home,20,null,work)==null,"missing home");
  check(Commute.classify(home,20,home,home)==null,"overlapping anchors");
  check(Commute.classify(new Commute.Point(35,128),20,home,work)==null,"away from anchors");
  var now=ZonedDateTime.of(2026,10,22,0,30,0,0,Commute.SEOUL);
  check(Commute.serviceDate(now).equals(LocalDate.of(2026,10,21)),"service date midnight");
  check(Commute.serviceMinute(now)==1470,"extended midnight time");
  check(Commute.clock(1501).equals("01:01"),"24+ clock");
  var list=Commute.parseCsv("DAY,MORNING,T1,08:00,08:32\nDAY,MORNING,T2,08:12,08:44\nDAY,EVENING,T3,18:42,19:14");
  var morning=Commute.select(list,Commute.Direction.MORNING,"DAY");
  check(morning.size()==2&&Commute.nextIndex(morning,481)==1,"next train and direction filter");
  check(Commute.nextIndex(morning,600)==-1,"service ended");
  check(Commute.day(LocalDate.of(2026,10,21),"AUTO",Set.of(LocalDate.of(2026,10,21))).equals("END"),"holiday");
  check(Commute.day(LocalDate.of(2026,10,24),"AUTO",Set.of()).equals("SAT"),"Saturday");
  check(Commute.arrivalClock(Instant.parse("2026-10-21T15:05:00Z")).equals("00:05"),"bus arrival date rollover");
  boolean failed=false;try{Commute.parseCsv("DAY,MORNING,A,08:44,08:12");}catch(IllegalArgumentException e){failed=true;}check(failed,"reject reversed times");
  check(Commute.remaining(Instant.ofEpochSecond(120),Instant.ofEpochSecond(61))==1,"remaining round up");
  System.out.println("PASS: 15 domain checks");
 }
}
