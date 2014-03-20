package com.alouder.polarlayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;

/*
 * Copyright (C) 2014 William JIANG (williamjiang0218@gmail.com)
 * Created on Feb 19, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * PolarLayout is designed to provide a square layout containing a disk filling
 * it to enable 
 * 1) placing of items by defining their degrees (relative to the x axis) and radius
 *  (relative to the radius of the imaginative disk). 
 * 2) spinning of the disk and all its children. 
 * 3) handling clicking of the children views centrally. 
 * {@link http://en.wikipedia.org/wiki/Polar_coordinate_system}
 * 
 * @author William JIANG
 * 
 */
public class PolarLayout extends ViewGroup {
	public static final String TAG = "PolarLayout";

	/**
	 * Two possible approaches are tried to enable the layout & drawing of the
	 * child views, the option two is preferred way: 
	 * 1) getChildStaticTransformation() is used to transform each child views,
	 * then the constructors must include setStaticTransformationsEnabled(true);
	 * 2) overriding the drawChild() and adjust canvas to draw each child views
	 * usingGetChildStaticTransformation determines which options is adopted,
	 * true -> option1 false -> option2.
	 */
	/*
	//Maybe there is no need to try option 1) any longer, especially some functions are phased out.
	private static final boolean usingGetChildStaticTransformation = false;
	*/

	// Minimum size of the layout in pixels
	private static final int MIN_SQUARE_SIZE = 100;
	
	// Maximum of angle difference between the touched position and the possible touched child view.
	private static final int MAX_ANGLE_DIFFERENCE = 60;
	
	//Relative to the size of the disk, the default radius of child items that are not fixed
	private static final float DEFAULT_RADIUS_RATIO = 0.9f;
	
	//Max radius of child items that are not fixed
	private static final float MAX_RADIUS_RATIO = 0.95f;
	
	//Min radius of child items that are not fixed
	private static final float MIN_RADIUS_RATIO = 0.1f;

	/**
	 * Defines spinning speed of the PolarLayout, the figures identify how many mSeconds
	 * to rotate the disk for 360 degrees.
	 */
	public enum SpinSpeed {
		SLOWEST(10000), SLOWER(8000), SLOW(5000), NORMAL(3000), FAST(2500), FASTER(
				2000), FASTEST(1000);

		int milliSecondsPerRound;

		private SpinSpeed(int msPerRound) {
			milliSecondsPerRound = msPerRound;
		}

		public static HashMap<Integer, SpinSpeed> SPEEDS = new HashMap<Integer, SpinSpeed>();

		static {
			SpinSpeed[] speeds = SpinSpeed.values();
			for (int i = 0; i < speeds.length; i++) {
				SpinSpeed speed = speeds[i];
				SPEEDS.put(speed.milliSecondsPerRound, speed);
			}
		}

		public static SpinSpeed SpeedOf(int duration) {
			if (SPEEDS.containsKey(duration))
				return SPEEDS.get(duration);
			else
				return NORMAL;
		}

		public int getValue() {
			return milliSecondsPerRound;
		}

		public boolean compare(int i) {
			return milliSecondsPerRound == i;
		}
	}
	
	/*/ //TODO: use the enum to define pivot of the child views
	public enum PivotPosition {
		Center,			//Default pivot position
		LeftCenter,
		TopCenter,
		BottomCenter,
		RightCenter,
		TopLeft,
		TopRight,
		BottomLeft,
		BottomRight
	}
	//*/

	/**
	 * Per-child layout information associated with PolarLayout.
	 */
	public static class LayoutParams extends ViewGroup.MarginLayoutParams {

		/**
		 * Indicate if the child view is fixed on the PolarLayout.
		 */
		@ViewDebug.ExportedProperty(category = "layout")
		private boolean isFixed = true;
		
		/**
		 * @return the isFixed
		 */
		public boolean isFixed() {
			return isFixed;
		}

		/**
		 * @param isFixed the isFixed to set
		 */
		public void setFixed(boolean isFixed) {
			this.isFixed = isFixed;
		}
		
		//TODO: Implements Pivot related layout operations.
		/**
		 * The pivot of the child view where it rotate around.
		 */
//		@ViewDebug.ExportedProperty(category = "layout")
//		public PivotPosition pivot = PivotPosition.Center;		

