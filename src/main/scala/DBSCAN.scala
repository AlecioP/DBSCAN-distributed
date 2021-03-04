import math._
import scala.util.control._
import annotation._
import org.apache.spark.SparkContext, org.apache.spark.SparkConf
//TO AVOID FUCKING SPARK LOG
import org.apache.log4j.{Level, Logger}

object DBSCAN{
    private val UNDEF : Int = -2
    val NOISE : Int = -1
    @transient private val outbreak = new Breaks;
    @transient private val inbreak = new Breaks;

    def distance(p1:(Double,Double),p2:(Double,Double)) : Double ={
        math.sqrt(
            math.pow(p1._1 - p2._1 , 2) +
            math.pow(p1._2 - p2._2 , 2)
        )
    }

    //Says wheter p1 equals p2 component-wise
    def cmp(p1:(Double,Double),p2:(Double,Double)): Boolean = {
        ((p1._1 == p2._1) && (p1._2 == p2._2))
    }

    //Useless function to replace RDD.count()
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

    //Label each point of the dataset with a label 
    
    def findClusters(inputFile : String, epsilon : Double, minCount : Int) : ModelWrapper = {

        Logger.getLogger("org").setLevel(Level.ERROR)
        //Configure Spark 
        val conf = new SparkConf().setAppName("DBSCAN-distr")
                                    .setMaster("local[*]")
        val sc = SparkContext.getOrCreate(conf)

        //Read file from spark
        val linesList = sc.textFile(inputFile)

        //Format as Seq[ Tuple2[Double] ]
        val regex = "\\s+"
        val points=linesList.flatMap(
                    x=>toCouple(
                        x.split(regex).map( x=>toDouble(x) )
                    )
        )

        //Maintain a copy of The points in the driver
        val driverP = points.collect()

        /*
        Create a mutable map for the points labels 
        and maintain it into the Master
        */
        val labels = collection.mutable.Map(   points.collect()
                                                .map( (_,UNDEF) )   
                                                toSeq : _*)

        //Init current number of Clusters found
        var clusterNum = 0

        val dim = points.count()

        for(it <- 0 until dim.toInt){outbreak.breakable{
    
            if( labels(driverP(it))!= UNDEF ) {outbreak.break}//CONTINUE 
    
            //EACH EXECUTOR HAS A SUBSET SbS OF ALL THE POINTS
            //COMPUTE DISTANCE OF P FROM EACH POINT IN SbS
            val p = sc.broadcast(driverP(it))
    
            //IN EXECUTOR
            val neighs = points.filter(x => distance(p.value,x)<=epsilon)
    
            //COLLECT C IN DRIVER
    
    
            var queue = neighs.collect().filter(!cmp(driverP(it),_)).toSet
            val c = queue.size
            //println("PRINT C "+c.toString)
    
            if(c<minCount){
                labels(driverP(it))=NOISE
            }
            else{
                //CLUSTER LABEL 
                clusterNum = clusterNum + 1
                labels(driverP(it))=clusterNum
        
                println("NEW CLUSTER "+clusterNum.toString)
        
        
                while(queue.size>0){inbreak.breakable{
                    val h =queue.head
                    val pStr = "("+h._1.toString + "," +  h._2.toString + ")"
           
                    if(  labels(h)  == NOISE) {labels(h)= clusterNum}
                    if(  labels(h)  != UNDEF) {queue = queue.filter(!cmp(h,_));inbreak.break}
            
                    //println("Add "+pStr+" to cluster "+clusterNum.toString)
            
                    labels(h) = clusterNum
            
                    val q = sc.broadcast(h)
            
                    val nN = points.filter(y => distance(q.value,y)<=epsilon)
            
                    //The neighboors of the neighboors
                    val driverNn = nN.collect().toSet
            
                    //val c1 = nN.count() ----------->MORE SHUFFLING BUT LESS COMPUTATION IN DRIVER
            
                    val c1 = driverNn.size // ----->LESS SHUFFLING BUT MORE COMPUTATION IN DRIVER
                    //println("PRINT C1 "+c1.toString)
                    if(c1>= minCount){
                        //println("Update queue for cluster "+clusterNum.toString)
                        val nNeighs = driverNn.filter(labels(_)< (-1))
                        //REMOVE THE ELEMENT COMPUTED, ADD ITS NEIGHBORS
                        queue = queue.filter(!cmp(h,_) ) ++ nNeighs
                    }else{
                        //println("REMOVE "+h.toString)
                        queue = queue.filter(!cmp(h,_) )
                    }//ELSE
            
                }}//WHILE
            }//ELSE
    
            //JOIN ALL EXECUTORS I GUESS
        }}

        sc.stop()
        //Create and return a ModelWrapper instance
        val mw = new ModelWrapper(clusterNum, labels)
        mw

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
        val file = "data/clusterin"
        val model = DBSCAN.findClusters(file,5,5)
        println("FOUND "+model.getClustersNum()+" CLUSTERS")
    }
}