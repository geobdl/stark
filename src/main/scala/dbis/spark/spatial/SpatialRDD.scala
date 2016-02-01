package dbis.spark.spatial

import org.apache.spark.rdd.RDD
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.Partition
import org.apache.spark.TaskContext
import org.apache.spark.Dependency
import org.apache.spark.Partitioner
import org.apache.spark.OneToOneDependency
import scala.reflect.ClassTag
import com.vividsolutions.jts.geom.Geometry
import scala.collection.JavaConversions._
import org.apache.spark.Logging
import dbis.spark.spatial.indexed.IntersectionIndexedSpatialRDD
import dbis.spark.spatial.indexed.IndexedSpatialRDD
import dbis.spark.spatial.indexed.SpatialGridPartitioner
import org.apache.spark.rdd.ShuffledRDD
import dbis.spark.spatial.plain.IntersectionSpatialRDD
import dbis.spark.spatial.plain.KNNSpatialRDD
import dbis.spark.spatial.indexed.persistent.MyRDD
import org.apache.spark.rdd.MapPartitionsRDD
import dbis.spark.spatial.indexed.RTree
import dbis.spark.spatial.indexed.RTree
import dbis.spark.spatial.indexed.RTree

/**
 * A base class for spatial RDD without indexing
 * 
 * @param prev The parent RDD
 */
abstract class SpatialRDD[G <: Geometry : ClassTag, V: ClassTag](
    @transient private val prev: RDD[(G,V)]
  ) extends RDD[(G,V)](prev) {
  
  
  /**
   * We do not repartition our data.
   */
  override protected def getPartitions: Array[Partition] = firstParent[(G,V)].partitions

  /**
   * Compute an intersection of the elements in this RDD with the given geometry
   */
  def intersect(qry: G): IntersectionSpatialRDD[G,V] = new IntersectionSpatialRDD(qry, this)
  
  def kNN(qry: G, k: Int): KNNSpatialRDD[G,V] = new KNNSpatialRDD(qry, k, this)
  
}

class SpatialRDDFunctions[G <: Geometry : ClassTag, V: ClassTag](
    rdd: RDD[(G,V)]
  ) extends Logging with Serializable {
  

  def intersect(qry: G): RDD[(G,V)] = new IntersectionSpatialRDD(qry, rdd)
  
  def kNN(qry: G, k: Int): RDD[(G,V)] = new KNNSpatialRDD(qry, k, rdd)
  
  def index(ppD: Int): IndexedSpatialRDDFunctions[G,V] = index(new SpatialGridPartitioner(ppD, rdd))
  
  def index(partitioner: SpatialPartitioner) = new IndexedSpatialRDDFunctions(partitioner, rdd)
  
  def grid(ppD: Int) = new ShuffledRDD[G,V,V](rdd, new SpatialGridPartitioner(ppD, rdd))
  
  def bla(ppD: Int) = {
    
    grid(ppD).mapPartitionsWithIndex { (idx,iter) => 
      val tree = new RTree[G,(G,V)](10)
      
      iter.foreach{ case (g,v) => tree.insert(g, (g,v)) }
//      println(s"partition: $idx  --> index size: ${tree.size()}")
      List(tree).toIterator
    }
    
  }
}

class IndexedSpatialRDDFunctions[G <: Geometry : ClassTag, V: ClassTag](
    partitioner: SpatialPartitioner,
    rdd: RDD[(G,V)]
  ) extends Logging with Serializable {
  

  def intersect(qry: G): IndexedSpatialRDD[G,V] = new IntersectionIndexedSpatialRDD(qry, partitioner, rdd)
  
//  def kNN(qry: G, k: Int): RDD[(G,V)] = new KNNSpatialRDD(qry, k, rdd)
}


class MyRDDFunctions[G <: Geometry : ClassTag, V: ClassTag](
    rdd: RDD[RTree[G, (G,V)]]) {
  
  def intersect(qry: G) = new MyRDD(qry, rdd)
  
}


object SpatialRDD {
  
  implicit def convertSpatial[G <: Geometry : ClassTag, V: ClassTag](rdd: RDD[(G, V)]): SpatialRDDFunctions[G,V] = new SpatialRDDFunctions[G,V](rdd)
  
  implicit def convertMy[G <: Geometry : ClassTag, V: ClassTag](rdd: RDD[RTree[G,(G,V)]]) = new MyRDDFunctions(rdd)
  
//  implicit def convertIndexedSpatial[G <: Geometry : ClassTag, V: ClassTag](rdd: RDD[(G,V)]): IndexedSpatialRDDFunctions[G,V] = new IndexedSpatialRDDFunctions[G,V](rdd)
  
}



























