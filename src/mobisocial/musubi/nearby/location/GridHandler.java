/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.nearby.location;

import java.math.BigInteger;

/**
 * This class retrieves the GPS location from shared preferences and outputs coordinates
 * that are converted in respect to a hexagonal grid. The output is the coordinates
 * concatenated.
 **/
public class GridHandler {
	
	// ----- CONSTANTS -----
	private static double HALF_MERIDIANAL_CIRCUMFERENCE=2003.93*1000; //half meridianal circumference of the earth in meters
	private static double RADIUS=6378100; //radius of the earth in meters
	private static final double CONVERSION = 3.2808399; // used to convert between meters and feet.
	
	// Main function (most important) that retreives all three grid types corresponding to inputed grid size
	public static long[] getGridCoords(double mlatitude, double mlongitude, int gridsize_feet)
	{
		long[] retVal = new long[3];
		
		int gridsize_meters = (int) (gridsize_feet / CONVERSION); // All grids conversions are done in meters.
		
		//System.err.println("Location retrieved is : Latitude: " + mlatitude + "\t Longitude: " + mlongitude + "\t Grid size (in feet): " + gridsize_feet);
		
		
		float xyz[];
  	    int exp=24;		//use : 2 ^ 48 + 21
  		BigInteger leftshift = BigInteger.valueOf(2);
  		leftshift = leftshift.pow(exp);
  		
  		long latlon;
  		
  		// Get all three grid types --- remember there are three grids that are overlapping that we need to check against. 
  		xyz = getXYZ(mlatitude, mlongitude, gridsize_meters, 0);
  		latlon = (long)(xyz[0]*1E6);
  		latlon <<= 24;
  		latlon |= (long) (xyz[1]*1E6);
   	    retVal[0] = latlon;
   	    
   	    xyz = getXYZ(mlatitude, mlongitude, gridsize_meters, 1);
  		latlon = (long)(xyz[0]*1E6);
  		latlon <<= 24;
  		latlon |= (long) (xyz[1]*1E6);
	    retVal[1] = latlon;
	    
	    xyz = getXYZ(mlatitude, mlongitude, gridsize_meters, 2);
  		latlon = (long)(xyz[0]*1E6);
  		latlon <<= 24;
  		latlon |= (long) (xyz[1]*1E6);
	    retVal[2] = latlon;
   	   
		return retVal;
	
	}
	
	private static float[] getXYZ(double x, double y, int gridsize, int gridType){
		
		//Support for 3 grid types
		float sqrt3 = (float) Math.sqrt(3);
		float step = gridsize;
		float iStep = step;
		float jStep = (float)step*sqrt3;
				
		double stripHeightPerDeg = HALF_MERIDIANAL_CIRCUMFERENCE/180.0;
		double roundoff_x=(int)Math.abs(x)+ 0.5;
		double stripWidthPerDeg = (2*Math.PI*RADIUS*Math.cos(roundoff_x))/360.0;
		
		//System.err.println("x : " + x);
		double xDist = x % 1.0;
		double xMod = x - xDist;
		//System.err.println("xDist : " + xDist);
		
		//System.err.println("stripHeightPerDeg : " + stripHeightPerDeg);
		xDist *= stripHeightPerDeg;
		//System.err.println("xDist : " + xDist);
		
		//System.err.println("y : " + y);
		y += 180;
		//System.err.println("y : " + y);
		//System.err.println("stripWidthPerDeg : " + stripWidthPerDeg);
		double yDist = y * stripWidthPerDeg;
		//System.err.println("yDist : " + yDist);
		
		switch(gridType){
		case 0:
			break;
		case 1:
			xDist -= 0.5*jStep;
			yDist -= 0.5*iStep;
			break;
		case 2:
			//x -= 0.5*jStep;
			yDist -= iStep;
			break;
		}
		
		float[] res = hexagonMap((float)xDist, (float)yDist, gridsize);
		
		switch(gridType){
		case 0:
			break;
		case 1:
			res[0] += 0.5*jStep;
			res[1] += 0.5*iStep;
			break;
		case 2:
			//x -= 0.5*jStep;
			res[1] += iStep;
			break;
		}
		
		//System.err.println("res : " + res[0] + "," + res[1]);
		res[0]/=stripHeightPerDeg;
		res[0] += xMod;
		res[1] = (float) (res[1]/stripWidthPerDeg);
		//System.err.println("lat lon : " + res[0] + "," + res[1]);
		//return xyz;
		return res;
	}
	
