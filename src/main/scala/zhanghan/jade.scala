package zhanghan

import org.apache.spark.storage.StorageLevel
import org.apache.spark.{RangePartitioner,HashPartitioner}
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import scala.collection.mutable.{Map,ArrayBuffer,Set}

import scala.util.control.Breaks._

/**
 * Created by zhanghan5 on 2015/10/8.
 * distribute clique mining algorithm
 */

object jade {

  val modularity = (m: Double,lc: Double,dc: Double) =>
    lc/(2.0*m) - math.pow((dc-lc)/(2.0*m),2)


  val isMGain = (x: (Long,Double,Double),
                 y: (Double,Double,Double),
                 m: Double) => {
    val mv = modularity(m, y._1, y._2) / y._3
    val newMv = modularity(m, y._1 + x._2, y._2 + x._3) / (y._3 + 1.0)
    if (newMv > mv) 1 else 0
  }


  val calOverlap = (cliqueA: Set[Long],cliqueB: Set[Long]) => {
    val overlapNum = (cliqueA & cliqueB).size
    val al = cliqueA.size
    val bl = cliqueB.size
    val minLen = if(al < bl) al else bl
    val overlap = overlapNum.toDouble / minLen.toDouble
    overlap
  }


  def dissoveOverlap(it: Iterator[Set[Long]],minOverlapLimmit: Double,upperOverlapLimit: Double) = {
	var clique = ArrayBuffer[Set[Long]]()
	while(it.hasNext){
		val v = it.next()
		clique += v
	}
	var tupperOverlapLimit = upperOverlapLimit
	var mergeFlag = true
	while(mergeFlag){
		val tempMergedClique = ArrayBuffer[Set[Long]]()
		var removeSet = Set[Long]()
		for(i <- 0 until clique.length if !removeSet.contains(i)){
			for(j <- i+1 until clique.length if !removeSet.contains(j)){
				if(calOverlap(clique(i),clique(j)) >= tupperOverlapLimit){
					tempMergedClique += clique(i) | clique(j)
					removeSet += (i,j)
				}
			}
		}
		if(removeSet.isEmpty && tupperOverlapLimit == minOverlapLimmit) mergeFlag = false
		if(!removeSet.isEmpty){
			for(i <- 0 until clique.length){
				if(!removeSet.contains(i)) tempMergedClique += clique(i)
			}
			clique = tempMergedClique
		}
		tupperOverlapLimit -= 0.1
		if(tupperOverlapLimit < minOverlapLimmit)
			tupperOverlapLimit = minOverlapLimmit
	}
	clique.iterator
  }

  def transDataToKV(line: String) = {
    val data = line.split('\t')
    val key = data(0).toLong
	var result =  Array[(Long,Long)]()
	if(data.length == 2) {
	  val value = data(1).split(',')
      result = result ++ value.map(uid => (key,uid.toLong))
	}
	result
  }

  def transDataToKSet(line: String) = {
    val data = line.split('\t')
    val key = data(0)
    val value = data(1).split(',').map(_.toLong)
    (key,value.toSet)
  }

  def bronkerbosch(p: Set[Long],r: Set[Long],x: Long,minCliqueSize: Int
	                   ,vertexConnectDic: Map[Long,scala.collection.immutable.Set[Long]],result:  ArrayBuffer[Set[Long]]) {
	if(p.size + r.size >= minCliqueSize && result.length < 1) {
	    if (p.isEmpty || r.size >= minCliqueSize)
	//if(p.size + r.size >= minCliqueSize){ 
	    //if (p.isEmpty)
	      result += r
	    else {
	      for(pendingUid <- p) {
	        if(pendingUid > x)
	          bronkerbosch(p & vertexConnectDic(pendingUid),r+pendingUid,pendingUid
	            ,minCliqueSize,vertexConnectDic,result)
	        }
	      }
	    }
	  }

  def findMaximalClique2(it: Iterator[(Long,(Long,scala.collection.immutable.Set[Long]))]
	,minCliqueSize: Int,iterNumPerUid: Int) = {
    val result = new ArrayBuffer[Set[Long]]()
    //var result = List[Set[Long]]()
    val relationMap = Map[Long,scala.collection.immutable.Set[Long]]()
    val uidKeyMap =  Map[Long,Set[Long]]()
    while(it.hasNext){
      val v = it.next()
      if(uidKeyMap.contains(v._1))
        uidKeyMap(v._1) += v._2._1
      else
        uidKeyMap(v._1) = Set(v._2._1)
      if(!relationMap.contains(v._2._1))
        relationMap(v._2._1) = v._2._2
    }
    val originCalItemArray = new ArrayBuffer[(Set[Long],Long,Long)]()
    for(i <- uidKeyMap) originCalItemArray += Tuple3(i._2,i._1,i._1)
    for(i <- originCalItemArray)
	{
		var iterNum = 0
		for(j <- i._1 if iterNum < iterNumPerUid && j > i._2){
			val tempResult = new ArrayBuffer[Set[Long]]()
			iterNum += 1
			bronkerbosch(i._1&relationMap(j),Set(i._2,j),j,minCliqueSize,relationMap,tempResult)
			result ++= tempResult
		}
	}
    result.iterator
  }

