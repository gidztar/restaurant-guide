package uk.ac.aston.pyzer.restaurantguide.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.ac.aston.pyzer.restaurantguide.model.Place.Photo;
import uk.ac.aston.pyzerg.restaurantguide.R;
import uk.ac.aston.pyzerg.restaurantguide.TouristHttpTransport;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;

public class PlaceOverlay extends ItemizedOverlay<MyPlaceOverlayItem> {

	private static final int KOEFF = 20;
	private ArrayList<MyPlaceOverlayItem> myOverlaysAll = new ArrayList<MyPlaceOverlayItem>();
	private ArrayList<MyPlaceOverlayItem> myOverlays = new ArrayList<MyPlaceOverlayItem>();
	private MapView mapView;
	private Bitmap bitmap;
	private Paint circlePaint;
	private Point centerSP;
	private Bitmap image;
	private String photoRef;
	private Place place;
	private PlaceDetail placeDetail;
	
	private ImageView favouriteIcon;
	private boolean alreadyFavourite;

	public PlaceOverlay(Drawable defaultMarker, MapView mapView) {
		super(boundCenterBottom(defaultMarker));
		this.mapView = mapView;
		bitmap = ((BitmapDrawable) defaultMarker).getBitmap();
		populate();
	}

	public void addOverlay(MyPlaceOverlayItem overlay) {
		myOverlaysAll.add(overlay);
		myOverlays.add(overlay);

	}

	public void doPopulate() {
		populate();
		setLastFocusedIndex(-1);

	}

	@Override
	protected MyPlaceOverlayItem createItem(int i) {
		return myOverlays.get(i);
	}

	@Override
	public int size() {
		return myOverlays.size();
	}

	public MapView getMapView() {
		return this.mapView;
	}

	// check to see if items/groups are overlapping
	private boolean isImposition(MyPlaceOverlayItem item1,
			MyPlaceOverlayItem item2) {

		// get the width of the map in pixels
		int latspan = mapView.getLatitudeSpan();
		// find out minimum distance between pins based on width and no. of pins
		// per view
		int delta = latspan / KOEFF;

		// work out the x and y distances
		int dx = item1.getPoint().getLatitudeE6()
				- item2.getPoint().getLatitudeE6();
		int dy = item1.getPoint().getLongitudeE6()
				- item2.getPoint().getLongitudeE6();

		// calculate the distance between the two points using pythag
		double dist = Math.sqrt(dx * dx + dy * dy);

		// if the distance between the points is within the minimum distance
		// range,
		// then there is overlapping
		if (dist < delta) {
			return true;
		} else {
			return false;
		}

	}

	public void clear() {
		myOverlaysAll.clear();
		myOverlays.clear();
	}

	public void calculateItems() {

		myOverlaysClear();

		boolean isImposition;

		// loop through all the items (an item may be single, or have a list of
		// items/places)
		for (MyPlaceOverlayItem itemFromAll : myOverlaysAll) {
			isImposition = false;
			// loop through all the items/groups again
			// using a sep list so that we can modify
			for (MyPlaceOverlayItem item : myOverlays) {
				// if the items/groups match, then we have the item already
				// so we don't need to do anything except break out the loop
				if (itemFromAll == item) {
					isImposition = true;
					break;

				}
				// if there is overlapping between the items/groups
				// then we will need to add the item to the group
				if (isImposition(itemFromAll, item)) {
					item.addList(itemFromAll);
					isImposition = true;
					break;
				}
			}

			// if there is no overlapping, then we will need to add this new
			// item/group to the list
			if (!isImposition) {
				myOverlays.add(itemFromAll);
			}
		}

		doPopulate();

	}

	private void myOverlaysClear() {
		for (MyPlaceOverlayItem item : myOverlaysAll) {
			item.getList().clear();

		}
		myOverlays.clear();

	}

