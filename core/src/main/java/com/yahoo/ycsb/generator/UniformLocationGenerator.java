package com.yahoo.ycsb.generator;

import java.util.Random;

public class UniformLocationGenerator extends LocationGenerator {

  private double minLatitude;
  private double maxLatitude;
  private double minLongitude;
  private double maxLongitude;

  private Random latRandom;
  private Random lngRandom;

  public UniformLocationGenerator (double minLat, double maxLat, double minLng, double maxLng) {
    this.latRandom = new Random ();
    this.lngRandom = new Random ();
    setMinLatitude (minLat);
    setMinLongitude (minLng);
    setMaxLatitude (maxLat);
    setMaxLongitude (maxLng);
   }

  @Override
  public Location nextValue() {
    double lat = this.minLatitude + (this.maxLatitude - this.minLatitude) * latRandom.nextDouble();
    double lng = this.minLongitude + (this.maxLongitude - this.minLongitude) * lngRandom.nextDouble();

    return new Location (lat,lng);
  }

  public void setMinLatitude (double minLat) {
    this.minLatitude = minLat;
  }

  public void setMinLongitude (double minLng) {
    this.minLongitude = minLng;
  }

  public void setMaxLatitude (double maxLat) {
    this.maxLatitude = maxLat;
  }

  public void setMaxLongitude (double maxLng) {
    this.maxLongitude = maxLng;
  }

  

}
