package dbis.stark.spatial

import java.io.ObjectOutputStream

import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag
import dbis.stark.STObject
import org.apache.spark.Partition


case class SpatialPartition(private val idx: Int, origIndex: Int, @transient private val rdd: RDD[_]) extends Partition {
  override def index: Int = idx

  var split = rdd.partitions(origIndex)

  private def writeObject(oos: ObjectOutputStream): Unit = {
    split = rdd.partitions(origIndex)
    oos.defaultWriteObject()
  }
}

object SpatialGridPartitioner {

  /**
    * Compute the cell bounds for the given cell id
    * @param id The ID of the cell to compute the bounds for
    * @param numPartitions The total number of partitions
    * @param partitionsPerDimension Number of partitions per dimensions
    * @param minX Minimum x value
    * @param minY Minimum y value
    * @param xLength Length of a cell on x axis
    * @param yLength Length of a cell on y axis
    * @return Returns the cell object for the given ID. The cell object contains the bounds
    */
  protected[spatial] def getCellBounds(id: Int, numPartitions: Int, partitionsPerDimension: Int, minX: Double, minY: Double, xLength: Double, yLength: Double): Cell = {
    
    require(id >= 0 && id < numPartitions, s"Invalid cell id (0 .. $numPartitions): $id")
    
    val dy = id / partitionsPerDimension
    val dx = id % partitionsPerDimension
    
    val llx = dx * xLength + minX
    val lly = dy * yLength + minY
    
    val urx = llx + xLength
    val ury = lly + yLength
      
    Cell(id, NRectRange(NPoint(llx, lly), NPoint(urx, ury)))
  }
  
  private def getCellId(_x: Double, _y: Double, minX: Double, minY: Double, maxX: Double, maxY: Double, xLength: Double, yLength:Double, partitionsPerDimension: Int): Int = {
        require(_x >= minX && _x <= maxX || _y >= minY || _y <= maxY, s"(${_x},${_y}) out of range!")
    
    val x = math.floor(math.abs(_x - minX) / xLength).toInt
    val y = math.floor(math.abs(_y - minY) / yLength).toInt
    
    val cellId = y * partitionsPerDimension + x
    
    cellId
  }    
  
}


/**
 * A grid partitioner that simply applies a grid to the data space.
 * 
 * The grid is applied from the lower left point (xmin, ymin) to the (xmax + 1, ymax + 1)
 * so that we only have grid cells over potentially filled space.  
 * 
 * @author hage
 * 
 * @param partitionsPerDimension The number of partitions per dimension. This results in ppD to the power of dimension partitions
 * @param rdd The [[org.apache.spark.rdd.RDD]] to partition
 * @param dimensions The dimensionality of the input data 
 */
class SpatialGridPartitioner[G <: STObject : ClassTag, V: ClassTag](
    rdd: RDD[(G,V)],
    partitionsPerDimension: Int,
    withExtent: Boolean,
    _minX: Double,
    _maxX: Double,
    _minY: Double,
    _maxY: Double,
    dimensions: Int) extends SpatialPartitioner(_minX, _maxX, _minY, _maxY) {
  
  require(dimensions == 2, "Only 2 dimensions supported currently")
  
  def this(rdd: RDD[(G,V)],
      partitionsPerDimension: Int,
      withExtent: Boolean,
      minMax: (Double, Double, Double, Double),
      dimensions: Int) = 
    this(rdd, partitionsPerDimension, withExtent, minMax._1, minMax._2, minMax._3, minMax._4, dimensions)  
  
  def this(rdd: RDD[(G,V)],
      partitionsPerDimension: Int,
      withExtent: Boolean = false,
      dimensions: Int = 2) = 
    this(rdd, partitionsPerDimension, withExtent, SpatialPartitioner.getMinMax(rdd), dimensions)
  
  
  protected[this] val xLength: Double = math.abs(maxX - minX) / partitionsPerDimension
  protected[this] val yLength: Double = math.abs(maxY - minY) / partitionsPerDimension
  
//  new Array[Cell](numPartitions) //Map.empty[Int, Cell]
  private val partitions = {
    val arr = Array.tabulate(numPartitions){ i => SpatialGridPartitioner.getCellBounds(i, numPartitions, partitionsPerDimension, minX, minY, xLength, yLength) }
    
    if(withExtent) {
      rdd.map{ case (g,_) =>
        val center = g.getCentroid
      
        val id = SpatialGridPartitioner.getCellId(center.getX, center.getY, minX, minY, maxX, maxY, xLength, yLength, partitionsPerDimension)
        
        val env = g.getEnvelopeInternal
  		  val gExtent = NRectRange(NPoint(env.getMinX, env.getMinY), NPoint(env.getMaxX, env.getMaxY))
//        println(s"$center --> $id")
  		  (id,gExtent)
      }
      .reduceByKey{case(a,b) => a.extend(b)}
      .collect
      .foreach { case (id, extent) =>
        arr(id) = Cell(arr(id).range, extent)
      }
    }
    
    arr
  }
  
  
  override def partitionBounds(idx: Int): Cell = partitions(idx) //getCellBounds(idx)
  
  override def partitionExtent(idx: Int): NRectRange = partitions(idx).extent
  
  override def numPartitions: Int = Math.pow(partitionsPerDimension,dimensions).toInt

  /**
   * Compute the partition for an input key.
   * In fact, this is a Geometry for which we use its centroid for
   * the computation
   * 
   * @param key The key geometry to compute the partition for
   * @return The Index of the partition 
   */
  override def getPartition(key: Any): Int = {
    val g = key.asInstanceOf[G]
    
    val center = g.getCentroid
    
    val id = SpatialGridPartitioner.getCellId(center.getX, center.getY, minX, minY, maxX, maxY, xLength, yLength, partitionsPerDimension)
    
    require(id >= 0 && id < numPartitions, s"Cell ID out of bounds (0 .. $numPartitions): $id")
    
    id
  }
  
}