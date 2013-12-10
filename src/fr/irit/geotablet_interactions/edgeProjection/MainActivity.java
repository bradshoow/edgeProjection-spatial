package fr.irit.geotablet_interactions.edgeProjection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import fr.irit.edgeProjection.R;
import fr.irit.geotablet_interactions.common.MyMapView;
import fr.irit.geotablet_interactions.common.MyTTS;
import fr.irit.geotablet_interactions.common.OsmNode;

public class MainActivity extends Activity {
	private static final int TARGET_SIZE = 96; // Touch target size for on screen elements

	private MyMapView mapView;
	private Map<View, Set<OsmNode>> selectedItems = new HashMap<View, Set<OsmNode>>(2);
	private Map<View, Integer> isOutsideView = new HashMap<View, Integer>(2);
	float x = 0.0f, y = 0.0f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mapView = (MyMapView) findViewById(R.id.map_view);
		final Set<OsmNode> nodes = mapView.getNodes();

		// Initialize adapters
		final ArrayAdapter<Set<OsmNode>> verticalAdapter = new ArrayAdapter<Set<OsmNode>>(
				this, android.R.layout.simple_list_item_1, android.R.id.text1);
		final ArrayAdapter<Set<OsmNode>> horizontalAdapter = new ArrayAdapter<Set<OsmNode>>(
				this, android.R.layout.simple_list_item_1, android.R.id.text1);

		final LinearLayout verticalListLayout = (LinearLayout) findViewById(R.id.vertical_list_layout);
		final LinearLayout horizontalListLayout = (LinearLayout) findViewById(R.id.horizontal_list_layout);

		// Initialize selectedItems and isOutsideView maps
		selectedItems.put(verticalListLayout, null);
		selectedItems.put(horizontalListLayout, null);
		isOutsideView.put(verticalListLayout, ListTouchListener.OUTSIDE_VIEW);
		isOutsideView.put(horizontalListLayout, ListTouchListener.OUTSIDE_VIEW);

		mapView.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					private int i = 0;

