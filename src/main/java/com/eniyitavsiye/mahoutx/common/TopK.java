package com.eniyitavsiye.mahoutx.common;

import org.apache.lucene.util.PriorityQueue;

import java.util.Arrays;
import java.util.List;

public abstract class TopK<T> extends PriorityQueue<T> {

    public TopK(int maxSize) {
        super(maxSize);
    }

    /**
     * Fills an array with the elements removed from this queue in increasing
     * order.
     *
     * Note elements are removed from this queue!
     *
     * @return Elements of this queue in ascending order.
     */
    @SuppressWarnings("unchecked")
    public T[] getElems() {
        //unsafe cast, left unchecked.
        T[] elems = (T[]) new Object[size()];
        for (int i = 0; i < size(); ++i) {
            elems[i] = pop();
        }
        return elems;
    }

    /**
     * Just like {@link #getElems()}, fills a list by removing from this list in
     * increasing order.
     *
     * Note elements are removed from this queue!
     *
     * @return List that contains elements of this queue in ascending order.
     */
    public List<T> getElemsAsList() {
        return Arrays.asList(getElems());
    }

}

