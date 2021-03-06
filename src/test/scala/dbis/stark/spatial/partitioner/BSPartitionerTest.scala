package dbis.stark.spatial.partitioner

import org.locationtech.jts.io.WKTReader
import dbis.stark.{STObject, TestUtils}
import dbis.stark.spatial.SpatialRDD._
import dbis.stark.spatial._
import dbis.stark.spatial.indexed.RTreeConfig
import dbis.stark.spatial.indexed.live.LiveIndexedSpatialRDDFunctions
import org.apache.spark.rdd.{RDD, ShuffledRDD}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.tagobjects.Slow
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class BSPartitionerTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  
  private var sc: SparkContext = _
  
  override def beforeAll() {
    val conf = new SparkConf().setMaster(s"local[${Runtime.getRuntime.availableProcessors()}]").setAppName("paritioner_test2")
    sc = new SparkContext(conf)
  }
  
  override def afterAll() {
    if(sc != null)
      sc.stop()
  }
  
  
  private def createRDD(points: Seq[String] = List("POINT(2 2)", "POINT(2.5 2.5)", "POINT(2 4)", "POINT(4 2)", "POINT(4 4)")): RDD[(STObject, Long)] = 
    sc.parallelize(points,4).zipWithIndex()
      .map { case (string, id) => (new WKTReader().read(string), id) }

