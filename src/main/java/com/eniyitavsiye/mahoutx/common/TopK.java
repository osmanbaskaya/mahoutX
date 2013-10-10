package com.eniyitavsiye.mahoutx.common;

import java.util.ArrayList;
import org.apache.lucene.util.PriorityQueue;

import java.util.Arrays;
import java.util.List;

public abstract class TopK<T> extends PriorityQueue<T> {

    public TopK(int maxSize) {
        super(maxSize);
    }

    /**
     * Fills a list with the elements removed from this queue in increasing
     * order.
     *
     * Note elements are removed from this queue! 0
     *
     *
     * @return Elements of this queue in ascending order.
     */
    public List<T> getElems() {
        List<T> elems = new ArrayList<T>();
        int size = size();
        for (int i = 0; i < size; ++i) {
            elems.add(pop());
        }
        return elems;
    }
}
