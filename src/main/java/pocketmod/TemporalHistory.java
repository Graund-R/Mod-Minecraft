package pocketmod;

import java.util.ArrayList;
import java.util.List;

/** Bounded cursor with a redo branch. Independent of Minecraft so branch/limit rules are testable. */
public final class TemporalHistory<T> {
    private final List<T> frames = new ArrayList<T>();
    private final int limit;
    private int cursor=-1;
    public TemporalHistory(int limit) { this.limit=Math.max(2,limit); }
    public T current() { return cursor<0 ? null : frames.get(cursor); }
    public T previous() { return cursor>0 ? frames.get(cursor-1) : null; }
    public T next() { return cursor+1<frames.size() ? frames.get(cursor+1) : null; }
    public int position() { return cursor; }
    public T at(int index) { return frames.get(index); }
    public T removeOldest() { if(frames.size()<2)throw new IllegalStateException(); cursor=Math.max(0,cursor-1);return frames.remove(0); }
    public int size() { return frames.size(); }
    public void back() { if (cursor>0) cursor--; }
    public void forward() { if (cursor+1<frames.size()) cursor++; }
    public void branch() { while(frames.size()>cursor+1) frames.remove(frames.size()-1); }
    public void add(T frame) {
        branch(); frames.add(frame); cursor++;
        if (frames.size()>limit) { frames.remove(0); cursor--; }
    }
    public void clear() { frames.clear();cursor=-1; }
}