//  "The BSP partitioner"
  it  should "find correct min/max values" in {
    
    val rdd = createRDD()    
    
    val parti = new BSPartitioner(rdd, 1, 1, false)
    
    withClue("wrong minX value") { parti.minX shouldBe 2 }
    withClue("wrong minX value") { parti.minY shouldBe 2 }
    withClue("wrong minX value") { parti.maxX shouldBe 4 + parti.sideLength } // max values are set to +1 to have "right open" intervals
    withClue("wrong minX value") { parti.maxY shouldBe 4 + parti.sideLength } // max values are set to +1 to have "right open" intervals
  }
  
  it  should "have the correct min/max in real world scenario" in {

    val rdd = TestUtils.createRDD(sc)

    val parti = new BSPartitioner(rdd,1, 10, false)

    parti.minX shouldBe -35.8655
    parti.maxX shouldBe 62.1345
    parti.minY shouldBe -157.74538
    parti.maxY shouldBe 153.25462

  }

  it  should "have the correct number of x cells in reald world scenario with length = 1" in {

    val rdd = TestUtils.createRDD(sc)

    val parti = new BSPartitioner(rdd,1, 10, false)

    parti.numXCells shouldBe 98 // ?

  }


  it  should "find the correct number of cells for X dimension" in {
    val rdd = createRDD()

    val parti = new BSPartitioner(rdd, 1, 1, false)

    parti.numXCells shouldBe 3
  }

  it  should "create correct number of cells" in {

    val rdd = createRDD()

    val parti = new BSPartitioner(rdd, 1, 1, false)

//    println(s"${parti.cells.mkString("\n")}")

    parti.cells.length shouldBe 9
  }

  it  should "create correct cell histogram" in {

    val rdd = createRDD()

    val parti = new BSPartitioner(rdd, 1, 1, false)

    val shouldSizes = Array(
      (Cell(NRectRange(NPoint(2,2), NPoint(3,3)),NRectRange(NPoint(2,2), NPoint(3,3))), 2), // 0
      (Cell(NRectRange(NPoint(3,2), NPoint(4,3)),NRectRange(NPoint(3,2), NPoint(4,3))), 0), // 1
      (Cell(NRectRange(NPoint(4,2), NPoint(5,3)),NRectRange(NPoint(4,2), NPoint(5,3))), 1), // 2
      (Cell(NRectRange(NPoint(2,3), NPoint(3,4)),NRectRange(NPoint(2,3), NPoint(3,4))), 0), // 3
      (Cell(NRectRange(NPoint(3,3), NPoint(4,4)),NRectRange(NPoint(3,3), NPoint(4,4))), 0), // 4
      (Cell(NRectRange(NPoint(4,3), NPoint(5,4)),NRectRange(NPoint(4,3), NPoint(5,4))), 0), // 5
      (Cell(NRectRange(NPoint(2,4), NPoint(3,5)),NRectRange(NPoint(2,4), NPoint(3,5))), 1), // 6
      (Cell(NRectRange(NPoint(3,4), NPoint(4,5)),NRectRange(NPoint(3,4), NPoint(4,5))), 0), // 7
      (Cell(NRectRange(NPoint(4,4), NPoint(5,5)),NRectRange(NPoint(4,4), NPoint(5,5))), 1)  // 8
    )

    parti.cells should contain only (shouldSizes:_*)
  }

  it should "have correct grid" in {
    val rdd = createRDD()

    val parti = new BSPartitioner(rdd, 1, 1, false)

    val shouldSizes = Array(
      Cell(NRectRange(NPoint(2,2), NPoint(3,3)),NRectRange(NPoint(2,2), NPoint(3,3))), // 0
      Cell(NRectRange(NPoint(3,2), NPoint(4,3)),NRectRange(NPoint(3,2), NPoint(4,3))), // 1
      Cell(NRectRange(NPoint(4,2), NPoint(5,3)),NRectRange(NPoint(4,2), NPoint(5,3))), // 2
      Cell(NRectRange(NPoint(2,3), NPoint(3,4)),NRectRange(NPoint(2,3), NPoint(3,4))), // 3
      Cell(NRectRange(NPoint(3,3), NPoint(4,4)),NRectRange(NPoint(3,3), NPoint(4,4))), // 4
      Cell(NRectRange(NPoint(4,3), NPoint(5,4)),NRectRange(NPoint(4,3), NPoint(5,4))), // 5
      Cell(NRectRange(NPoint(2,4), NPoint(3,5)),NRectRange(NPoint(2,4), NPoint(3,5))), // 6
      Cell(NRectRange(NPoint(3,4), NPoint(4,5)),NRectRange(NPoint(3,4), NPoint(4,5))), // 7
      Cell(NRectRange(NPoint(4,4), NPoint(5,5)),NRectRange(NPoint(4,4), NPoint(5,5)))  // 8
    )

    parti.cells.length shouldBe shouldSizes.length
    parti.cells.zipWithIndex.foreach{ case ((cell,_),idx) =>
      cell shouldBe shouldSizes(idx)
    }

  }


  it  should "return the correct partition id" in {
    val rdd = createRDD()
    val parti = new BSPartitioner(rdd, 1, 1, false)

//    val partIds = Array(0,0,1,2,3)

    val parts = rdd.map{ case (g,v) => parti.getPartition(g) }.collect()

    parts should contain inOrderOnly(0,1,2,3)


//    rdd.collect().foreach{ case (g,id) =>
//      val pId = parti.getPartition(g)
//
//      pId shouldBe partIds(id.toInt)
//    }
  }


  it  should "return all points for one partition" in {

    val rdd: RDD[(STObject, (String, Long, String, STObject))] = TestUtils.createRDD(sc, numParts = Runtime.getRuntime.availableProcessors())

    // with maxcost = size of RDD everything will end up in one partition
    val parti = new BSPartitioner(rdd, 2, maxCostPerPartition = 1000, pointsOnly = false)

    val shuff = new ShuffledRDD(rdd, parti)

    shuff.count() shouldBe rdd.count()

  }

  it  should "return all points for two partitions" in {

    val rdd: RDD[(STObject, (String, Long, String, STObject))] = TestUtils.createRDD(sc, numParts = Runtime.getRuntime.availableProcessors())

    // with maxcost = size of RDD everything will end up in one partition
    val parti = new BSPartitioner(rdd, 2, maxCostPerPartition = 500, pointsOnly = false)

    val shuff = new ShuffledRDD(rdd, parti)
    // insert dummy action to make sure Shuffled RDD is evaluated
    shuff.foreach{f => }

    shuff.count() shouldBe 1000
    shuff.count() shouldBe rdd.count()

  }

  it  should "return all points for max cost 100 & sidelength = 1" in {

    val rdd: RDD[(STObject, (String, Long, String, STObject))] = TestUtils.createRDD(sc, numParts = Runtime.getRuntime.availableProcessors())

    // with maxcost = size of RDD everything will end up in one partition
    val parti = new BSPartitioner(rdd, 1, maxCostPerPartition = 100, pointsOnly = false)

    val shuff = new ShuffledRDD(rdd, parti)
    // insert dummy action to make sure Shuffled RDD is evaluated
    shuff.foreach{f => }

    shuff.count() shouldBe 1000
    shuff.count() shouldBe rdd.count()

  }

  it  should "return only one partition if max cost equals input size" in {

    val rdd: RDD[(STObject, (String, Long, String, STObject))] = TestUtils.createRDD(sc, numParts = Runtime.getRuntime.availableProcessors())

    // with maxcost = size of RDD everything will end up in one partition
    val parti = new BSPartitioner(rdd, 1, maxCostPerPartition = 1000, pointsOnly = false)

    parti.numPartitions shouldBe 1

  }

  /* this test case was created for bug hunting, where for the taxi data, all points with coordinates (0 0) were
   * not added to any partition.
   *
   * The result was that the cell bounds for the histogram where created wrong.
   */
  it  should "work with 0 0 " in {
    val rdd = sc.textFile("src/test/resources/taxi_sample.csv", Runtime.getRuntime.availableProcessors())
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}

    val minMax = SpatialPartitioner.getMinMax(rdd)

    BSPartitioner.numCellThreshold = Runtime.getRuntime.availableProcessors()
    val parti = new BSPartitioner(rdd, 1, 10*1000, false, minMax._1, minMax._2, minMax._3, minMax._4)

