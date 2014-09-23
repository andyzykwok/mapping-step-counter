package ca.uwaterloo.lab4_202_06;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

	LineGraphView graph;
	MapView mv;
	NavigationalMap map;

	int stepcount = 0;	
	float stepsNorth = 0, stepsEast = 0, stepsDisplacement = 0, directionAngle;
	float [] gravity = new float [3];

	PointF startPoint = new PointF (200,200);
	PointF endPoint = startPoint;
	PointF userLocation = startPoint;
	PointF startTurn, endTurn;

	String endText = "You did not reach the destination.";
	String directionText = "\n";
	
	boolean directPath = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		TextView stepText, magneticText;
		Button button;

		// Declaring layout
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		LinearLayout l = (LinearLayout)findViewById(R.id.layout);
		l.setOrientation(LinearLayout.VERTICAL);		

		// Creating & displaying graph
		graph = new LineGraphView(getApplicationContext(),100,Arrays.asList("x", "y", "z"));
		l.addView(graph);

		// Step count
		stepText = new TextView (getApplicationContext());
		l.addView(stepText);
		magneticText = new TextView (getApplicationContext());
		l.addView(magneticText);

		// SENSORS
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);	

		// Linear Acceleration Sensor    
		Sensor linearaccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		SensorEventListener acceleration = new LinearAccelerationEventListener(stepText);
		sensorManager.registerListener(acceleration, linearaccelerationSensor, SensorManager.SENSOR_DELAY_FASTEST);

		// Accelerometer
		Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		SensorEventListener accelerometer = new AccelerometerEventListener();
		sensorManager.registerListener(accelerometer, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

		// Magnetic Field Sensor (Compass)
		Sensor magneticfieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		SensorEventListener magneticfield = new MagneticFieldEventListener(magneticText);
		sensorManager.registerListener(magneticfield, magneticfieldSensor, SensorManager.SENSOR_DELAY_FASTEST);

		// Reset Button 
		button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				stepcount = 0;
				stepsNorth = 0f;
				stepsEast = 0f;
				stepsDisplacement = 0f;
			}// end onClick method
		});

		// Map
		mv = new MapView(getApplicationContext(), 600, 300, 25, 25);
		map = MapLoader.loadMap(getExternalFilesDir(null), "Lab-room-peninsula.svg");
		mv.setMap(map);
		l.addView(mv);
		registerForContextMenu(mv);

		// Class that implements PositionListener
		class Position implements PositionListener
		{
			List <PointF> points = new ArrayList <PointF> ();
			float yTestPoint;
			
			// Constructor
			public Position () {
				// nothing required
			} 

			@Override
			public void originChanged(MapView source, PointF loc) {
				// TODO Auto-generated method stub

				mv.setOriginPoint(loc);
				userLocation = loc;
				mv.setUserPoint(userLocation);
				startPoint = loc;
				mv.setUserPath(points);    
				
				endText = "You have not reached the destination.";
				
			} // end originChange method

			@Override
			public void destinationChanged(MapView source, PointF dest) {
				// TODO Auto-generated method stub
				mv.setDestinationPoint(dest);
				endPoint = dest;

				// Direct path
				if (map.calculateIntersections(startPoint, endPoint).size() == 0) {
					map.calculateIntersections(dest, startPoint).size();
					
					points.add(mv.getUserPoint()); 
					points.add(endPoint);
					
					directPath = true;
				}
				
				//Pathfinder
				else {
					yTestPoint = endPoint.y;
					if(startPoint.y > endPoint.y) {
						yTestPoint = startPoint.y;
					}
					//Find where y values meet without blockage
					startTurn = new PointF(startPoint.x, yTestPoint);
					endTurn = new PointF(endPoint.x, yTestPoint);
					while (map.calculateIntersections(startTurn, endTurn).size() > 0) {
						startTurn = new PointF(startPoint.x, yTestPoint);
						endTurn = new PointF(endPoint.x, yTestPoint);
						yTestPoint++;
					}

					points.add(mv.getUserPoint());
					points.add(startTurn);
					points.add(endTurn);
					points.add(endPoint);
					
					directPath = false;	
				}
				mv.setUserPath(points);
				points = new ArrayList <PointF> ();
				
				// Instructions
				if (directPath)
					directionText = String.format("\nWalk %.2f steps North & %.2f steps East.", mv.getUserPoint().y - endPoint.y, endPoint.x - mv.getUserPoint().x );

				else 	
					directionText = String.format("\nWalk %.2f steps north, %.2f steps east and %.2f steps north.",
						mv.getUserPoint().y - startTurn.y, mv.getUserPoint().x - endTurn.x, mv.getUserPoint().y - endPoint.y );		
			} // end destinationChanged method       
		} // end Position Class
		Position p = new Position();
		mv.addListener(p);
	} // end onCreate method

	/* Takes Linear Acceleration readings and filters values. The filtered values 
	 * are graphed and put through the finite state machine. Steps are counted. Direction
	 * of steps are also calculated then added to its count.
	 */
	class LinearAccelerationEventListener implements SensorEventListener {
		float [] filteredOutput = new float [3];	
		boolean step = false;
		int stage = 1;
		float x, y, z, littlez = 0, bigz = 0;	
		TextView display; 
		
		PointF nextPosition;
		float stepsRemaining;

		//Constructor
		public LinearAccelerationEventListener(TextView outputView){
			display = outputView;
		}

		@Override
		public void onAccuracyChanged(Sensor s, int i) {
			// nothing required here.
		}

		//low pass filter
		float [] lowPass (float [] input, float [] output){
			float ALPHA = 0.15f;
			if ( output == null ) 
				return input;
			for(int i = 0; i < input.length; i++) {
				output[i] = output[i] + ALPHA * (input[i] - output[i]);
			}
			return output;
		}

		//high pass filter
		float [] highPass (float [] input, float [] output){
			float ALPHA = 0.85f;
			if ( output == null ) return input;
			for(int i = 0; i < input.length; i++) {
				output[i] = ALPHA * input[i] + (1-ALPHA) * output[i];
			}
			return output;
		}

		@Override
		public void onSensorChanged(SensorEvent se) {
			if (se.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){			
				filteredOutput = highPass(lowPass(se.values, filteredOutput), filteredOutput);

				x = filteredOutput [0];
				y = filteredOutput [1];
				z = filteredOutput [2];

				graph.addPoint(filteredOutput);
			}

			//limit large movements
			if (z > 3 || z < -3 || x > 3 || x < -3 || y > 3 || y < -3){
				stage = 1;
			}

			//stage 1 
			if (stage == 1 && z > 0.8 && z < 2 && !step) {
				stage = 2;
				bigz = 0;
				littlez = 0;
			}

			//find largest value in step
			if (bigz < z) {
				bigz = z;
			}

			//stage 2
			if (stage == 2 && z < 0.05 && !step){
				stage = 3;			
			}

			//find lowest value in step
			if (littlez > z){
				littlez = z;
			}
			//filter out shaking (if positive and negative values are close or negative values are greater than positive, this indicates shaking)
			if (Math.abs(Math.abs(bigz)-Math.abs(littlez)) < 0.25 || Math.abs(bigz) < Math.abs(littlez)){
				stage = 1; //reset sequence
			} 

			//stage 3
			if (stage == 3 && z < -0.25 && z > -2 && !step){
				stage = 4;
			}

			//stage 4
			if (stage == 4 && z > -0.05 && !step){
				step = true;
			}

			//count step, reset values
			if (step) {
				stepcount++;
				step = false;
				stage = 1;

				//Step Directions
				stepsNorth += (float) Math.cos(directionAngle);
				stepsEast += (float) Math.sin(directionAngle);
				stepsDisplacement = (float) Math.sqrt(Math.pow(stepsNorth, 2) + Math.pow(stepsEast, 2));

				nextPosition = new PointF(mv.getUserPoint().x + 0.25f*((float) Math.sin(directionAngle)),
						mv.getUserPoint().y - 0.25f*((float) Math.cos(directionAngle)));

				// If no contact, set to new position
				if (map.calculateIntersections(mv.getUserPoint(),nextPosition).size() == 0) {
					mv.setUserPoint(nextPosition);
					nextPosition = null;
				}

				// If hits wall, do not change position
				else
					nextPosition = null;
				
				// Check if destination is reached
				if((Math.abs((mv.getUserPoint().x - endPoint.x)) < 0.5) && (Math.abs((mv.getUserPoint().y - endPoint.y)) < 0.5))
					endText = "You have reached the destination.";
				
				else
					endText = "You have not reached the destination.";
				
				// Steps Remaining
				if (directPath)
						directionText = String.format("\nWalk %.2f steps North & %.2f steps East.", mv.getUserPoint().y - endPoint.y, endPoint.x - mv.getUserPoint().x );

				else 	
					directionText = String.format("\nWalk %.2f steps north, %.2f steps east and %.2f steps north.",
							mv.getUserPoint().y - startTurn.y, mv.getUserPoint().x - endTurn.x, mv.getUserPoint().y - endPoint.y );		
			}
			
			display.setText(String.format ("---Steps---\nSTEPS: " + stepcount + "\n\n---Displacement---\nFrom Origin: " 
					+ stepsDisplacement + "\nNorth: " + stepsNorth + "\nEast: " + stepsEast ));

		} // end onSensorChanged method
	} // end LinearAccelerationEventListener class.

	/* Used to find the current value of gravity */
	class AccelerometerEventListener implements SensorEventListener {

		// Constructor
		public AccelerometerEventListener(){
			//nothing required here
		}

		@Override
		public void onAccuracyChanged(Sensor s, int i) {
			// nothing required here.
		}

		@Override
		public void onSensorChanged(SensorEvent se) {
			if (se.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
				gravity = se.values;
			}
		} // end onSensorChanged method
	} // end AccelerometerEventListener class.

	/* Takes the Magnetic Field values and finds the orientation. Orientation
	 * values are then filtered.
	 */
	class MagneticFieldEventListener implements SensorEventListener {
		TextView output;
		float[] orientation, rotationMatrix, inclinationMatrix, error;
		float selectedValue, errorSum = 0;
		int sample, sampleSize;

		//Constructor
		public MagneticFieldEventListener(TextView outputView) {
			output = outputView;
			orientation = new float[3];
			inclinationMatrix = new float[9];
			rotationMatrix = new float[9];
			sample = -1;
			sampleSize = 30;
			error = new float[sampleSize];
		}

		@Override
		public void onAccuracyChanged(Sensor s, int i) {
			// nothing required here
		}

		@Override
		public void onSensorChanged(SensorEvent se) {
			SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, se.values);
			SensorManager.getOrientation(rotationMatrix, orientation);

			if (sample < 0) {
				selectedValue = orientation [0];
				sample = 0;
			}

			else if (sample < sampleSize) {
				if (orientation[0] - selectedValue > 3.14f)
					error[sample] = orientation[0] - selectedValue - 6.28f;	            
				else if (orientation[0] - selectedValue < -3.14f)
					error[sample] = orientation[0] - selectedValue + 6.28f;	            
				else
					error[sample] = orientation[0] - selectedValue;

				sample++;
			}

			else {
				errorSum = 0;
				for(int i = 0; i < sampleSize; i++)
					errorSum += error[i];

				if (selectedValue + errorSum / sampleSize > 3.14f)
					selectedValue = selectedValue + errorSum / sampleSize - 6.28f;            
				else if (selectedValue + errorSum / sampleSize < -3.14f)
					selectedValue = selectedValue + errorSum / sampleSize + 6.28f;
				else
					selectedValue = selectedValue + errorSum / sampleSize;

				sample = 0;
			}

			directionAngle = selectedValue;

			output.setText(String.format("\nMagnetic Field: ( %.2f, %.2f, %.2f)\nFiltered Orientation Value: %.2f\n\n" + endText + directionText,
					orientation [0], orientation [1], orientation [2], selectedValue));
		} // end onSensorChanged method
	} // end MagneticFieldEventListener class 

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	} // end onCreateOptionsMenu method

	// Methods required to display map
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		mv.onCreateContextMenu(menu, v, menuInfo);
	} // end onCreateContextMenu method

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return super.onContextItemSelected(item) || mv.onContextItemSelected(item);
	} // end onContextItemSelected method
} // end MainActivity class.