	@Override
	protected boolean onTap(int index) {

		// if there are no details available, it is because
		// there is overlapping so user must zoom more
		if (!myOverlays.get(index).placeDetailsAvailable()) {
			Toast.makeText(
					mapView.getContext(),
					"There are " + (myOverlays.get(index).getList().size() + 1)
							+ " places.", Toast.LENGTH_SHORT).show();
		} else {
			// display the place details
			Resources res = mapView.getContext().getResources();
			placeDetail = null;
			// we look up the extended details to get the formatted address
			placeDetail = PlaceDetail.getPlaceDetail(myOverlays.get(index)
					.getPlace(), res);
			place = myOverlays.get(index).getPlace();
			// create the dialog to show the place details
			AlertDialog.Builder dialog = new AlertDialog.Builder(
					mapView.getContext());

			// get the layout inflater
			LayoutInflater factory = LayoutInflater.from(mapView.getContext());
			// get the dialog view
			final View dialogView = factory.inflate(R.layout.dialog_layout,
					null);

			// set the dialog to use this custom view
			dialog.setView(dialogView);

			// get the custom dialog title and set the text
			RelativeLayout titleView = (RelativeLayout) factory.inflate(R.layout.custom_title_dialog_box, null);
			TextView tv = (TextView) titleView.findViewById(R.id.custom_dialog_title);
			tv.setText(myOverlays.get(index).getPlace().getName());
			
			favouriteIcon = (ImageView) titleView.findViewById(R.id.custom_dialog_favourite);
			favouriteIcon.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					addToFavourites(v);
				}
			});
			
			ImageView sendToFriend = (ImageView) titleView.findViewById(R.id.custom_dialog_send);
			sendToFriend.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent sendIntent = new Intent(Intent.ACTION_VIEW);
					sendIntent.setData(Uri.parse("sms:"));
					sendIntent.putExtra("sms_body", placeDetail.getResult()
							.getName()
							+ "\n\n"
							+ placeDetail.getResult().getVicinity()
							+ "\n"
							+ placeDetail.getResult().getFormatted_phone_number());
					v.getContext().startActivity(sendIntent);
				}
				
			});
			
			if (isAlreadyFavourite()) {
				favouriteIcon.setImageResource(R.drawable.favourite_on);
				alreadyFavourite = true;
			}
			
			// add the custom title to the dialog
			dialog.setCustomTitle(titleView);
			
			if (placeDetail != null) {
				int rating = 0;
				int reviews = 0;

				if (placeDetail.getResult().getRating() != 0) {
					rating = placeDetail.getResult().getRating();
					reviews = placeDetail.getResult().getReviews().size();
				}
				// get the photos list
				List<Photo> photos = new ArrayList<Photo>();
				photos = placeDetail.getResult().getPhotos();

				// assuming that photos were sent back
				// get the first photo reference
				if (photos != null && photos.size() > 0) {
					photoRef = photos.get(0).getPhoto_reference();

					class GoogleRequest extends AsyncTask<Void, Void, Integer> {

						@Override
						protected Integer doInBackground(Void... params) {
							HttpRequestFactory hrf = TouristHttpTransport
									.createRequestFactory();
							try {
								// send the photo reference to the photos API
								HttpRequest request = hrf
										.buildGetRequest(new GenericUrl(
												mapView.getResources()
														.getString(
																R.string.places_photos_url)));
								request.getUrl().put(
										"key",
										mapView.getResources().getString(
												R.string.google_places_key));
								request.getUrl()
										.put("photoreference", photoRef);
								request.getUrl().put("sensor", true);
								request.getUrl().put("maxheight", 400);

								// get the photo back as a bitmap
								image = BitmapFactory.decodeStream(request
										.execute().getContent());

							} catch (IOException e) {
								Log.e("IOException",
										"Photo request failed" + e.getMessage());
							}
							return null;
						}

						@Override
						protected void onPostExecute(Integer result) {
							if (image != null) {
								// get the layout from the dialog view
								LinearLayout ll = (LinearLayout) dialogView
										.findViewById(R.id.dialogLayout);
								// get the image view from layout
								// ImageView imageView = (ImageView)
								// ll.findViewById(R.id.placePhoto);
								ImageView imageView = new ImageView(
										ll.getContext());
								// set the bitmap as the image source
								imageView.setImageBitmap(image);

								ll.addView(imageView);
							}
						}

					}

					GoogleRequest googleRequest = new GoogleRequest();
					googleRequest.execute();

				}
				
				LinearLayout dialogLayout = (LinearLayout) dialogView
						.findViewById(R.id.dialogLayout);
				
				TextView message = (TextView) dialogLayout.findViewById(R.id.dialogMessage);
				
				message.setText(placeDetail.getResult().getVicinity() + "\n"
						+ placeDetail.getResult().getFormatted_phone_number()
						+ "\n\nRating (based on " + reviews + " reviews):");
				
				LinearLayout ratingsLayout = (LinearLayout) dialogLayout.findViewById(R.id.dialogRatings);
				
				for (int i = 0; i < rating; i++) {
					ImageView star = new ImageView(ratingsLayout.getContext());
					star.setImageResource(R.drawable.favourite_on_small);
					ratingsLayout.addView(star);
				}
				
				if (rating < 5) {
					for (int i = 0; i < (5-rating); i++) {
						ImageView star = new ImageView(ratingsLayout.getContext());
						star.setImageResource(R.drawable.favourite_off_small);
						ratingsLayout.addView(star);
					}
				}
				
				
			} else {
				dialog.setMessage("No address");
			}
			dialog.setNegativeButton("Close",
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});

			dialog.show();
		}

		return true;
	}

	public void draw(Canvas canvas, MapView mapview, boolean b) {
		// create a
		// Point centerSP = new Point();
		if (!objectExists(centerSP)) {
			centerSP = new Point();
		}

		mapview.getProjection().toPixels(mapview.getMapCenter(), centerSP);

		if (!objectExists(circlePaint)) {
			circlePaint = new Paint();
			circlePaint.setColor(0x55ff0000);
			circlePaint.setAlpha(10);
			circlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
		}
		// set up a paint for the circles
		// Paint circlePaint = new Paint();
		// circlePaint.setColor(0x55ff0000);
		// circlePaint.setAlpha(10);
		// circlePaint.setStyle(Paint.Style.FILL_AND_STROKE);

		// find out the width and height of the view
		int width = mapview.getWidth();
		int height = mapview.getHeight();

		for (MyPlaceOverlayItem p : this.myOverlays) {

			Point screenPts = new Point();
			mapview.getProjection().toPixels(p.getPoint(), screenPts);

			// check if this item is off screen or not
			if (screenPts.x > centerSP.x + (width / 2)
					|| screenPts.x < centerSP.x - (width / 2)
					|| screenPts.y > centerSP.y + (height / 2)
					|| screenPts.y < centerSP.y - (height / 2)) {
				// screenPts is offscreen
				// pythagoras theorum to calculate the distance of this item
				// from the center (my location)
				double dx = Math.abs(centerSP.x - screenPts.x);
				double dy = Math.abs(centerSP.y - screenPts.y);

				int padding = 30;

				double ox = dx - ((width / 2) - padding);
				double oy = dy - ((height / 2) - padding);

				if (ox < 0)
					ox = 0;
				if (oy < 0)
					oy = 0;

				double distanceSP = Math.sqrt((ox * ox) + (oy * oy));

				// if distance in pixels is > 1000, we won't draw the circles
				// improves performance
				if (distanceSP <= 1000) {
					// draw a circle around this item centered at its location
					drawCircle(screenPts, (float) distanceSP, circlePaint,
							canvas);
				}

				canvas.drawBitmap(bitmap, screenPts.x - 20, screenPts.y - 43,
						null);
			} else {
				canvas.drawBitmap(bitmap, screenPts.x - 20, screenPts.y - 43,
						null);
			}
		}
	}

	private void drawCircle(Point location, float radius, Paint paint,
			Canvas canvas) {
		// set the width of the line
		paint.setStrokeWidth((float) 3.0);
		// draw a circle radius 100 pixels
		canvas.drawCircle(location.x, location.y, radius, paint);
	}

	private boolean objectExists(Object o) {
		if (o != null) {
			return true;
		}

		return false;
	}

	// add the selected place to the favourites list
	public void addToFavourites(View v) {
		if (!alreadyFavourite) {
			AlertDialog.Builder alert = new AlertDialog.Builder(
					mapView.getContext());
	
			alert.setTitle("Added to favourites!");
			alert.setMessage("Add some notes too:");
	
			// Set an EditText view to get user input
			final EditText input = new EditText(mapView.getContext());
			alert.setView(input);
	
			alert.setPositiveButton("Save",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							String value = input.getText().toString();
							// add the item to the favourites list
							FavouritesList
									.getInstance()
									.getPlaces()
									.add(new FavouritePlace(place,
											value));
						}
					});
	
			alert.setNegativeButton("No Thanks!",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							// add the item to the favourites list
							FavouritesList
									.getInstance()
									.getPlaces()
									.add(new FavouritePlace(place,
											"None"));
						}
					});
	
			alert.show();
	
			favouriteIcon.setImageResource(R.drawable.favourite_on);
			alreadyFavourite = true;
		} else {
			Toast.makeText(v.getContext(), "Already a favourite", Toast.LENGTH_SHORT).show();
		}
	}
	
	// check if the selected place is already in the favourites list
	private boolean isAlreadyFavourite() {
		alreadyFavourite = false;
		for (FavouritePlace fp : FavouritesList.getInstance().getPlaces()) {
			if (fp.getPlace().getName().equals(place.getName())) {
				alreadyFavourite = true;
				
				return alreadyFavourite;
			}
		}
		
		return false;
	}

}