  def getExpandNodeSet(it: Iterator[(Long,(Long,Int))]) = {
    val result = Map[Long,Set[Long]]()
	while(it.hasNext){
	  val v = it.next()
	  if(!result.contains(v._1)) result(v._1) = Set[Long]()
  	  if(v._2._2 == 1) result(v._1) += v._2._1
	}
    result.iterator
  }

  def main(args: Array[String]): Unit ={
	if(args.length < 7)
	{
		System.err.println("Usage: <inputFile> <outputFile> <minCliqueSize> <iterNumPerUid> <minOverlapLimmit> "
			+ "<upperOverlapLimit> <partitionNum>")
		System.exit(1)
	}
    val conf = new SparkConf()
    val sc = new SparkContext(conf)
	val inputFile = args(0)
	val outputFile = args(1)
	val minCliqueSize = args(2).toInt
	val iterNumPerUid = args(3).toInt
	val minOverlapLimmit = args(4).toDouble
	var upperOverlapLimit = args(5).toDouble
	var partitionNum = args(6).toInt


    val lines = sc.textFile(inputFile,partitionNum)
	val duplexRelationRdd = lines.map(x => {
		var result = (1l,scala.collection.immutable.Set[Long]())
		val data = x.split("\t")
		if(data.length == 2) result = (data(0).toLong, data(1).split(",").map(_.toLong).toSet)
		else result = (data(0).toLong, scala.collection.immutable.Set[Long]())
		result
		}).persist()
	//***********************
	//deal with origin relation data
	//
    //val relationRdd = lines.flatMap(l => transDataToKV(l))
    //val reverseRelationRdd = relationRdd.map(x => (x._2,x._1))
    //  .groupByKey()
    //  .mapValues(_.toSet)
    //val duplexRelationRdd = relationRdd.groupByKey()
    //  .mapValues(_.toSet)
    //  .join(reverseRelationRdd)
    //  .mapValues{ case (x,y) => x & y}
    //  .partitionBy(new HashPartitioner(partitionNum))
	//
	//***********************

    val duplexRelationCalPartitionRdd = duplexRelationRdd.flatMap(x => x._2.map(i => (i,x._1)))
      .join(duplexRelationRdd)
      .map{case(x,y) => (y._1,(x,y._2))}
      .partitionBy(new HashPartitioner(partitionNum))
	  //.map{case(x,y) => x + ":" + y._1 + "-" + y._2.mkString(",")}
    val maximalClique = duplexRelationCalPartitionRdd
      .mapPartitions(it => findMaximalClique2(it,minCliqueSize,iterNumPerUid))
	 // maximalClique.saveAsTextFile(outputFile+"_maximal")
	//
	//dissolve overlap issue
	//
	val cliqueRdd = maximalClique.mapPartitions(it => dissoveOverlap(it
	  ,minOverlapLimmit,upperOverlapLimit))
	  .repartition(1)
	  .mapPartitions(it => dissoveOverlap(it,minOverlapLimmit,upperOverlapLimit))
	  .zipWithIndex()
      .partitionBy(new HashPartitioner(partitionNum))
	  .map{case(x,y) => (y,x)}
	//
	//expander clique by node
	//
    val backupTotalStatsRdd = cliqueRdd.flatMap{case(x,y) => y.map(i => (i,x))}
	  .join(duplexRelationRdd).values
    val lc_dc_Rdd = backupTotalStatsRdd.join(cliqueRdd)
      .mapValues{case(x,y) => ((x&y).size.toDouble/2.0,x.size.toDouble,1.0)}
      .reduceByKey{case(x,y) => (x._1+y._1,x._2+y._2,x._3+y._3)}
    val m = duplexRelationRdd.values.map(_.size).reduce(_+_).toDouble
    val connectNodeRdd = backupTotalStatsRdd.reduceByKey(_|_)
      .join(cliqueRdd)
      .mapValues(x => x._1--x._2)
    val expandNodeDataRdd = connectNodeRdd.flatMapValues(x => x)
      .map{case(x,y) => (y,x)}
      .join(duplexRelationRdd)
      .map{case(x,y) => (y._1,(x,y._2))}
    val add_lc_dc_Rdd = expandNodeDataRdd.join(cliqueRdd)
	  .map{case(x,y) => ((x,y._1._1),((y._1._2&y._2).size.toDouble,y._1._2.size.toDouble))}
      .reduceByKey{case(x,y) => (x._1+y._1,x._2+y._2)}
      .map{case(x,y) => (x._1,(x._2,y._1,y._2))}
	val expandNodeSetRdd = add_lc_dc_Rdd.join(lc_dc_Rdd)
	  .map(x => (x._1,(x._2._1._1,isMGain(x._2._1,x._2._2,m))))
	  .mapPartitions(getExpandNodeSet)
	val newCliqueRdd = expandNodeSetRdd.join(cliqueRdd)
	  .mapValues(x => x._1 | x._2).values

    newCliqueRdd.repartition(1).mapPartitions(it => dissoveOverlap(it,minOverlapLimmit,upperOverlapLimit))
	  .zipWithIndex().map{case(x,y) => y+"--"+x.mkString(",")}
	  .saveAsTextFile(outputFile)
	/*
	*/
    sc.stop()
  }
}
