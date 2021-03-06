package dbis.stark.spatial.partitioner

import dbis.stark.STObject
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

object PartitionStrategy extends Enumeration {
  type PartitionStrategy = Value
  val GRID, BSP = Value
}
import PartitionStrategy._


abstract class PartitionerConfig(val strategy: PartitionStrategy,
                                 pointsOnly: Boolean,
                                 minmax: Option[(Double, Double, Double, Double)]
                                ) extends Serializable

case class BSPStrategy(cellSize: Double,
                       maxCost: Double,
                       pointsOnly: Boolean = false,
                       minmax: Option[(Double, Double, Double, Double)] = None
                      ) extends PartitionerConfig(PartitionStrategy.BSP, pointsOnly, minmax)

case class GridStategy(partitionsPerDimensions: Int,
                       pointsOnly: Boolean = false,
                       minmax: Option[(Double, Double, Double, Double)] = None
                      ) extends PartitionerConfig(PartitionStrategy.GRID, pointsOnly, minmax)


object PartitionerFactory {
  def get[G <: STObject : ClassTag, V : ClassTag](strategy: PartitionerConfig, rdd: RDD[(G, V)]) = strategy match {
    case BSPStrategy(cellSize, maxCost, pointsOnly, minmax) => minmax match {
      case None => new BSPartitioner(rdd, cellSize, maxCost, pointsOnly)
      case Some(mm) => new BSPartitioner(rdd, cellSize, maxCost, pointsOnly, mm)
    }

    case GridStategy(partitionsPerDimensions, pointsOnly, minmax) => minmax match {
      case None => new SpatialGridPartitioner[G,V](rdd, partitionsPerDimensions, pointsOnly)
      case Some(mm) => new SpatialGridPartitioner[G,V](rdd, partitionsPerDimensions, pointsOnly, mm, dimensions = 2)
    }


  }
}


