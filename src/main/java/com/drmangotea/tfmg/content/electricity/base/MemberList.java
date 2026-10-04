package com.drmangotea.tfmg.content.electricity.base;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * The members of an electrical network, in insertion order, with an index by
 * block position. Lookups by position used to be linear scans of the list,
 * done once per added member during a flood fill and once per member every
 * lazy tick, which made a grid of n blocks cost O(n^2) to load.
 *
 * Every mutation the code base uses goes through the overrides below, so the
 * index cannot drift from the list.
 *
 * @author vyrriox
 */
public class MemberList extends ArrayList<IElectric> {

    private final Long2ObjectOpenHashMap<IElectric> byPos = new Long2ObjectOpenHashMap<>();

    /** The member registered at this block position, or null. */
    public IElectric at(long pos) {
        return byPos.get(pos);
    }

    /** True when this very instance is a member (not just its position). */
    public boolean containsInstance(IElectric electric) {
        return electric != null && byPos.get(electric.getPos()) == electric;
    }

    @Override
    public boolean add(IElectric electric) {
        boolean added = super.add(electric);
        index(electric);
        return added;
    }

    @Override
    public void add(int index, IElectric electric) {
        super.add(index, electric);
        index(electric);
    }

    @Override
    public boolean addAll(Collection<? extends IElectric> c) {
        for (IElectric electric : c)
            add(electric);
        return !c.isEmpty();
    }

    @Override
    public IElectric set(int index, IElectric electric) {
        IElectric old = super.set(index, electric);
        // The usual case swaps a dead instance for the live one at the same
        // position (chunk reload): update that slot instead of rebuilding.
        if (old != null && old.getPos() == electric.getPos())
            byPos.put(electric.getPos(), electric);
        else
            reindex();
        return old;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);
        if (removed)
            reindex();
        return removed;
    }

    @Override
    public IElectric remove(int index) {
        IElectric removed = super.remove(index);
        reindex();
        return removed;
    }

    @Override
    public boolean removeIf(Predicate<? super IElectric> filter) {
        boolean removed = super.removeIf(filter);
        if (removed)
            reindex();
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean removed = super.removeAll(c);
        if (removed)
            reindex();
        return removed;
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof IElectric electric))
            return false;
        IElectric indexed = byPos.get(electric.getPos());
        if (indexed == electric)
            return true;
        // Another instance holds this position (a dead block entity left from
        // before a reload): fall back to the list itself for this rare case.
        return indexed != null && super.contains(o);
    }

    @Override
    public void clear() {
        super.clear();
        byPos.clear();
    }

    private void reindex() {
        byPos.clear();
        for (IElectric electric : this)
            index(electric);
    }

    /** A live instance always wins the position over a stale one. */
    private void index(IElectric electric) {
        IElectric current = byPos.get(electric.getPos());
        if (current == null || (current != electric && ElectricNetworkManager.isStale(current)))
            byPos.put(electric.getPos(), electric);
    }
}
