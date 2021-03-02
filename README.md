# DBSCAN-distributed
Scala + Spark implementation of DBSCAN clustering 

## Algoritmo  DBSCAN 

<p>
L'algoritmo DBSCAN e' un algoritmo di clustering,<br> 
che divide un dataset in un numero di gruppi non <br>
fissato a priori.

Questo algoritmo itera il punti del dataset :<br>
**foreach** *p* **in** *DS*

per ogni *p* determina tutti i punti nella sua <br>
neighborhood calcolando la funzione <br>
*distance(p1:(Float&plus;),p2:(Float&plus;)):Boolean*.<br>

Una volta calcolati tutti i punti li conta, se questo <br>
conteggio *c* risulta essere minore di un certo valore <br>
prestabilito *minCount* allora *p* e' etichettato con <br>
label *NOISE*, altrimenti diventa il primo punto di un <br>
nuovo cluster.<br>
    
A questo punto ogni punto nella neighborhood di *p* entra <br> 
a far parte del cluster se a sua volta nella sua <br>
neighborhood ci sono almeno *minCount* punti.<br>

Ricorsivamente ogni punto avvia la stessa computazione <br>
su ogni punto a lui vicino. <br>
    
L'algoritmo termina nel momento un cui tutti i punti <br>
in *DS* sono stati etichettati.<br>
    
</p>

![title](img/visual.gif)

## Implementazione

