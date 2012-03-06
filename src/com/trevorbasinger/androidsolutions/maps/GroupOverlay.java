package com.trevorbasinger.androidsolutions.maps;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Collection;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

public abstract class GroupOverlay<T extends OverlayItem> extends ItemizedOverlay<OverlayItem> {
  final public static int MIN_PER_GROUP = 3;

  protected Drawable groupMarker;
  protected Drawable defaultMarker;

  private int zoomLevel = -1;
  private ArrayList<T> modifiedList = new ArrayList<T>();
  private boolean refresh = false;
  
  public GroupOverlay(Drawable defaultMarker, Drawable groupMarker){
    super(defaultMarker);
    this.defaultMarker = defaultMarker;
    this.groupMarker = groupMarker;
  }

  @Override
  public void draw(Canvas canvas, MapView mapView, boolean shadow) {
    // if the zoom level has changed
    if((groupMarker != null && zoomLevel != mapView.getZoomLevel()) || refresh ) {
      refresh = false; // reset refresh to false
      zoomLevel = mapView.getZoomLevel(); // Set the new current zoom level
      // Might need to add exception handling for ConcurrentModificationExceptions
      groupMarkers(mapView.getProjection()); 
    }
    super.draw(canvas, mapView, shadow);
  }

  protected void refresh() {
    this.refresh = true;
  }

  protected synchronized void groupMarkers(Projection p) {
    // Create a new generic array list
    ArrayList<T> list = new ArrayList<T>();
    // Fill with generic items
    for(int i = 0; i < originalSize(); i++) {
      list.add(getOriginalItem(i));
    }

    // Create generic groups array
    ArrayList<ArrayList<T>> groups = new ArrayList<ArrayList<T>>();
    // Create generic individual array
    ArrayList<T> individuals = new ArrayList<T>();
    Iterator<T> it = list.iterator();

grouping:
    while(it.hasNext()) { // BEGIN GROUPING LOOP
      T item = it.next(); // Get item from the list iterator
      it.remove(); // Remove the last element returned from the iterator

      for(ArrayList<T> group : groups) { // For each group
        for(T groupItem : group) { // and every item in them
          // See if the original item intersects the group item
          if ( isClose(item, groupItem, p) ) {
            // If it does, add the item into the group
            group.add(item); 
            // And continue to the next iteration of the grouping loop
            continue grouping;
          }
        }
      }

      Iterator<T> iit = individuals.iterator();
      while(iit.hasNext()) { // For every individual item 
        /* It's important to get the individual item like this because
         * if we try to use a for loop we will get a 
         * ConcurrentModificationException when we remove it from
         * the individual group in the case of a match.
         */ 
        T indivItem = iit.next();
        // See if the two items intersect
        if( isClose(item, indivItem, p) ) {
          // Create a new group
          ArrayList<T> group = new ArrayList<T>();
          group.add(item); // Add the original item...
          group.add(indivItem); // ... and the individual item
          iit.remove(); // And prevent a CMExeption by removing the individual item
          groups.add(group); // Finally, add the group to the array of groups...
          continue grouping; // ... and continue
        }
      }

      // If no match is found then add to the list of individual items
      individuals.add(item);
    } // END GROUPING LOOP

    for(ArrayList<T> group : groups) {
      // If a group doesn't have the minimum amount of items
      if( group.size() < MIN_PER_GROUP ) { 
        for(T item : group) { // get each item from the group...
          individuals.add(item); // ... and add it to the individual array
        }
      } 
      else {
        GeoPoint avg = calcAvgGeo(group); // Calculate the avg Geopoint of the group
        T groupItem = createNewGroupItem(avg); // and create a new group item
        groupItem.setMarker(groupMarker); // Set the marker
        individuals.add(groupItem); // and add it to the individuals array
      }
    }

    modifiedList.clear(); // Clear the old list
    modifiedList.addAll(individuals); // Add the individual items into the modified list
    setLastFocusedIndex(-1);
    populate();
  }

  /** Methods for retrieving the original list of items */
  protected abstract void addOriginalItem(T item);
  protected abstract T getOriginalItem(int position);
  protected abstract int originalSize();
  protected abstract boolean tapped(final T item);
  protected abstract T createNewGroupItem(GeoPoint avg);

  /** Methods for describing the groups */
  protected abstract int minimumItemsPerGroup();

  /** Getters for drawables */
  public Drawable getDefaultMarker() { return defaultMarker; }
  public Drawable getGroupMarker() { return groupMarker; }

  /** Overrided methods from ItemizedOverlay */
  
  @Override
  public int size() {
    return modifiedList.size();
  }

  @Override
  protected T createItem(int i) {
    return modifiedList.get(i);
  }

  @Override
  protected boolean onTap(int index) {
    T item = modifiedList.get(index);
    tapped(item);
    return true;
  } 

  /** Helper methods */
  
  /**
   * Calculates the average X and Y coordinates in a list of
   * GeoPoints.
   *
   * @param List of overlayItems containing a GeoPoint
   * @return Average Geopoint of all coordinates
   */
  protected GeoPoint calcAvgGeo(Collection<T> pList){
    int latsum = 0;
    int lonsum = 0;
    for(T o : pList){
      latsum += o.getPoint().getLatitudeE6();
      lonsum += o.getPoint().getLongitudeE6();
    }
    int x = latsum / pList.size();
    int y = lonsum / pList.size();
    return new GeoPoint(x, y);
  }

  /**
   * Determines whether or not the point's circular
   * radii intersect eachother.
   */
  protected boolean isClose(T itemOne, T itemTwo, Projection p ){
    if(p == null) {
      return false;
    }

    if(defaultMarker == null) {
      return false;
    }

    int markerHeight = defaultMarker.getIntrinsicHeight();
    int markerWidth = defaultMarker.getIntrinsicWidth();

    Point pnt = p.toPixels(itemOne.getPoint(), null);
    Point ppnt = p.toPixels(itemTwo.getPoint(), null);

    if(pnt == null || ppnt == null){
      return false;
    }
    Rect r = new Rect(
        pnt.y - (markerHeight/2), 
        pnt.x - (markerWidth/2),
        pnt.y + (markerHeight/2),
        pnt.x + (markerWidth/2));

    Rect rr = new Rect(
        ppnt.y - (markerHeight/2), 
        ppnt.x - (markerWidth/2),
        ppnt.y + (markerHeight/2),
        ppnt.x + (markerWidth/2));
    return r.intersect(rr);
  }
}