	static float[] hexagonMap(float touchX, float touchY, int step){
		
		float sqrt3 = (float) Math.sqrt(3);
		//int step = 100;
		float iStep = step*3;
		float jStep = (float)step*sqrt3;
		boolean isTouched = true;
		
		float res[] = new float[2];
		
		if(isTouched){
			float yIndex = (touchY % iStep)/iStep;
			//System.err.println("touchY : " + touchY +" yIndex : " + yIndex);
			//Simple case
			if(yIndex <= 0.33 || (yIndex > 0.5 && yIndex < 0.83)){
				float lineBlock = touchY / iStep;
				//first row
				if(yIndex < 0.5){
					float xIndex = touchX / jStep;
					float hexX = (float)((int)xIndex) * jStep;
					float hexY = (float)((int)lineBlock) * iStep;
					//drawHexagon(hexX, hexY, step, canvas, true);
					res[0] = hexX; res[1] = hexY;
				}else //Second row
				{
					float xIndex = (touchX - (jStep/2)) / jStep;
					float hexX = (float)(((int)xIndex)+0.5) * jStep;
					float hexY = (float)(((int)lineBlock)+0.5 ) * iStep;
					//drawHexagon(hexX, hexY, step, canvas, true);
					res[0] = hexX; res[1] = hexY;
				}
			}else{
				if(yIndex < 0.5){
					float yNum = touchY / iStep;
					float xNum = touchX / jStep;
					
					float xAdder = (float)((int)xNum);
					float yAdder = (float)((int)yNum);
					
					float x1 = (float) (xAdder + 0.5);
					float y1 = (float) (yAdder + 0.165);
					float dist1 = getDist(x1, y1, xNum, yNum);
					
					float x2 = (float) (xAdder);
					float y2 = (float) (yAdder + 0.665);
					float dist2 = getDist(x2, y2, xNum, yNum);
					
					float x3 = (float) (xAdder + 1);
					float y3 = (float) (yAdder + 0.665);
					float dist3 = getDist(x3, y3, xNum, yNum);
					
					//System.err.println("xNum, yNum " + xNum + "," + yNum);
					//System.err.println("xAddr, yAddr " + xAdder + "," + yAdder);
					//System.err.println("x1, y1, dist1 " + x1 + "," + y1 + "," + dist1);
					//System.err.println("x2, y2, dist2 " + x2 + "," + y2 + "," + dist2);
					//System.err.println("x3, y3, dist3 " + x3 + "," + y3 + "," + dist3);
					
					if(dist1 < dist2){
						if(dist1 < dist3){
							//drawHexagon((x1-0.5f)*jStep, (y1-0.165f)*iStep, step, canvas, true);
							res[0] = (x1-0.5f)*jStep; res[1] = (y1-0.165f)*iStep;
						}
						else{
							//drawHexagon((x3-0.5f)*jStep, (y3-0.165f)*iStep, step, canvas, true);
							res[0] = (x3-0.5f)*jStep; res[1] = (y3-0.165f)*iStep;
						}
					}else{
						if(dist2 < dist3){
							//drawHexagon((x2-0.5f)*jStep, (y2-0.165f)*iStep, step, canvas, true);
							res[0] = (x2-0.5f)*jStep; res[1] = (y2-0.165f)*iStep;
						}else{
							//drawHexagon((x3-0.5f)*jStep, (y3-0.165f)*iStep, step, canvas, true);
							res[0] = (x3-0.5f)*jStep; res[1] = (y3-0.165f)*iStep;
						}
					}
				}else{
					float yNum = touchY / iStep;
					float xNum = touchX / jStep;
					
					float xAdder = (float)((int)xNum);
					float yAdder = (float)((int)yNum);
					
					float x1 = (float) (xAdder + 0.5);
					float y1 = (float) (yAdder + 1.165);
					float dist1 = getDist(x1, y1, xNum, yNum);
					
					float x2 = (float) (xAdder);
					float y2 = (float) (yAdder + 0.665);
					float dist2 = getDist(x2, y2, xNum, yNum);
					
					float x3 = (float) (xAdder + 1);
					float y3 = (float) (yAdder + 0.665);
					float dist3 = getDist(x3, y3, xNum, yNum);
					
					//System.err.println("xNum, yNum " + xNum + "," + yNum);
					//System.err.println("xAddr, yAddr " + xAdder + "," + yAdder);
					//System.err.println("x1, y1, dist1 " + x1 + "," + y1 + "," + dist1);
					//System.err.println("x2, y2, dist2 " + x2 + "," + y2 + "," + dist2);
					//System.err.println("x3, y3, dist3 " + x3 + "," + y3 + "," + dist3);
					
					if(dist1 < dist2){
						if(dist1 < dist3){
							//drawHexagon((x1-0.5f)*jStep, (y1-0.165f)*iStep, step, canvas, true);
							res[0] = (x1-0.5f)*jStep; res[1] = (y1-0.165f)*iStep;
						}
						else{
							//drawHexagon((x3-0.5f)*jStep, (y3-0.165f)*iStep, step, canvas, true);
							res[0] = (x3-0.5f)*jStep; res[1] = (y3-0.165f)*iStep;
						}
					}else{
						if(dist2 < dist3){
							//drawHexagon((x2-0.5f)*jStep, (y2-0.165f)*iStep, step, canvas, true);
							res[0] = (x2-0.5f)*jStep; res[1] = (y2-0.165f)*iStep;
						}else{
							//drawHexagon((x3-0.5f)*jStep, (y3-0.165f)*iStep, step, canvas, true);
							res[0] = (x3-0.5f)*jStep; res[1] = (y3-0.165f)*iStep;
						}
					}
				}
			}
		}
		return res;
	}
	
	static float getDist(float x1, float y1, float x2, float y2){
		return (float) Math.sqrt((x1-x2)*(x1-x2)+ (y1-y2)*(y1-y2));
	}

}