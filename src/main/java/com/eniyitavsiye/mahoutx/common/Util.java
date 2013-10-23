/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 *
 * @author ceyhun
 */
public class Util {

  public static Object[] asArray(Object... arr) { return arr; }

  public static int[] getIntArray(String text) {
	  String[] arr = text.split(",");
    int[] result = new int[arr.length];
    for (int i = 0; i < arr.length; ++i) {
      result[i] = Integer.parseInt(arr[i]);
    }
    return result;
  }
  
  public static List<Integer> getRandomNumbers(int size, int range){
     if (size > range)
         size = range;
     List<Integer> arr = new ArrayList<>();
     for (int i = 0; i<range; i++){
         arr.add(i);
     }
     shuffle(arr);
     return arr.subList(0, size);
  }
  
  public static void shuffle(List<Integer> a) {
    Random rnd = new Random();
    int mid = a.size() / 2;
    for (int i = mid; i < a.size(); i++) {
        int lo = rnd.nextInt(mid);
        int buffer = a.get(lo);
        a.set(lo, i);
        a.set(i, buffer);
    }
}
	
}