Per prima cosa generiamo un dataset di prova
(Codice preso dall'esempio k-means visto a lezione) :


```scala
/*
import java.io._
import scala.util.Random

val fileName = "clusterin" 

val epsilon = 0.0001
val numK = 4

val randomX = new Random
val randomY = new Random
val maxCoordinate = 100.0

def genFile() = {

    val initPoints = Vector((50.0,50.0),(50.0,-50.0),(-50.0,50.0),(-50.0,-50.0))

    val distance = 80.0
    val numPoints = 1000

    val randomPoint = new Random

    val file = new File(fileName)
    val bw = new BufferedWriter(new FileWriter(file))
    for (i <- 0 until numPoints) {
      val x = (randomX.nextDouble-0.5) * distance
      val y = (randomX.nextDouble-0.5) * distance
      val centroid = initPoints(randomPoint.nextInt(initPoints.length))
      bw.write((centroid._1+x)+"\t"+(centroid._2 + y)+"\n")
    }
    bw.close
}

genFile()
*/
```


    Intitializing Scala interpreter ...



    Spark Web UI available at http://macbook-pro:4040
    SparkContext available as 'sc' (version = 3.0.2, master = local[*], app id = local-1614678417589)
    SparkSession available as 'spark'



A questo punto abbiamo un dataset da importare nella <br>
nostra applicazione, andando a costruire un RDD dalla <br>
libreria Spark


```scala
val linesList = sc.textFile("clusterin")
```




    linesList: org.apache.spark.rdd.RDD[String] = clusterin MapPartitionsRDD[1] at textFile at <console>:25




La funzione **SparkContext.textFile** restituisce<br>
una struttura lineare dove ogni elemento e' <br>
una riga del file in input<br><br>
Convertiamo questa struttura in <br>
una struttura che contenente delle <br>
coppie di *Float* che rappresentano<br>
le coordinate di punti nel piano cartesiano<br>


```scala
val regex = "\\s+"
def toFloat(s: String): Float = {
  try {
    s.toFloat
  } catch {
    case e: Exception => 0
  }
}
def toCouple(a : Array[Float]) : Array[(Float,Float)]= {
val c =(a(0),a(1))
Array(c)
}
val points=linesList.flatMap(x=>toCouple(x.split(regex).map(x=>toFloat(x))))
```




    regex: String = \s+
    toFloat: (s: String)Float
    toCouple: (a: Array[Float])Array[(Float, Float)]
    points: org.apache.spark.rdd.RDD[(Float, Float)] = MapPartitionsRDD[2] at flatMap at <console>:38




Cominciamo col creare una mappa per etichettare<br>
ogni punto nel dataset. Inizializziamo tutte le<br>
etichetta al valore **UNDEF**


```scala
//val label= collection.mutable.Map(points.collect().map(x => (x, "undef")).toMap.toSeq:_*)
```

A questo avviamo un iterazione su tutti i punti<br>
nel dataset per assegnare un etichetta ad ognuno<br><br>

Prima di avviare la computazione, dobbiamo definire<br>
due valori costanti a priori, che sono **Epsilon** e **MinCount** <br><br>

Inoltre dobbiamo definire la funzione di distanza<br>
tra due punti dello spazio. Nel nostro caso questa<br>
sara semplicemente la distanza euclidea tra i punti<br>


```scala
val epsilon = 4
val minCount = 5

// SQRT[ (x1-x2)^2 + (y1-y2)^2 ]
def distance(p1:(Float,Float),p2:(Float,Float))= 
    math.sqrt(math.pow(p1._1-p2._1,2)+math.pow(p1._2-p2._2,2))

//for(p<-points) {[MAIN-LOOP]}
```




    epsilon: Int = 4
    minCount: Int = 5
    distance: (p1: (Float, Float), p2: (Float, Float))Double





```scala
/*
RDD.count() may be inefficient :

https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/rdd/RDD.scala

Better version of count with less shuffling
*/

//(index,(x,y))

def cmp(p1:(Float,Float),p2:(Float,Float)): Boolean = {((p1._1 == p2._1) && (p1._2 == p2._2))}

def bCount(anRdd : org.apache.spark.rdd.RDD[(Float,Float)]) = 
    anRdd.map(x => ("same", (1,x) )).reduceByKey( (v1,v2) => (v1._1+v2._1,v1._2) ).collect()(0)._2._1.toInt
```




    cmp: (p1: (Float, Float), p2: (Float, Float))Boolean
    bCount: (anRdd: org.apache.spark.rdd.RDD[(Float, Float)])Int





```scala
/*

+++ DRIVER +++ 

label = (p0,"undef")(p1,"undef")(p2,"undef")(p3,"undef")(p4,"undef")
        (p5,"undef")(p6,"undef")(p7,"undef")(p8,"undef")(p9,"undef")

points = p0,p1,p2,p3,p4,p5,p6,p7,p8,p9

+++ EXECUTOR +++ 

        d(p0,p1) < epsilon ==> T ok        |
p0      d(p0,p2) < epsilon ==> F           |
        d(p0,p3) < epsilon ==> F           |
                                           |
        d(p0,p4) < epsilon ==> F           |
p0      d(p0,p5) < epsilon ==> T ok  == >  | 4 > minCount ==> T then label(p) = Nuovo Cluster C1
        d(p0,p6) < epsilon ==> T ok        |
                                           |
        d(p0,p7) < epsilon ==> F           |
p0      d(p0,p8) < epsilon ==> F           |
        d(p0,p9) < epsilon ==> T ok        |


p0
|
Neigh
|
V
p1 p5 p6 p9




*/

//val broadcast = sc.broadcast(points.collect())

// TRANSIENT : Do Not serialize -> Make shadow clone and send to executor OR 
//                                 Every executor accesses the structure in the driver

//val n = collection.mutable.Map(m.toSeq: _*) 
```


```scala
val driverP = points.collect()
```


```scala
val UNDEF : Int = -2
val NOISE : Int = -1

val labels = collection.mutable.Map(   points.collect().map( (_,UNDEF) )   toSeq : _*)
//var (dPx,dPy) = driverP.unzip
val dim = points.count()
```


```scala
var clusterNum = 0
```




    clusterNum: Int = 0





```scala
import scala.util.control._
```




    import scala.util.control._





```scala
@transient val outbreak = new Breaks;
@transient val inbreak = new Breaks;
```




    outbreak: scala.util.control.Breaks = scala.util.control.Breaks@6291618c
    inbreak: scala.util.control.Breaks = scala.util.control.Breaks@7d342b96





```scala

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
            }
            
        }}
    }
    
    //JOIN ALL EXECUTORS I GUESS
}}
```
