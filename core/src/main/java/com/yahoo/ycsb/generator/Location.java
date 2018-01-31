package com.yahoo.ycsb.generator;

public class Location {

  private double latitude;
  private double longitude;

  public Location (double lat, double lng) {
    setLatitude (lat);
    setLongitude (lng);
  }

  public double getLatitude() {
    return this.latitude;
  }

  public double getLongitude() {
    return this.longitude;
  }

  public void setLatitude(double lat) {
    this.latitude = lat;
  }

  public void setLongitude(double lng) {
    this.longitude = lng;
  }
}
