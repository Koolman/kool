package kr.oganeungil.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure domain logic. No generated timetable entries are ever treated as live data. */
public final class Commute {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public enum Direction { MORNING, EVENING }
    public record Point(double latitude, double longitude) {
        public Point { if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) throw new IllegalArgumentException("잘못된 좌표"); }
    }
    public record Train(String id, String day, Direction direction, int departure, int arrival) {
        public Train { if (id.isBlank() || !Set.of("DAY","SAT","END").contains(day) || departure<0 || arrival<departure || arrival>1800) throw new IllegalArgumentException("시간표 값 확인 필요"); }
    }
    public static double distance(Point a, Point b) {
        double p=Math.toRadians(b.latitude-a.latitude),q=Math.toRadians(b.longitude-a.longitude);
        double z=Math.sin(p/2)*Math.sin(p/2)+Math.cos(Math.toRadians(a.latitude))*Math.cos(Math.toRadians(b.latitude))*Math.sin(q/2)*Math.sin(q/2);
        return 6371000*2*Math.atan2(Math.sqrt(z),Math.sqrt(Math.max(0,1-z)));
    }
    /** Null is intentional: never guess direction from time of day or an ambiguous fix. */
    public static Direction classify(Point position, double accuracy, Point home, Point work) {
        if (home==null || work==null || accuracy<0 || accuracy>300 || !Double.isFinite(accuracy)) return null;
        double h=distance(position,home),w=distance(position,work);
        if (Math.min(h,w)>1500 || Math.abs(h-w)<Math.max(150,accuracy*2)) return null;
        return h<w?Direction.MORNING:Direction.EVENING;
    }
    public static int minute(String value) {
        if (!value.matches("(?:[01]\\d|2\\d):[0-5]\\d(?::[0-5]\\d)?")) throw new IllegalArgumentException("시각은 HH:MM 형식으로 입력해주세요 (00~29시)");
        return Integer.parseInt(value.substring(0,2))*60+Integer.parseInt(value.substring(3,5));
    }
    public static String clock(int minute) { return String.format(Locale.ROOT,"%02d:%02d",Math.floorMod(minute,1440)/60,Math.floorMod(minute,60)); }
    public static String arrivalClock(Instant arrival) { return arrival.atZone(SEOUL).format(DateTimeFormatter.ofPattern("HH:mm")); }
    public static int remaining(Instant arrival, Instant now) { return (int)Math.max(0,Math.ceil(Duration.between(now,arrival).toMillis()/60000.0)); }
    public static LocalDate serviceDate(ZonedDateTime now) { return now.getHour()<3?now.toLocalDate().minusDays(1):now.toLocalDate(); }
    public static int serviceMinute(ZonedDateTime now) { return now.getHour()*60+now.getMinute()+(now.getHour()<3?1440:0); }
    public static String day(LocalDate date, String override, Set<LocalDate> holidays) {
        if(Set.of("DAY","SAT","END").contains(override)) return override;
        if(holidays.contains(date)||date.getDayOfWeek()==DayOfWeek.SUNDAY) return "END";
        return date.getDayOfWeek()==DayOfWeek.SATURDAY?"SAT":"DAY";
    }
    public static List<Train> select(List<Train> all, Direction direction, String day) {
        return all.stream().filter(t->t.direction==direction&&t.day.equals(day)).sorted(Comparator.comparingInt(Train::departure)).collect(java.util.stream.Collectors.toList());
    }
    public static int nextIndex(List<Train> trains,int minute) {
        for(int i=0;i<trains.size();i++) if(trains.get(i).departure>=minute) return i;
        return -1;
    }
    /** Normalized CSV: day,direction,train_id,departure_arrival,destination_arrival. Same train at both stations. */
    public static List<Train> parseCsv(String text) {
        List<Train> out=new ArrayList<>(); Set<String> ids=new HashSet<>(); int line=0;
        for(String row:text.replace("\uFEFF", "").split("\\R")) {
            line++; row=row.trim(); if(row.isEmpty()||row.startsWith("#")||row.startsWith("day,"))continue;
            String[] f=row.split(",",-1);
            if(f.length!=5) throw new IllegalArgumentException(line+"행: 5개 열이 필요합니다");
            for(int i=0;i<f.length;i++)f[i]=f[i].trim();
            try {
                Train train=new Train(f[2],f[0],Direction.valueOf(f[1]),minute(f[3]),minute(f[4]));
                if(!ids.add(train.day+train.direction+train.id))throw new IllegalArgumentException("열차번호 중복");
                out.add(train);
            } catch(RuntimeException e){throw new IllegalArgumentException(line+"행: "+e.getMessage());}
        }
        return List.copyOf(out);
    }
}