		/**
		 * The relative distance from the pole, a circle of radius=1 will fill
		 * the PolarLayout exactly.
		 */
		@ViewDebug.ExportedProperty(category = "layout")
		public float radius = DEFAULT_RADIUS_RATIO;

		/**
		 * The clockwise angle in degrees from the pole, the x axis toward right is 0
		 * degree.
		 */
		@ViewDebug.ExportedProperty(category = "layout")
		private float azimuth = 0;

		/**
		 * @return the azimuth
		 */
		public float getAzimuth() {
			return azimuth;
		}

		/**
		 * @param azimuth the azimuth to set
		 */
		public void setAzimuth(float azimuth) {
			this.azimuth = azimuth;
		}

		/**
		 * Initial Orientation in degrees of the child view, default to be 0
		 * degree.
		 */
		@ViewDebug.ExportedProperty(category = "layout")
		private float orientation = 0;


		/**
		 * @return the orientation
		 */
		public float getOrientation() {
			return orientation;
		}

		/**
		 * @param orientation the orientation to set
		 */
		public void setOrientation(float orientation) {
			this.orientation = orientation;
		}
		
		/*/ //TODO: check why changing nothing doesn't trigger redraw()
		private int nothing = 0;

		public int getNothing() {
			return nothing;
		}
		public void setNothing(int nothing) {
			this.nothing = nothing;
		}
		//*/

		/**
		 * Defines if the orientation of child view relative to the polar is fixed.
		 */
		@ViewDebug.ExportedProperty(category = "layout")
		public boolean keepOrientation = true;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);

			TypedArray a = c.obtainStyledAttributes(attrs,
					R.styleable.PolarLayout_Layout);

			final int N = a.getIndexCount();
			Resources resources = c.getResources();
			int tempId;
			for (int i = 0; i < N; i++) {
				int attr = a.getIndex(i);
				switch (attr) {
					case R.styleable.PolarLayout_Layout_layout_fixed:
						setFixed(a.getBoolean(attr, false));
						break;
//					case R.styleable.PolarLayout_Layout_layout_pivot:
//						//TODO: to extract pivot by reusing Gravity class.
//						break;
					case R.styleable.PolarLayout_Layout_layout_azimuth:
						tempId = a.getResourceId(attr, -1);
						if (tempId != -1) {
							setAzimuth(resources.getDimension(tempId));
						} else {
							setAzimuth(a.getFloat(attr, 0));
						}
						break;
					case R.styleable.PolarLayout_Layout_layout_orientation:
						tempId = a.getResourceId(attr, -1);
						if (tempId != -1) {
							setOrientation(resources.getDimension(tempId));
						} else {
							setOrientation(a.getFloat(attr, 0));
						}
						break;
					case R.styleable.PolarLayout_Layout_layout_radius:
						tempId = a.getResourceId(attr, -1);
						if (tempId != -1) {
							radius = resources.getDimension(tempId);
						} else {
							radius = a.getFloat(attr, 0);
						}

						break;
					case R.styleable.PolarLayout_Layout_layout_keepOrientation:
						keepOrientation = a.getBoolean(attr, true);
						break;
				}
			}
			
			a.recycle();
			
			if (isFixed() || radius == 0)
				return;
			
