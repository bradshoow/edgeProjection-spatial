package fr.irit.geotablet_interactions.edgeProjection;

import java.util.Set;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Adapter;
import fr.irit.geotablet_interactions.common.MyTTS;
import fr.irit.geotablet_interactions.common.OsmNode;

/**
 * Listener to scroll with finger(s) and read with TTS a list from an adapter
 * 
 * @author helene jonin
 * @mail helene.jonin@gmail.com
 * 
 */
public class ListTouchListener implements OnTouchListener {
	public static final int VERTICAL_DIRECTION = 1; // Means view is vertically displayed
	public static final int HORIZONTAL_DIRECTION = 2; // Means view is horizontally displayed
	public static final int INSIDE_VIEW = 0; // Means at least 1 finger is inside view
	public static final int OUTSIDE_AND_TOUCHING_VIEW = 1; // Means at least 1 finger is outside view but still touching
	public static final int OUTSIDE_VIEW = 2; // Means all fingers are outside view

	private static final int INVALID_POINTER_ID = -1;

	private Context context;
	private Adapter adapter;
	private int direction;
	private int lastAdapterIndex;
	private int activePointerId;

	/**
	 * Constructor
	 * 
	 * @param context
	 *            The context
	 * @param adapter
	 *            An adapter (whatever adapter ?)
	 * @param direction
	 *            The view direction (only 2 possible directions for now)
	 */
	public ListTouchListener(Context context, Adapter adapter, int direction) {
		super();

		if ((direction != VERTICAL_DIRECTION)
				&& (direction != HORIZONTAL_DIRECTION)) {
			throw new IllegalArgumentException("Invalid direction");
		}

		this.context = context;
		this.adapter = adapter;
		this.direction = direction;
		this.lastAdapterIndex = -1;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean onTouch(View v, MotionEvent ev) {
		int action = MotionEventCompat.getActionMasked(ev);

		switch (action) {
		case MotionEvent.ACTION_DOWN: {
			// Save the ID of this pointer (for dragging)
			activePointerId = MotionEventCompat.getPointerId(ev, 0);

			((MainActivity) context).onViewLeft(v, INSIDE_VIEW, 0.0f, 0.0f);

			break;
		}

		case MotionEvent.ACTION_MOVE: {
			// Find the index of the active pointer and fetch its position
			int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);

			float y = MotionEventCompat.getY(ev, pointerIndex);
			float x = MotionEventCompat.getX(ev, pointerIndex);

			// Take paddings into account
			if (v.getPaddingTop() > 0) {
				y -= v.getPaddingTop();
			}
			if (v.getPaddingLeft() > 0) {
				x -= v.getPaddingLeft();
			}

			// Speak if inside view
			if (((direction == VERTICAL_DIRECTION) && (x <= v.getWidth()))
					|| ((direction == HORIZONTAL_DIRECTION) && (y >= 0))) {

				if (adapter != null) {
					// Calculate adapter index to say
					int adapterIndex = direction == VERTICAL_DIRECTION ?
							(int) (y / (v.getHeight() / adapter.getCount())) :
							(int) (x / (v.getWidth() / adapter.getCount()));

					// If the index is valid (>= 0 and less than items count),
					// and different from the last one said or the TTS is not
					// speaking
					if ((adapterIndex >= 0)
							&& (adapterIndex < adapter.getCount())
							&& (lastAdapterIndex != adapterIndex
								|| !MyTTS.getInstance(context).isSpeaking())) {
						Object selectedItem = adapter.getItem(adapterIndex);
//						if (!selectedItem.toString().isEmpty()) {
//							((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(150);
//						}
						MyTTS.getInstance(context).setPitch(0.4f);
						MyTTS.getInstance(context).speak(
								selectedItem.toString(),
								TextToSpeech.QUEUE_FLUSH, null);
						if (!selectedItem.toString().isEmpty()) {
							((MainActivity) context).setSelectedItem(v, (Set<OsmNode>) selectedItem);
						}
						lastAdapterIndex = adapterIndex;
					}
				}
			} else {
				((MainActivity) context).onViewLeft(v, OUTSIDE_AND_TOUCHING_VIEW, x, y);
			}

			break;
		}

		case MotionEvent.ACTION_UP: {
			activePointerId = INVALID_POINTER_ID;
			((MainActivity) context).onViewLeft(v, OUTSIDE_VIEW, 0.0f, 0.0f);
			break;
		}

		case MotionEvent.ACTION_CANCEL: {
			activePointerId = INVALID_POINTER_ID;
			((MainActivity) context).onViewLeft(v, OUTSIDE_VIEW, 0.0f, 0.0f);
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

	public Adapter getAdapter() {
		return adapter;
	}

	public void setAdapter(Adapter adapter) {
		this.adapter = adapter;
	}

}
