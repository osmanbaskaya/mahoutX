/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

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
	
}
