import math._
import scala.util.control._
import annotation._
import org.apache.spark.SparkContext, org.apache.spark.SparkConf
import org.apache.spark.storage._
//TO AVOID FUCKING SPARK LOG
import org.apache.log4j.{Level, Logger}

object DBSCAN{
    private val UNDEF : Int = -2
    val NOISE : Int = -1
    // TRANSIENT : Do Not serialize 
    @transient private val outbreak = new Breaks;
    @transient private val inbreak = new Breaks;

    def distance(p1:(Double,Double),p2:(Double,Double)) : Double ={
        math.sqrt(
            math.pow(p1._1 - p2._1 , 2) +
            math.pow(p1._2 - p2._2 , 2)
        )
    }

    def cmp(p1:(Double,Double),p2:(Double,Double)): Boolean = {
        ((p1._1 == p2._1) && (p1._2 == p2._2))
    }

    def bCount(anRdd : org.apache.spark.rdd.RDD[(Double,Double)]) = {
        anRdd.map(x => ("same", (1,x) ))
                .reduceByKey( (v1,v2) => (v1._1+v2._1,v1._2) )
                .collect()(0)._2._1.toInt
    }

    def toDouble(s: String): Double = {
        try {
            s.toDouble
        } catch {
            case e: Exception => 0
        }
    }
    def toCouple(a : Array[Double]) : Array[(Double,Double)]= {
        val c =(a(0),a(1))
        Array(c)
    }

    /*
    def testSparkSub() = {
        Logger.getLogger("org").setLevel(Level.ERROR)
        val sc = SparkContext.getOrCreate()
        println("Enter test")
        val testArr : Array[(Double,Double)] = Array((7, 2), (5, 4), (9, 6), (4, 7), (8, 1), (2, 3))
        val tree = new InNode(3,testArr,0)
        sc.broadcast(tree)
        sc.stop()
    }
    */
    
    def findClusters(inputFile : String, epsilon : Double, minCount : Int) : ModelWrapper = {

        Logger.getLogger("org").setLevel(Level.ERROR)
        //Configure Spark 
        val sc = SparkContext.getOrCreate()

        //Read file from spark
        val linesList = sc.textFile(inputFile)
        val regex = "\\s+"
        var points=linesList.flatMap(x=>toCouple(x.split(regex).map( x=>toDouble(x) ))
        //Remove duplicate keys
        ).map((_,1)).reduceByKey((v1,v2)=>v1+v2).keys//.persist(StorageLevel.MEMORY_ONLY_SER)

        val driverP : Array[(Double,Double)]= points.collect()

        val labels = collection.mutable.Map(   points.collect().map( (_,UNDEF) )   toSeq : _*)

        var clusterNum = 0

        val dim = driverP.size

        def log2 : Double => Double = (q) => math.log10(q) / math.log10(2)

        val LEAF_DIM = 1000

        val d : Int = log2(dim/LEAF_DIM).toInt + 1 

        val searchTree = new InNode(d,driverP,0)

        val treeBC = sc.broadcast(searchTree)
        var index = 0 

        val SOMETIME = 1000

        outbreak.breakable{
        for(p <- driverP){inbreak.breakable{
            
            index%SOMETIME match {
                case 0 => {
                    if(labels.filter(_._2 != UNDEF).keySet.size == dim){
                        outbreak.break
                    }
                }
                case _ => { }
            }
            index = index + 1
    
            if( labels(p)!= UNDEF ) {inbreak.break}//CONTINUE 
          
            var queue = searchTree.rangeQuery(epsilon,p,distance,0).toSet

            val c = queue.size
    
            (c<minCount) match {
                case true =>   labels(p)=NOISE
                case false => {
                    //CLUSTER LABEL 
                    clusterNum = clusterNum + 1
                    
                    labels(p)=clusterNum
        
                    queue = queue.filter(p1 => labels(p1)<=NOISE )
                    queue.foreach(labels(_)=clusterNum )

                    var done = false
                    while(!queue.isEmpty){
                        
                        points = sc.parallelize(queue.toSeq) // DRIVER ---shuffling---> EXECUTOR
                        val labelsBC = sc.broadcast(labels)
                        def arg1( acc1 : Set[(Double,Double)], acc2 : Set[(Double,Double)] ) = {
                             acc1 ++ acc2 
                        }
                        def arg0(acc : Set[(Double,Double)],  p1 : (Double,Double)) = {  
                            var nN = treeBC.value.rangeQuery(epsilon,p1,distance,0)
                            (nN.size >= minCount) match {
                                case true => {
                                    nN = nN.filter(p2 => labelsBC.value(p2)<=NOISE )
                                    //This modifies only the local copy of labels but this way is more efficient
                                    //nN.foreach(p3 => labelsBC.value(p3)=clusterNum)
                                    acc ++ nN.toSet
                                }
                                case false => acc
                            }
                        }
                        
                        queue = points.aggregate( Set() : Set[(Double,Double)]) (arg0,arg1)
                        labelsBC.destroy 
                        queue.foreach(labels(_)=clusterNum )
                    }

                }//Case false
            }//Match c<minCount
    
            //JOIN ALL EXECUTORS I GUESS
            
        }}//FOR
        }

        sc.stop()
        //Create and return a ModelWrapper instance
        new ModelWrapper(clusterNum, labels)
    }/*FIND_CLUSTERS METHOD*/

}

class ModelWrapper(
            val labelN : Int, 
            val labelV : collection.mutable.Map[(Double,Double),Int]){

    def getNoiseP() : Seq[ (Double,Double) ] = {
        labelV.filter( _._2 == DBSCAN.NOISE).keys.toSeq
    }

    def getClustersNum() : Int = labelN 

    def clusterPoints(c : Int) : Seq[ (Double,Double) ] = {
        labelV.filter( _._2 == c ).keys.toSeq
    }
}

object EntryPoint{
    def main(args: Array[String]) {
        println("Enter main")
        var file : Option[String] = None 
        var epsilon : Option[String] = None
        var minc : Option[String] = None
        try {
            
        for(i <- 0 until args.length){
            args(i) match {
                case "--data-file" => {
                    file = Option(args(i+1))
                    println("DATA FILE IS : "+file)
                    }
                case "--eps" => {
                    epsilon = Option(args(i+1))
                }
                case "--minc" => {
                    minc = Option(args(i+1))
                }
                case _ => {}
            }
        }

        } catch {
            case e: java.lang.ArrayIndexOutOfBoundsException => {
                println("Missing option value");
                System.exit(-1)

            }
        }

        if(file == None || epsilon == None || minc == None){
            println("Args missing")
            System.exit(-1)
        }

        //DBSCAN.testSparkSub()
        //System.exit(0)

        val t0 = System.nanoTime
        val model = DBSCAN.findClusters(
                        file.get,
                        epsilon.get.toDouble,
                        minc.get.toInt)
        val t1 = System.nanoTime
        println("Elapsed time : "+(t1-t0)/math.pow(10,9)+" seconds")
        println("FOUND "+model.getClustersNum()+" CLUSTERS")
    }
}