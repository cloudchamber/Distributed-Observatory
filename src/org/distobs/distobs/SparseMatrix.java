package org.distobs.distobs;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.shapes.Shape;


public class SparseMatrix {
	private int MAX_NUM_ELEMENTS = 1000;
	private int sm_x[] = new int[MAX_NUM_ELEMENTS];
	private int sm_y[] = new int[MAX_NUM_ELEMENTS];
	private int sm_val[] = new int[MAX_NUM_ELEMENTS];
	private int length = 0;
	private int width, height;
	private int mx=0, my=0;
	private int[] pixels = new int[1536*2048];
	
	public SparseMatrix(int w, int h) {
		width = w;
		height = h;
	}
	
	public void add(int x, int y, int val) {
		if (length < MAX_NUM_ELEMENTS) {
			sm_x[length] = x;
			sm_y[length] = y;
			sm_val[length] = val;
			length++;
		}
	}
	
	public int length() {
		return length;
	}
	
	public int sum() {
		int sum = 0;
		for (int i=0; i<length; i++) {
			sum += sm_val[i];
		}
		return sum;
	}
	
	public double mean() {
		return sum()/(1.0*width*height);
	}
	
	public double var() {
		double mean = mean();
		double var = 0;
		
		for (int i=0; i<length; i++) {
			var += (sm_val[i]-mean)*(sm_val[i]-mean);
		}
		var /= (1.0*width*height);
		
		return var;
	}
	
	public int max() {
		int max = 0;
		for (int i=0; i<length; i++) {
			if (sm_val[i]>max) {
				max = sm_val[i];
				mx = sm_x[i];
				my = sm_y[i];
			}
		}
		return max;
	}
	
	public int mx() {
		return mx;
	}
	
	public int my() {
		return my;
	}
	
	public void sparsify(Bitmap bm) {
		bm.getPixels(pixels, 0, 1, 0, 0, bm.getWidth(), bm.getHeight());
		
		width = bm.getWidth();
		height = bm.getHeight();
		
		for (int x=0; x<width; x++) {
			for (int y=0; y<height; y++) {
				//if (Color.red(bm.getPixel(x, y)) > 1) {				
				//	add(x, y, Color.red(bm.getPixel(x, y)));
				//}
				//Log.v(TAG,"x="+x+" y="+y+" val="+pixels[x]);
			}
		}    						
	}
}
