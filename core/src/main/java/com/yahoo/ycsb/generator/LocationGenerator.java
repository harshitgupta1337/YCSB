package com.yahoo.ycsb.generator;

public abstract class LocationGenerator extends Generator<Location> 
{
  private Location lastValue;

  protected void setLastValue (Location last) {
    this.lastValue = last;
  }

  @Override
  public Location lastValue() {
    return this.lastValue;
  }
}
