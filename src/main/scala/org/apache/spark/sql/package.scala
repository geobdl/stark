package org.apache.spark.sql

import org.apache.spark.sql.raster.TileUDT

package object raster {
  def registerUDTs(): Unit = {
    // Referencing the companion objects here is intended to have it's constructor called,
    // which is where the registration actually happens.
    TileUDT
  }
}