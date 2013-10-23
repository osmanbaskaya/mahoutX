package com.eniyitavsiye.mahoutx.common;

import java.util.ArrayList;
import org.apache.lucene.util.PriorityQueue;
import com.eniyitavsiye.mahoutx.common.Util;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

    //get s elements from the queue randomly
    public List<T> getRandomElems(int s) {
        List<T> elems = new ArrayList<T>();
        List<Integer> randNumbers = Util.getRandomNumbers(s, size());
        Collections.sort(randNumbers);
        int k = 0;
        int i = 0;
        while (k < s){
            if (i == randNumbers.get(k)) {
                elems.add(pop());
                k++;
            }
            else{
                pop();
            }
            i++;
        }
        return elems;
    }
}