//    parti.printHistogram(java.nio.file.Paths.get(System.getProperty("user.home"), "histo2.csv"))
//    parti.printPartitions(java.nio.file.Paths.get(System.getProperty("user.home"), "partition2.csv"))

    // make sure there are no duplicate cells, i.e. they shouldn't have the same region
    parti.cells.map { case (cell, _) => cell.range }.distinct.length shouldBe parti.cells.length


    // every point must be in one partition
    rdd.collect().foreach { case (st, name) =>
      try {
        val pNum = parti.getPartition(st)
        withClue(name) { pNum should (be >= 0 and be < parti.numPartitions) }
      } catch {
      case e:IllegalStateException =>

        val xOk = st.getGeo.getCentroid.getX >= minMax._1 && st.getGeo.getCentroid.getX <= minMax._2
        val yOk = st.getGeo.getCentroid.getY >= minMax._3 && st.getGeo.getCentroid.getY <= minMax._4

        parti.bsp.partitions.foreach { cell =>

          val xOk = st.getGeo.getCentroid.getX >= cell.range.ll(0) && st.getGeo.getCentroid.getX <= cell.range.ur(0)
          val yOk = st.getGeo.getCentroid.getY >= cell.range.ur(1) && st.getGeo.getCentroid.getY <= cell.range.ur(1)

          println(s"${cell.id} cell: ${cell.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY))}  x: $xOk  y: $yOk")
        }

        val containingCell = parti.cells.find(cell => cell._1.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY)))
        if(containingCell.isDefined) {
          println(s"should be in ${containingCell.get._1.id} which has bounds ${parti.cells(containingCell.get._1.id)._1.range} and count ${parti.cells(containingCell.get._1.id)._2}")
        } else {
          println("No cell contains this point!")
        }



        fail(s"$name: ${e.getMessage}  xok: $xOk  yOk: $yOk")
      }

    }
  }

  it should "use cells as partitions for taxi" in {
    val rdd = sc.textFile("src/test/resources/taxi_sample.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}

    val minMax = SpatialPartitioner.getMinMax(rdd)

//    BSPartitioner.numCellThreshold = -5
    val parti = new BSPartitioner(rdd, 1, 100, false, minMax._1, minMax._2, minMax._3, minMax._4)

    val nonempty = parti.cells.filter(_._2 > 0)
//    withClue("number of non empty cells") { nonempty.length shouldBe 7 }

    val cnt = rdd.count()

    val coveredCells = parti.bsp.partitions.flatMap(p => parti.bsp.getCellsIn(p.range)).sorted
    val distinctCells = coveredCells.distinct.sorted

    withClue("covered cells") { coveredCells should contain theSameElementsAs distinctCells }


    withClue("number of elements in covered cells") {parti.bsp.partitions.flatMap { p =>
      parti.bsp.getCellsIn(p.range)
    }.filter(_ < parti.cells.length).map(idx => parti.cells(idx)._2).sum shouldBe cnt }



    withClue("number of elements in partitions") {nonempty.map(_._2).sum shouldBe cnt}

    rdd.collect().foreach { case (st, name) =>
      try {
        val pNum = parti.getPartition(st)
        withClue(name) { pNum should (be >= 0 and be < parti.numPartitions) }
      } catch {
      case e:IllegalStateException =>

        val xOk = st.getGeo.getCentroid.getX >= minMax._1 && st.getGeo.getCentroid.getX <= minMax._2
        val yOk = st.getGeo.getCentroid.getY >= minMax._3 && st.getGeo.getCentroid.getY <= minMax._4

        parti.bsp.partitions.foreach { cell =>

          val xOk = st.getGeo.getCentroid.getX >= cell.range.ll(0) && st.getGeo.getCentroid.getX <= cell.range.ur(0)
          val yOk = st.getGeo.getCentroid.getY >= cell.range.ur(1) && st.getGeo.getCentroid.getY <= cell.range.ur(1)

          println(s"${cell.id} cell: ${cell.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY))}  x: $xOk  y: $yOk")
        }

        val containingCell = parti.cells.find(cell => cell._1.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY)))
        if(containingCell.isDefined) {
          println(s"should be in ${containingCell.get._1.id} which has bounds ${parti.cells(containingCell.get._1.id)._1.range} and count ${parti.cells(containingCell.get._1.id)._2}")
        } else {
          println("No cell contains this point!")
        }



        fail(s"$name: ${e.getMessage}  xok: $xOk  yOk: $yOk")
      }

    }


  }

  it  should "create real partitions correctly for taxi" taggedAs Slow in {
    val rdd = sc.textFile("src/test/resources/taxi_sample.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}

      val minMax = SpatialPartitioner.getMinMax(rdd)

      BSPartitioner.numCellThreshold = -1
      val parti = new BSPartitioner(rdd, 0.1, 100, false, minMax._1, minMax._2, minMax._3, minMax._4)

      parti.numPartitions shouldBe 9

      rdd.collect().foreach { case (st, name) =>
        try {
          val pNum = parti.getPartition(st)
          withClue(name) { pNum should (be >= 0 and be < parti.numPartitions) }
        } catch {
        case e:IllegalStateException =>

          val xOk = st.getGeo.getCentroid.getX >= minMax._1 && st.getGeo.getCentroid.getX <= minMax._2
          val yOk = st.getGeo.getCentroid.getY >= minMax._3 && st.getGeo.getCentroid.getY <= minMax._4

          parti.bsp.partitions.foreach { cell =>

            val xOk = st.getGeo.getCentroid.getX >= cell.range.ll(0) && st.getGeo.getCentroid.getX <= cell.range.ur(0)
            val yOk = st.getGeo.getCentroid.getY >= cell.range.ur(1) && st.getGeo.getCentroid.getY <= cell.range.ur(1)

            println(s"${cell.id} cell: ${cell.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY))}  x: $xOk  y: $yOk")
          }

          val containingCell = parti.cells.find (cell => cell._1.range.contains(NPoint(st.getGeo.getCentroid.getX, st.getGeo.getCentroid.getY)))
          if(containingCell.isDefined) {
            println(s"should be in ${containingCell.get._1.id} which has bounds ${parti.cells(containingCell.get._1.id)._1.range} and count ${parti.cells(containingCell.get._1.id)._2}")
          } else {
            println("No cell contains this point!")
          }



          fail(s"$name: ${e.getMessage}  xok: $xOk  yOk: $yOk")
        }

      }


  }

  it  should "do yello sample" in {
    val rdd = sc.textFile("src/test/resources/blocks.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}

      val minMax = SpatialPartitioner.getMinMax(rdd)
      BSPartitioner.numCellThreshold = Runtime.getRuntime.availableProcessors()
      val parti = new BSPartitioner(rdd, 0.2, 100, true)

      val rddtaxi = sc.textFile("src/test/resources/taxi_sample.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}

      val minMaxTaxi = SpatialPartitioner.getMinMax(rddtaxi)
      val partiTaxi = new BSPartitioner(rddtaxi, 0.1, 100, false)

      val matches = for(t <- partiTaxi.bsp.partitions;
          b <- parti.bsp.partitions
                        if t.extent.intersects(b.extent)) yield (t,b)

      matches.length shouldBe >(0)
      val res = new LiveIndexedSpatialRDDFunctions(rdd, RTreeConfig(5)).join(rddtaxi, JoinPredicate.CONTAINS, None)
  }

  it  should "correctly partiton random points" in {
    val rdd = sc.textFile("src/test/resources/points.wkt")
      .map(_.split(";"))
      .map(arr => (STObject(arr(0)),arr(1)))

    val parti = new BSPartitioner(rdd, 0.5,1000, pointsOnly = false)

    val numparts = parti.numPartitions

    val parted = rdd.partitionBy(parti)

    parted.collect().foreach { case (st, name) =>
      try {
        val pNum = parti.getPartition(st)
        withClue(name) { pNum should (be >= 0 and be < numparts)}
      } catch {
        case e:IllegalStateException =>


          fail(s"$name: ${e.getMessage}  xok: xOk  yOk: yOk")
      }
    }
  }

  it  should "correctly join with single point and polygon" in {

    val pointsRDD = sc.parallelize(Seq(
      (STObject("POINT (77.64656066894531  23.10247055501927)"),1)))

    val polygonsRDD = sc.parallelize(Seq(
      (STObject("POLYGON ((77.2723388671875 23.332168306311473, 77.8436279296875 23.332168306311473, " +
        "77.8436279296875 22.857194700969636, 77.2723388671875 22.857194700969636, 77.2723388671875 23.332168306311473, " +
        "77.2723388671875 23.332168306311473))"),1)))

    val pointsBSP = new BSPartitioner(pointsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = false)
    val pointsPart = pointsRDD.partitionBy(pointsBSP)

    val polygonsBSP = new BSPartitioner(polygonsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = true)
    val polygonsPart = polygonsRDD.partitionBy(polygonsBSP)

    val joined = polygonsPart.join(pointsPart, JoinPredicate.CONTAINS)

    joined.collect().length shouldBe 1
  }

  it  should "correctly join with single point and polygon containedby" in {

    val pointsRDD = sc.parallelize(Seq(
      (STObject("POINT (77.64656066894531  23.10247055501927)"),1)))

    val polygonsRDD = sc.parallelize(Seq(
      (STObject("POLYGON ((77.2723388671875 23.332168306311473, 77.8436279296875 23.332168306311473, " +
        "77.8436279296875 22.857194700969636, 77.2723388671875 22.857194700969636, 77.2723388671875 23.332168306311473, " +
        "77.2723388671875 23.332168306311473))"),1)))

    val pointsBSP = new BSPartitioner(pointsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = false)
    val pointsPart = pointsRDD.partitionBy(pointsBSP)

    val polygonsBSP = new BSPartitioner(polygonsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = true)
    val polygonsPart = polygonsRDD.partitionBy(polygonsBSP)

    val joined = pointsPart.join(polygonsPart, JoinPredicate.CONTAINEDBY)

    joined.collect().length shouldBe 1
  }

  it  should "correctly join with single point and polygon intersect point-poly" in {

    val pointsRDD = sc.parallelize(Seq(
      (STObject("POINT (77.64656066894531  23.10247055501927)"),1)))

    val polygonsRDD = sc.parallelize(Seq(
      (STObject("POLYGON ((77.2723388671875 23.332168306311473, 77.8436279296875 23.332168306311473, " +
        "77.8436279296875 22.857194700969636, 77.2723388671875 22.857194700969636, 77.2723388671875 23.332168306311473, " +
        "77.2723388671875 23.332168306311473))"),1)))

    val pointsBSP = new BSPartitioner(pointsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = false)
    val pointsPart = pointsRDD.partitionBy(pointsBSP)

    val polygonsBSP = new BSPartitioner(polygonsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = true)
    val polygonsPart = polygonsRDD.partitionBy(polygonsBSP)

    val joined = pointsPart.join(polygonsPart, JoinPredicate.INTERSECTS)

    joined.collect().length shouldBe 1
  }

  it  should "correctly join with single point and polygon intersect poly-point" in {

    val pointsRDD = sc.parallelize(Seq(
      (STObject("POINT (77.64656066894531  23.10247055501927)"),1)))

    val polygonsRDD = sc.parallelize(Seq(
      (STObject("POLYGON ((77.2723388671875 23.332168306311473, 77.8436279296875 23.332168306311473, " +
        "77.8436279296875 22.857194700969636, 77.2723388671875 22.857194700969636, 77.2723388671875 23.332168306311473, " +
        "77.2723388671875 23.332168306311473))"),1)))

    val pointsBSP = new BSPartitioner(pointsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = false)
    val pointsPart = pointsRDD.partitionBy(pointsBSP)

    val polygonsBSP = new BSPartitioner(polygonsRDD, sideLength = 0.5, maxCostPerPartition = 1000, pointsOnly = true)
    val polygonsPart = polygonsRDD.partitionBy(polygonsBSP)

    val joined = polygonsPart.join(pointsPart, JoinPredicate.INTERSECTS)

    joined.collect().length shouldBe 1
  }


  ignore  should "correctly partition wikimapia" in {
    val rdd = sc.textFile("/home/hg/Documents/uni/stuff/stark/fix_shi/wiki_full.wkt")
      .map(_.split(";"))
      .map(arr => (STObject(arr(0)),arr(1)))

    val parti = new BSPartitioner(rdd, 0.5,1000, pointsOnly = true)

    val numparts = parti.numPartitions

    val parted = rdd.partitionBy(parti)

    parted.collect().foreach { case (st, name) =>
      try {
        val pNum = parti.getPartition(st)
        withClue(name) { pNum should (be >= 0 and be < numparts)}
      } catch {
        case e:IllegalStateException =>

          //          val xOk = st.getGeo.getCentroid.getX >= minMax._1 && st.getGeo.getCentroid.getX <= minMax._2
          //          val yOk = st.getGeo.getCentroid.getY >= minMax._3 && st.getGeo.getCentroid.getY <= minMax._4



          fail(s"$name: ${e.getMessage}  xok: xOk  yOk: yOk")
      }
    }
  }

  ignore should "compute correct join contains" in {
    val points = sc.textFile("/home/hg/Documents/uni/stuff/stark/fix_shi/points10k.csv")
                    .map(_.split(","))
                      .map(arr => s"POINT(${arr(1)} ${arr(0)})")
                      .map(s => (STObject(s),9))


    val wiki = sc.textFile("/home/hg/Documents/uni/stuff/stark/fix_shi/wiki_full.wkt")
      .map(_.split(";"))
      .map(arr => (STObject(arr(0)),arr(1)))


    val jWOPart = wiki.join(points, JoinPredicate.CONTAINS).collect()

    jWOPart should not be empty

    val pointBSP = new BSPartitioner(points, 0.5, 100, pointsOnly = false)
    val polyBSP = new BSPartitioner(wiki, 0.5, 100, pointsOnly = true)

    val pointsParted = points.partitionBy(pointBSP)
    val polyParted = wiki.partitionBy(polyBSP)

    val jWPart = polyParted.join(pointsParted, JoinPredicate.CONTAINS).collect()

    println(s"no part: ${jWOPart.length}")
    println(s"w/ part: ${jWPart.length}")

    jWOPart should contain theSameElementsAs jWPart
  }


  ignore  should "compute correct join containedby" in {
    val pointsWkt = sc.textFile("/home/hg/Documents/uni/stuff/stark/fix_shi/points10k.csv")
      .map(_.split(","))
      .map(arr => s"POINT(${arr(1)} ${arr(0)})")

    val points = pointsWkt.map(s => (STObject(s),9))


    val wiki = sc.textFile("/home/hg/Documents/uni/stuff/stark/fix_shi/wiki_full.wkt")
      .map(_.split(";"))
      .map(arr => (STObject(arr(0)),arr(1)))


    val jWOPart = points.join(wiki, JoinPredicate.CONTAINEDBY).collect()

    jWOPart should not be empty

    val pointBSP = new BSPartitioner(points, 0.5, 100, pointsOnly = false)
    val polyBSP = new BSPartitioner(wiki, 0.5, 100, pointsOnly = true)

    val pointsParted = points.partitionBy(pointBSP)
    val polyParted = wiki.partitionBy(polyBSP)

    val jWPart = pointsParted.join(polyParted, JoinPredicate.CONTAINEDBY).collect()

    println(s"no part: ${jWOPart.length}")
    println(s"w/ part: ${jWPart.length}")

    jWOPart should contain theSameElementsAs jWPart
  }

  it should "produce same join results with sampling as without" taggedAs Slow in {
    val rddBlocks = sc.textFile("src/test/resources/blocks.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}//.sample(withReplacement = false, 0.5)

    val rddTaxi = sc.textFile("src/test/resources/taxi_sample.csv", 4)
      .map { line => line.split(";") }
      .map { arr => (STObject(arr(1)), arr(0))}//.sample(withReplacement = false, 0.5)


    val joinResNoPart = new LiveIndexedSpatialRDDFunctions(rddBlocks, RTreeConfig(5)).join(rddTaxi, JoinPredicate.CONTAINS, None).sortByKey().collect()


    val taxiPartiNoSample = new BSPartitioner(rddTaxi, sideLength = 0.3, maxCostPerPartition = 100, pointsOnly = true)


    val blockPartiNoSample = new BSPartitioner(rddBlocks, sideLength = 0.2, maxCostPerPartition = 100, pointsOnly = false)

    val joinResPlain = new LiveIndexedSpatialRDDFunctions(rddBlocks.partitionBy(blockPartiNoSample), RTreeConfig(5)).join(rddTaxi.partitionBy(taxiPartiNoSample), JoinPredicate.CONTAINS, None).sortByKey().collect()
    joinResPlain.length shouldBe > (0)

    withClue("join part no sample does not have same results as no partitioning") { joinResPlain should contain theSameElementsAs joinResNoPart }
  }

}
