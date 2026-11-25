package pocketmod.temporal;
import pocketmod.TemporalHistory;
public final class TemporalHistorySmoke {
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        TemporalHistory<Integer> h=new TemporalHistory<Integer>(3);
        check(h.current()==null && h.previous()==null && h.next()==null,"empty");
        h.add(1);h.add(2);h.add(3);h.add(4);
        check(h.size()==3 && h.current()==4 && h.at(0)==2,"bounded history");
        h.back();check(h.current()==3 && h.next()==4,"backward cursor retains future");
        h.back();h.back();check(h.current()==2,"lower bound");
        h.forward();h.forward();h.forward();check(h.current()==4,"upper bound");
        h.back();h.add(8);check(h.next()==null && h.current()==8 && h.at(1)==3,"new action replaces future");
        h.back();h.branch();check(h.size()==2 && h.current()==3,"explicit branch");
        check(h.removeOldest()==2 && h.size()==1 && h.current()==3,"memory trim keeps current");
        h.clear();check(h.position()==-1 && h.current()==null,"world cleanup");
        System.out.println("Temporal history: passed");
    }
}
