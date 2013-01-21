/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

/**
 *
 * @author ceyhun
 */
public interface UserItemIDIndexMapFunction {

	public Integer userIndex(long id);
	public Integer itemIndex(long id);

	public int getUserCount();
	public int getItemCount();
	
}