			// Limit the ratio within a reasonable range when it is not fixed
			if (radius > MAX_RADIUS_RATIO)
				radius = MAX_RADIUS_RATIO;
			else if (radius < MIN_RADIUS_RATIO)
				radius = MIN_RADIUS_RATIO;
		}

		public LayoutParams(int w, int h) {
			super(w, h);
		}

		/**
		 * {@inheritDoc}
		 */
		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

		/**
		 * {@inheritDoc}
		 */
		public LayoutParams(ViewGroup.MarginLayoutParams source) {
			super(source);
		}

		@Override
		public String toString() {
			return String.format("%d°, %d%, orientation=%d, " +
					"isFixed=%b, keepOrientation=%b", 
					(int)getAzimuth(), (int)radius, (int)getOrientation(), isFixed(), keepOrientation);
		}
	}

	OnTouchListener listener = null;
	
	OnClickListener childOnClickListener = new OnClickListener() {
		
		//Default onClickListener of the child views to rotate it to 0 degrees
		@Override
		public void onClick(View v) {
			LayoutParams params = (LayoutParams) v.getLayoutParams();
			if (params == null || params.isFixed())
				return;

			float temp = (params.getAzimuth() + spinning) % 360;

			float toSpin = temp <= 180 ? -temp : 360 - temp;

			spin(toSpin);
		}

	};

	// Time in milliSeconds to rotate the disk for 360 degrees
	private SpinSpeed speed = SpinSpeed.NORMAL;

	/**
	 * Returns a set of layout parameters with a width of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}, a height of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
	 */
	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
	}

	// Override to allow type-checking of LayoutParams.
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof PolarLayout.LayoutParams;
	}

	@Override
	public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(
			ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}

	// Center xOffset&yOffset in pixels on the PolarLayout
	private float centerOffset = 0;

	// Rotated degrees of the PolarLayout as a whole
	private float spinning = 0;

	public float getSpinning() {
		return spinning;
	}
	
	public void setSpinning(float spinning) {
		this.spinning = spinning;
		// Simply re-calculate all when spinning might result in position
		// changes of all child views
		requestLayout();
	}
	
	/*/ //Drawing the disk border
	private int disk_color = 0xff0000ff;
	private Paint p;
	
	public int getDiskColor() {
		return disk_color;
	}
	
	@Override
	protected void dispatchDraw(Canvas canvas) {
		//When the disk_color is set, the Paint p would not be null
		if (p != null) {
			canvas.drawOval(rect, p);
		}
		super.dispatchDraw(canvas);
	}

	//*/ //End of Drawing the disk border

	// Used for option 1) only, as an indicator to trigger redrawing of the child views
	// private boolean needInvalidate = false;

	/** Re-usable matrix for canvas transformations */
	private Matrix matrix;

	public PolarLayout(Context context) {
		super(context);
		init(context, null);
	}

	public PolarLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public PolarLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	/*
	 * To set the LayoutParams of the PolarLayout by parsing layout file.
	 */
	private void init(Context c, AttributeSet attrs) {
		/* Commented when Option 1) is abandoned
		// For Option 1 only: using child view transformation to draw
		if (usingGetChildStaticTransformation)
			this.setStaticTransformationsEnabled(true);

		this.needInvalidate = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
		*/

		// Instantiate a single Matrix object.
		if (matrix == null)
			matrix = new Matrix();

		// Retrieve parameters if they are defined in layout file
		if (attrs != null) {
			TypedArray a = c.obtainStyledAttributes(attrs,
					R.styleable.PolarLayout);

			final int N = a.getIndexCount();
			Resources resources = c.getResources();
			int tempId;
			for (int i = 0; i < N; i++) {
				int attr = a.getIndex(i);
				switch (attr) {
					case R.styleable.PolarLayout_spinning:
						tempId = a.getResourceId(attr, -1);
						if (tempId != -1) {
							spinning = resources.getDimension(tempId);
						} else {
							spinning = a.getFloat(attr, 0);
						}
						break;
//					case R.styleable.PolarLayout_disk_color:
//						tempId = a.getResourceId(attr, -1);
//						if (tempId != -1) {
//							disk_color = resources.getColor(tempId);
//						} else {
//							disk_color = a.getColor(attr, 0x0000ff);
//						}
//						
//						//Set the background color to transparent to prevent View.onDraw() from overriding of disk 
//						this.setBackgroundColor(Color.TRANSPARENT);
//						p = new Paint();
//						p.setColor(disk_color);
//						p.setStyle(Style.FILL_AND_STROKE);
//						break;
						
//					case R.styleable.PolarLayout_spin_speed:
//						tempId = a
//								.getInteger(attr, SpinSpeed.NORMAL.getValue());
//						speed = SpinSpeed.SpeedOf(tempId);
//						break;
				}
			}

			a.recycle();
		}

		// The delayed function set all children's onClickListener to the PolarLayout 
		// for centralized onClick handling
		this.post(new Runnable() {
			// Post in the PolarLayout's message queue to make sure
			// the child views are added before
			@Override
			public void run() {
				PolarLayout circle = PolarLayout.this;
				// Prepare to handling click of the child views
				int childCount = circle.getChildCount();
				for (int i = 0; i < childCount; i++) {
					View child = circle.getChildAt(i);
					if (child.getVisibility() == View.GONE)
						continue;

					LayoutParams params = (LayoutParams) child.getLayoutParams();
					
					//If the child view is not fixed, click on it might trigger the default rotating
					if (!params.isFixed()) {
						// Let the PolarLayout to handle onClick events of its children
						child.setOnClickListener(childOnClickListener);						
					}
				}
			}
		});
	}

	/**
	 * Get the angle of the touch position relative to the circle center.
	 * 
	 * @param x
	 *            The x offset within the PolarLayout.
	 * @param y
	 *            The y offset within the PolarLayout.
	 * @return 
	 * 			angle in degrees, 0 <= angle < 360.
	 */
	public float getAngle(float x, float y) {
		float angle = (float) Math.toDegrees(Math.atan2(y - centerOffset, x
				- centerOffset));

		if (angle < 0) {
			angle += 360;
		}

		return angle;
	}

	/**
	 * Override this to detect the touch happens on which child view, notice
	 * that child.getHitRect() CANNOT be used because it might not support
	 * transformation.
	 * Step 1), get the angle and radius relative to the center
	 * of the circle.
	 * Step 2), sort child views by angle difference with this
	 * touch event.
	 * Step 3), check if the touch happens within any child views.
	 * 		If yes, returns false and call child.dispatchTouchEvent()
	 * 		If all no, returns true and let onTouch() to handle it.
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		boolean consumed = false;

		final float x = ev.getX();
		final float y = ev.getY();
		// Step 1: get the angle of the touch event.
		final float angle = getAngle(x, y);

		// Step 2: get sorted LayoutParams.
		int childCount = this.getChildCount();

		//TODO: is it really necessary?
		//Calculate the current children positions since they might have changed
		HashMap<LayoutParams, View> childrenMap = new HashMap<LayoutParams, View>();
		for (int i = 0; i < childCount; i++) {
			View v = this.getChildAt(i);
			if (v.getVisibility() != View.GONE && v.isClickable()) {
				LayoutParams param = (LayoutParams) v.getLayoutParams();
				childrenMap.put(param, v);
			}
		}

		ArrayList<LayoutParams> params = new ArrayList<LayoutParams>(
				childrenMap.keySet());
		// The sorting is based on angle difference between the child items and the touched position
		Collections.sort(params, new Comparator<LayoutParams>() {

			@Override
			public int compare(LayoutParams lhs, LayoutParams rhs) {
				float delta1 = Math.abs((lhs.getAzimuth() + spinning - angle) % 360);
				float delta2 = Math.abs((rhs.getAzimuth() + spinning - angle) % 360);
				delta1 = delta1 <= 180 ? delta1 : 360 - delta1;
				delta2 = delta2 <= 180 ? delta2 : 360 - delta2;
				return Float.compare(delta1, delta2);
			}
		});

		PointF localPoint = new PointF();
		for (int i = 0; i < childCount; i++) {
			LayoutParams p = params.get(i);

			// Neglect those child views that are too far away
			float delta = Math.abs((p.getAzimuth() + spinning - angle) % 360);
			
			// Avoid further comparing when the delta is out of +60 and -60
			if (delta > MAX_ANGLE_DIFFERENCE)
				break;

			if (isTransformedTouchPointInView(x, y, p, localPoint)) {
				MotionEvent cp = MotionEvent.obtain(ev);

				// Refer to
				// http://stackoverflow.com/questions/17845545/custom-viewgroup-dispatchtouchevent-doesnt-work-correctly
				// the MotionEvent must be offset properly thus within the child
				// view to process further.
				cp.offsetLocation(localPoint.x - p.leftMargin, localPoint.y
						- p.topMargin);
				View child = childrenMap.get(p);
				consumed = child.dispatchTouchEvent(cp);
				cp.recycle();
				return consumed;
			}
		}

		//Now the touch doesn't happen on any child view, so it is on the PolarLayout itself
		MotionEvent transformed = MotionEvent.obtain(ev);
		transformed.offsetLocation(-centerOffset, -centerOffset);

		//Let the PolarLayout to handle it, either by the onTouchListerner or release new event
		if (listener != null && listener.onTouch(this, transformed))
			return true;
		else if (onTouchEvent(ev))
			return true;

		return false;
	}

	/**
	 * Transformed position within the PolarLayout to the relative position of the child view,
	 * and check if it is within the scope of the specific child view.
	 * @param x 
	 * 		The concerned x axis position
	 * @param y 
	 * 		The concerned y axis position.
	 * @param param 
	 * 		LayoutParams of the child view.
	 * @param outLocalPoint 
	 * 		Point storing the mapped location relative to the child view.
	 * @return 
	 * 		True if a child view contains the specified point;
	 * 		False when the touch happens out of the child view.
	 */
	protected boolean isTransformedTouchPointInView(float x, float y,
			LayoutParams param, PointF outLocalPoint) {
		float widthHalf = (param.rightMargin - param.leftMargin)/2f;
		float heightHalf = (param.bottomMargin - param.topMargin)/2f;
		float pivotX = (param.rightMargin+param.leftMargin) /2f;
		float pivotY = (param.topMargin + param.bottomMargin) /2f;
		
//		//finalAzimuth describes the position of the child view
//		float finalAzimuth = param.azimuth;
		//finalOrientation describes the absolute orientation of the child view
		float finalOrientation = param.getOrientation();
		
		
		//Otherwise, spinning of the disk and its azimuth shall also be counted
		if (!param.isFixed()) {
//			//The final azimuth is shifted by the spinning of the disk
//			finalAzimuth += spinning;
			
			// If the child view is neither fixed nor keepOrientation
			if (!param.keepOrientation) {
				finalOrientation += spinning;				
			}
		}
		
		// Mapping the original coordinate to the position relative to the child view
		matrix.reset();
		matrix.postTranslate(-pivotX, -pivotY);
		matrix.postRotate(-finalOrientation);
		float[] pts = new float[] { x, y };

		// Get the mapped x/y relative to the child reference point or (r, theta).
		matrix.mapPoints(pts);

		outLocalPoint.x = pts[0];
		outLocalPoint.y = pts[1];
		
		Log.d(TAG, String.format("(%f, %f) => %s", x, y, outLocalPoint.toString()));
		return Math.abs(pts[0])<=widthHalf && Math.abs(pts[1])<=heightHalf;
	}

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		/* For Option 1) only, not used any longer
		if (usingGetChildStaticTransformation)
			return super.drawChild(canvas, child, drawingTime);
		*/

		LayoutParams params = (LayoutParams) child.getLayoutParams();
		if (child.getVisibility() == GONE || params == null)
			return super.drawChild(canvas, child, drawingTime); // Or simply return false ?
		
		/*/
		Log.d(TAG, String.format("azimuth=%d, orientation=%d, l=%d, r=%d, t=%d, b=%d",
				(int)params.getAzimuth(), (int)params.orientation, params.leftMargin,
				params.rightMargin, params.topMargin, params.bottomMargin));
		//*/
		
		// In case the child view is fixed, only azimuth determines how it is rotated
		float rotationNeeded = spinning + params.azimuth;
		
		if (params.isFixed())
			rotationNeeded = params.orientation;		
		else if (params.keepOrientation)
			rotationNeeded += params.orientation;
		
		float width = params.rightMargin - params.leftMargin;
		float height = params.bottomMargin - params.topMargin;
		float centerX = (params.rightMargin + params.leftMargin)/2f;
		float centerY = (params.bottomMargin + params.topMargin)/2f;
		float wHalf = width / 2f;
		float hHalf = height / 2f;
		
		canvas.save();
		
		/*/ //Applying transformations via matrix
		
		matrix.setTranslate(params.leftMargin+wHalf, params.topMargin+hHalf);
		
		//Rotate the child view to its final orientation
		matrix.postRotate(rotationNeeded);		
		
		//Then translate the child view to its final position on the canvas
		matrix.postTranslate(-wHalf, -hHalf);
		
		canvas.setMatrix(matrix);
		child.draw(canvas);		
		//*/ //End of Applying transformations via matrix
		
		//*/ //Applying transformations directly on the canvas
		//TODO: different cases when pivot is not in the center of the view
		canvas.translate(centerX, centerY);
		canvas.rotate(rotationNeeded);
		
		//Notice: the axis must be adjusted to avoid offset of the pivot
		canvas.translate(-wHalf, -hHalf);
		
		child.draw(canvas);		
		//*/ //End of Applying transformations directly on the canvas
		
		canvas.restore();
		return true;

		/*/ //Obsoleted on 24/02
		if (params.keepOrientation) {
			canvas.rotate(params.azimuth + spinning + params.orientation);
			canvas.translate(0, -(child.getHeight() >> 1));
			child.draw(canvas);

			// Be cautious, do not use this when canvas has been transformed
			// super.drawChild(canvas, child, drawingTime);

			canvas.restore();

			return true;
		} else {
			return super.drawChild(canvas, child, drawingTime);
		}
		//*/
	}

	/* Commented after abandon Option 1)
	@Override
	protected boolean getChildStaticTransformation(View child, Transformation t) {
		LayoutParams params = (LayoutParams) child.getLayoutParams();
		if (child.getVisibility() == GONE || params == null)
			return false;

		if (params.isUpright) {
			// layoutChild(child);
			t.clear();
			final int height = child.getHeight();
			final Matrix matrix = t.getMatrix();
			float degrees = params.azimuth + params.rotation + spinning;
			matrix.postRotate(degrees, 0, height >> 1);

			if (needInvalidate) {
				child.invalidate();
			}
			return true;
		}

		return false;
	}
	*/

	private RectF rect = null;

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int measuredWidth = measure(widthMeasureSpec);
		int measuredHeight = measure(heightMeasureSpec);

		int w = MeasureSpec.getSize(measuredWidth);
		int h = MeasureSpec.getSize(measuredHeight);
		int size = (w < h) ? measuredWidth : measuredHeight;

		setMeasuredDimension(size, size);
		rect = new RectF(0, 0, size, size);

		if (centerOffset == 0) {
			centerOffset = MeasureSpec.getSize(size) >> 1;
		}

		final int count = getChildCount();

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);

			if (child.getVisibility() != GONE) {
//				LayoutParams params = (LayoutParams) child.getLayoutParams();

				// TODO: to improve the logic?
				int spec = MeasureSpec.makeMeasureSpec(size,
						MeasureSpec.AT_MOST);

				child.measure(spec, spec);

				//Not needed when there is no direct calling of child.setRotation()
//				if (params.keepOrientation) {
//					child.setPivotX(0);
//					child.setPivotY(child.getMeasuredHeight() >> 1);
//				}
			}
		}

	}

	private int measure(int measureSpec) {
		int result = MIN_SQUARE_SIZE;

		// Decode the measurement specifications.
		int mode = MeasureSpec.getMode(measureSpec);
		int size = MeasureSpec.getSize(measureSpec);

		switch (mode) {
			case MeasureSpec.AT_MOST:
				result = Math.max(result, size);
				break;
			case MeasureSpec.EXACTLY:
				result = Math.max(result, size);
				break;
			case MeasureSpec.UNSPECIFIED:
				break;
		}

		result = MeasureSpec.makeMeasureSpec(result, mode);
		return result;
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		// The layout has actually already been performed and the positions
		// cached. Apply the cached values to the children.
		final int count = getChildCount();

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			layoutChild(child);
		}
	}

	/**
	 * Assign a size and position to a view and all of its descendants.
	 * 
	 * Notice:
	 * the LayoutParams contains only virtual bounds of the child views, the
	 * drawChild() will transform using these parameters the views. In addition,
	 * the dispatchTouchEvent() must map motionEvent to this virtual bounds;
	 * 
	 * @param child
	 *            The child view.
	 */
	private void layoutChild(View child) {
		if (child.getVisibility() != GONE) {
			final int w = child.getMeasuredWidth();
			final int h = child.getMeasuredHeight();
			PolarLayout.LayoutParams st = (PolarLayout.LayoutParams) child
					.getLayoutParams();

			// The actual shown degrees, adjusted by the PolarLayout rotation
			// when the child is not fixed
			float relativeDegrees = st.getAzimuth() + (st.isFixed() ? 0 : spinning);
			// child.setRotation(relativeDegrees - st.rotation);
			// Get the outermost position of the child view
			double radians = Math.toRadians(relativeDegrees);
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			
			// Get the x/y offset of the child view pivot
			float xOffset = (float) (centerOffset * (1 + st.radius * cos));
			float yOffset = (float) (centerOffset * (1 + st.radius * sin));

			//use the center to store the relative position to the pole
			//In other words, orientation doesn't affect the l/t/r/b margins
			st.leftMargin = (int) (xOffset - w/2f);
			st.topMargin = (int) (yOffset - h/2f);
			st.rightMargin = st.leftMargin + w;
			st.bottomMargin = st.topMargin + h;

			child.layout(st.leftMargin, st.topMargin, st.rightMargin,
					st.bottomMargin);
		}
	}

	public void spin(float toSpin) {
		ObjectAnimator animator = ObjectAnimator.ofFloat(this, "spinning",
				spinning, spinning + toSpin);
		animator.setDuration(Math.abs((long) (speed.getValue() * toSpin / 360)));
//		animator.addListener(this);
		animator.start();
	}

	/*// //Override onTouchEvent() to handle the event by PolarLayout itself+++++++++++++++
	private float lastAngle = 0;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (getChildCount() == 0) {
			return false;
		}

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				startTouch(event);
				break;

			case MotionEvent.ACTION_MOVE:
				if (mTouchState == TOUCH_STATE_CLICK) {
					startScrollIfNeeded(event);
				}
				if (mTouchState == TOUCH_STATE_SCROLL) {
					mVelocityTracker.addMovement(event);
					scrollList((int) event.getY() - mTouchStartY);
				}
				break;

			case MotionEvent.ACTION_UP:
				float velocity = 0;
				if (mTouchState == TOUCH_STATE_CLICK) {
					clickChildAt((int) event.getX(), (int) event.getY());
				} else if (mTouchState == TOUCH_STATE_SCROLL) {
					mVelocityTracker.addMovement(event);
					mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND);
					velocity = mVelocityTracker.getYVelocity();
				}
				endTouch(velocity);
				break;

			default:
				endTouch(0);
				break;
		}
		return true;

		boolean result = false;

		// dumpEvent(v, event);
		final int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				pointerId0 = event.getPointerId(0);

				// To reset the secondary point id
				pointerId1 = INVALID_POINTER_ID;

				startPosition0.set(event.getX(), event.getY());
				// Log.d(TAG, "mode from " + mode + " to SINGLE");
				mode = TouchMode.SINGLE;
				break;

			case MotionEvent.ACTION_POINTER_DOWN:
				// Skip further handling when there is already a secondary
				// pointer recorded
				if ((pointerId1 != INVALID_POINTER_ID)
						|| (event.findPointerIndex(pointerId1) != -1)) {
					break;
				} else if (mode == TouchMode.DUAL) {
					Log.w(TAG, "mode=DUAL is not expected.");
					Log.w(TAG, "pointerId0=" + pointerId0 + ", pointerId1="
							+ pointerId1);
					dumpEvent(v, event);
					break;
				}

				result = initiatePointers(event);
				break;

			case MotionEvent.ACTION_UP:
				result = detectSinglePointAction(event);
				if (mode == TouchMode.DUAL) {
					if (onDetectedActionListener != null) {
						onDetectedActionListener
								.onDetectedAction(DetectedActionCode.DUAL_MODE_END);
					}
				}
				break;

			case MotionEvent.ACTION_POINTER_UP:
				// result = detectDualPointsAction(event);
				result = true;
				break;

			case MotionEvent.ACTION_MOVE:
				if (mode != TouchMode.DUAL) {
					break;
				}
				result = detectDualPointsMove(event);

				break;

			default:
				if (mode == TouchMode.DUAL) {
					dumpEvent(null, event);
				}
				break;
		}

		return result ? result : v.onTouchEvent(event);
	}
	//*/ //End of Override onTouchEvent() ------------------------------------

	/*/
	@Override
	public void onAnimationStart(Animator animation) {
	}

	@Override
	public void onAnimationEnd(Animator animation) {
		spinning = spinning % 360;
	}

	@Override
	public void onAnimationCancel(Animator animation) {
	}

	@Override
	public void onAnimationRepeat(Animator animation) {
	}

	@Override
	public boolean onDetectedAction(DetectedActionCode code, float change) {
		switch (code) {
			case LONG_PRESS:
				break;
			case PARALLEL_DOWN:
				break;
			case PARALLEL_LEFT:
				break;
			case PARALLEL_RIGHT:
				break;
			case PARALLEL_UP:
				break;
			case PRESS:
				break;
			case SPIN:
				spin(change);
				break;
			case TAP:
				break;
			case ZOOM_IN:
				break;
			case ZOOM_OUT:
				break;
			case NONE:
			default:
				break;
		}

		Log.i(TAG, code.toString() + ": " + change);
		// Toast.makeText(this.getContext(), code + " is detected " + change,
		// Toast.LENGTH_SHORT).show();
		return true;
	}
	//*/

}