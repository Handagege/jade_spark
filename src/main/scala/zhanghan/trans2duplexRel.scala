package zhanghan

import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import scala.collection.mutable.{Map,ArrayBuffer,Set}

import scala.util.control.Breaks._

/**
 * Created by zhanghan5 on 2015/11/5.
 * distribute clique mining algorithm
 */

object trans2duplexRel {

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

  def main(args: Array[String]): Unit ={
	if(args.length < 2)
	{
		System.err.println("Usage: <inputFile> <outputFile>")
		System.exit(1)
	}
    val conf = new SparkConf()
    val sc = new SparkContext(conf)
	val inputFile = args(0)
	val outputFile = args(1)

    val lines = sc.textFile(inputFile)
	//deal with origin relation data
	//
    val relationRdd = lines.flatMap(l => transDataToKV(l))
    val reverseRelationRdd = relationRdd.map(x => (x._2,x._1))
      .groupByKey()
      .mapValues(_.toSet)
    val duplexRelationRdd = relationRdd.groupByKey()
      .mapValues(_.toSet)
      .join(reverseRelationRdd)
      .mapValues{ case (x,y) => x & y}
	  .map{case(x,y) => x+"\t"+y.mkString(",")}
	duplexRelationRdd.saveAsTextFile(outputFile)
	}
}
