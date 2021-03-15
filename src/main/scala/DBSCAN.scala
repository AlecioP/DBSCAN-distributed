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

        for(p <- driverP){outbreak.breakable{

            println("IN CLUSTER : "+ labels.filter(_._2 > NOISE).keySet.size )
            println("NOISE : "+labels.filter(_._2 == NOISE).keySet.size)
            println("UNDEF : "+labels.filter(_._2 == UNDEF).keySet.size)

            var (t0,t1) = (0L,0L)
    
            if( labels(p)!= UNDEF ) {outbreak.break}//CONTINUE 
    
            //EACH EXECUTOR HAS A SUBSET SbS OF ALL THE POINTS
            //COMPUTE DISTANCE OF P FROM EACH POINT IN SbS
          
            t0 = System.nanoTime
            var queue = searchTree.rangeQuery(epsilon,p,distance,0).toSet
            t1 = System.nanoTime

            println("SEQ TIME : "+(  (t1-t0)/math.pow(10,9))  )

            val c = queue.size
            //println("PRINT C "+c.toString)
    
            (c<minCount) match {
                case true =>   labels(p)=NOISE
                case false => {
                    //CLUSTER LABEL 
                    clusterNum = clusterNum + 1
                    labels(p)=clusterNum
        
                    //queue = queue.filter(!cmp(p,_))
                    queue = queue.filter(p1 => labels(p1)<=NOISE )
                    queue.foreach(labels(_)=clusterNum )

                    t0 = System.nanoTime
                    var done = false
                    while(!queue.isEmpty){
                        done match {case false => {println("Enter");done=true} case true => {} }
                        println("Queue dim : "+queue.size)

                        //for (newN <- queue ){  labels(newN) = clusterNum} 
                        

                        points = sc.parallelize(queue.toSeq) // DRIVER ---shuffling---> EXECUTOR


                        queue =  points.map(treeBC.value.rangeQuery(epsilon,_,distance,0))
                                .filter(_.length >= minCount)
                                .flatMap(a => a)
                                .collect.toSet  // DRIVER <---shuffling--- EXECUTOR
                        queue = queue.filter(p1 => labels(p1)<=NOISE )
                        queue.foreach(labels(_)=clusterNum )
                    }
                    t1 = System.nanoTime
                    println("NN TIME : "+(  (t1-t0)/math.pow(10,9))  )

                }//Case false
            }//Match c<minCount
    
            //JOIN ALL EXECUTORS I GUESS
            
        }}//FOR

        sc.stop()
        //Create and return a ModelWrapper instance
        new ModelWrapper(clusterNum, labels)
    /*FIND_CLUSTERS METHOD*/}

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


        /*
            DRIVER : New Cluster from P0 

            for p <- driverP 

            val neighs = tree.rangeQuery(p)

            if(neighs.dim < minCount)
                p -> noise
            else


            P-NEIGHS = P1,P2,P3,P4,P5           P6,P7

                var points =  sc.parallelize(P-NEIGHS)

                points.flat

                val subset = points.filter( _ in P-NEIGHS )
                

                P-NEIGHS = subset.flatMap(rangeQuery(_)<minCounts).collect


                    Exec 1 : P1,P2            Exec 2 : P3             Exec 3  : P4
        
                            tree.search(P1) -> NN < minCount -> STOP
                                                  >= flatMap -> NN

                    tmp = nn.collect
                    delete[] points
                    points = sc.parallelize(tmp)
                            
        
        */