					// Wait for display layout complete
					// to be able to use the method toPoint
					// (which uses map view properties)
					@SuppressWarnings({ "deprecation" })
					public void onGlobalLayout() {
						i++;

						// Perform after called twice - doesn't work otherwise -
						// don't know why
						if (i >= 2) {
							// Initialize lists for adapters
							int verticalItemsCount = (int) (verticalListLayout.getHeight() / TARGET_SIZE);
							initializeAdapaterList(verticalAdapter, verticalItemsCount);
							initializeAdapaterList(horizontalAdapter, (int) (horizontalListLayout.getWidth() / TARGET_SIZE));

							// Display vertical list :
							// Iterate through nodes, calculate index according
							// to their latitude, check it and add them to vertical adapter
							for (OsmNode n : nodes) {
								int verticalIndex = (int) (n.toPoint(mapView).y / TARGET_SIZE);
								if ((verticalIndex >= 0)
										&& (verticalIndex < verticalItemsCount)) {
									verticalAdapter.getItem(verticalIndex).add(n);
								}
							}

							// Display them
							displayAdapter(verticalAdapter, verticalListLayout,
									new LayoutParams(LayoutParams.MATCH_PARENT, TARGET_SIZE));

							try {
								mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
							} catch (NoSuchMethodError x) {
								mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
							}
						}
					}
				});

		// Set listener to the layouts
		verticalListLayout.setOnTouchListener(
				new ListTouchListener(this, verticalAdapter, ListTouchListener.VERTICAL_DIRECTION) {

			@Override
			public boolean onTouch(View v, MotionEvent ev) {
				if (isOutsideView.get(horizontalListLayout) == OUTSIDE_VIEW) {
					// If horizontal list is not touched, listen to vertical list by setting adapter
					if (getAdapter() == null) {
						setAdapter(verticalAdapter);
					}

					// Display horizontal list :
					// Clear all previous nodes added
					for (int i = 0; i < horizontalAdapter.getCount(); i++) {
						horizontalAdapter.getItem(i).clear();
					}

					// Iterate through nodes, calculate index according
					// to their latitude, check it and add them to horizontal adapter if selected
					for (OsmNode n : nodes) {
						if ((selectedItems.get(verticalListLayout) != null)
								&& selectedItems.get(verticalListLayout).contains(n)) {
							int horizontalIndex = (int) (n.toPoint(mapView).x / TARGET_SIZE);
							if ((horizontalIndex >= 0)
									&& (horizontalIndex < (int) (horizontalListLayout.getWidth() / TARGET_SIZE))) {
								horizontalAdapter.getItem(horizontalIndex).add(n);
							}
						}
					}

					// Display them
					horizontalListLayout.removeAllViews();
					displayAdapter(horizontalAdapter, horizontalListLayout,
							new LayoutParams(TARGET_SIZE, LayoutParams.MATCH_PARENT));
				} else {
					// Else set adapter to null so that vertical list can't change while touching horizontal list
					setAdapter(null);
				}

				if (isOutsideView.get(v) == OUTSIDE_AND_TOUCHING_VIEW) {
					// Simulate on touch event on the map view
					// so that the user is able to slip directly
					// from list to point of interest (without raising finger)
					onTouchMapView(v, x - v.getWidth(), y); // Calculate x from top left of map view
				}

				return super.onTouch(v, ev);
			}

		});

		horizontalListLayout.setOnTouchListener(
				new ListTouchListener(this, horizontalAdapter, ListTouchListener.HORIZONTAL_DIRECTION) {

			@Override
			public boolean onTouch(View v, MotionEvent ev) {
				if (isOutsideView.get(v) == OUTSIDE_AND_TOUCHING_VIEW) {
					// Same as above
					onTouchMapView(v, x, mapView.getHeight() - Math.abs(y)); // Calculate y from top left of map view
				}
				return super.onTouch(v, ev);
			}

		});

		// Set listener to the map view
		mapView.setOnTouchListener(new OnTouchListener() {
			private static final int INVALID_POINTER_ID = -1;

			private int activePointerId;
			
			public boolean onTouch(View v, MotionEvent ev) {
				int action = MotionEventCompat.getActionMasked(ev);

				switch (action) {
				case MotionEvent.ACTION_DOWN: {
					// Save the ID of this pointer (for dragging)
					activePointerId = MotionEventCompat.getPointerId(ev, 0);
					int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
					onTouchMapView(null, MotionEventCompat.getX(ev, pointerIndex), MotionEventCompat.getY(ev, pointerIndex));
					break;
				}

				case MotionEvent.ACTION_MOVE: {
					// Find the index of the active pointer and fetch its position
					int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
					onTouchMapView(null, MotionEventCompat.getX(ev, pointerIndex), MotionEventCompat.getY(ev, pointerIndex));
					break;
				}

				case MotionEvent.ACTION_UP: {
					activePointerId = INVALID_POINTER_ID;
					MyTTS.getInstance(getApplicationContext()).stop();
					break;
				}

				case MotionEvent.ACTION_CANCEL: {
					activePointerId = INVALID_POINTER_ID;
					break;
				}

				case MotionEvent.ACTION_POINTER_UP: {

					int pointerIndex = MotionEventCompat.getActionIndex(ev);
					int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

					if (pointerId == activePointerId) {
						// This was our active pointer going up. Choose a new
						// active pointer and adjust accordingly.
						int newPointerIndex = pointerIndex == 0 ? 1 : 0;
						activePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
					}

					break;
				}
				}
				return true;
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onDestroy() {
		MyTTS.release();
		super.onDestroy();
	}

	/**
	 * Getter for selected item in the list (to be guided to)
	 * 
	 * @return The selected item
	 */
	public Object getSelectedItem(View v) {
		return selectedItems.get(v);
	}

	/**
	 * Setter for selected item in the list (to be guided to)
	 * 
	 * @param selectedItems
	 *            The selected item
	 */
	public void setSelectedItem(View v, Set<OsmNode> selectedItem) {
		selectedItems.put(v, selectedItem);
	}

	/**
	 * For a view, set if no more finger inside, in the isOutsideView map, and set coordinates it not
	 * 
	 * @param v
	 *            The view
	 * @param outside
	 *            Can be outside, outside and touching, or inside
	 *            @see ListTouchListener
	 * @param x
	 *            The x coordinate
	 * @param y
	 *            The y coordinate
	 */
	public void onViewLeft(View v, int outside, float x, float y) {
		if ((outside != ListTouchListener.OUTSIDE_VIEW)
				&& (outside != ListTouchListener.OUTSIDE_AND_TOUCHING_VIEW)
				&& (outside != ListTouchListener.INSIDE_VIEW)) {
			throw new IllegalArgumentException("Invalid direction");
		}

		if (isOutsideView.get(v) != outside) {
			isOutsideView.put(v, outside);
		}

		if (outside != ListTouchListener.OUTSIDE_VIEW) {
			this.x = x;
			this.y = y;
		}
	}

	@SuppressWarnings("serial")
	private void initializeAdapaterList(ArrayAdapter<Set<OsmNode>> adapter, int itemsCount) {
		List<Set<OsmNode>> verticalNodesArray = new ArrayList<Set<OsmNode>>(itemsCount);
		for (int i = 0; i < itemsCount; i++) {
			verticalNodesArray.add(new HashSet<OsmNode>() {

				@Override
				public String toString() {
					return formatListForTts(super.toString());
				}

			});
		}
		adapter.addAll(verticalNodesArray);
	}

	private String formatListForTts(String str) {
		str = str.replace("[", "");
		str = str.replace("]", "");
		return str;
	}

	private void displayAdapter(ArrayAdapter<Set<OsmNode>> adapter, ViewGroup v, LayoutParams layoutParams) {
		for (int i = 0; i < adapter.getCount(); i++) {
			View adapterView = adapter.getView(i, null, null);
			adapterView.setLayoutParams(layoutParams);
			v.addView(adapterView);
		}
	}

	//Mathieu's code's transformations to display all osmnode	
	private void onTouchMapView(View v, float x, float y) {
		//retrieve all nodes
		final Set<OsmNode> nodes = mapView.getNodes();
		//retrieve selected node
		Set<OsmNode> selectedNodes = new HashSet<OsmNode>();
		if ((v != null) && ((v.getId() == R.id.vertical_list_layout) || (v.getId() == R.id.horizontal_list_layout))) {
			selectedNodes = selectedItems.get(v);
		} else {
			for (Set<OsmNode> allSelectedNodes : selectedItems.values()) {
				if (allSelectedNodes != null) {
					selectedNodes.addAll(allSelectedNodes);
				}
			}
		}
				
		for (OsmNode n : nodes) {
			if ((n.toPoint(mapView).y <= y + TARGET_SIZE / 2)
					&& (n.toPoint(mapView).y >= y - TARGET_SIZE / 2)
					&& (n.toPoint(mapView).x <= x + TARGET_SIZE / 2)
					&& (n.toPoint(mapView).x >= x - TARGET_SIZE / 2)) {
				if (  !MyTTS.getInstance(this).isSpeaking()  
						&&  (selectedNodes.toString()).contains(n.getName()) 
						) {
					//((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(150);
					MyTTS.getInstance(this).setPitch(1.6f);
					MyTTS.getInstance(this).speak(
							getResources().getString(R.string.found) + 
							n.getName(),
							TextToSpeech.QUEUE_ADD,
							null);
				}
				else if (!MyTTS.getInstance(this).isSpeaking()
						) {
					//((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(150);
					MyTTS.getInstance(this).setPitch(1.6f);
					MyTTS.getInstance(this).speak(
							n.getName(),
							TextToSpeech.QUEUE_ADD,
							null);
				}
			}
		}	
	}
	
	
	//Hélène's code to only display one osmnode
//	private void onTouchMapView(View v, float x, float y) {
//		Set<OsmNode> selectedNodes = new HashSet<OsmNode>();
//		if ((v != null) && ((v.getId() == R.id.vertical_list_layout) || (v.getId() == R.id.horizontal_list_layout))) {
//			selectedNodes = selectedItems.get(v);
//		} else {
//			for (Set<OsmNode> allSelectedNodes : selectedItems.values()) {
//				if (allSelectedNodes != null) {
//					selectedNodes.addAll(allSelectedNodes);
//				}
//			}
//		}
//		if (selectedNodes != null) {
//			for (OsmNode n : selectedNodes) {
//				if ((n.toPoint(mapView).y <= y + TARGET_SIZE / 2)
//						&& (n.toPoint(mapView).y >= y - TARGET_SIZE / 2)
//						&& (n.toPoint(mapView).x <= x + TARGET_SIZE / 2)
//						&& (n.toPoint(mapView).x >= x - TARGET_SIZE / 2)) {
//					if (!MyTTS.getInstance(this).isSpeaking()) {
//						String from = "";
//						if (v != null) {
//							from = " " + getResources().getString(R.string.finger) + " ";
//							if (v.getId() == R.id.vertical_list_layout) {
//								from += getResources().getString(R.string.left);
//							}
//							if (v.getId() == R.id.horizontal_list_layout) {
//								from += getResources().getString(R.string.bottom);
//							}
//						}
//						((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(150);
//						MyTTS.getInstance(this).speak(
//								getResources().getString(R.string.found) + " " + n.getName() + from,
//								TextToSpeech.QUEUE_ADD,
//								null);
//					}
//				}
//			}
//		}
//	}

}
