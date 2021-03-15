import scala.util.Sorting._
import java.io.Serializable
abstract class Node(val d : Int){
    def rangeQuery(epsilon : Double, 
                   center : (Double,Double),
                   distanceF : ( (Double,Double),(Double,Double) )=>Double ,
                    currentDepth : Int) : Seq[(Double,Double)]
}
class LeafNode(d : Int,points : Array[(Double,Double)]) extends Node(d) with Serializable{
    val cell : Array[(Double,Double)] = points
    
    def rangeQuery(epsilon : Double, 
                   center : (Double,Double),
                   distanceF : ( (Double,Double),(Double,Double) )=>Double ,
                    currentDepth : Int): Seq[(Double,Double)] = {
        cell.filter(distanceF(center,_)<=epsilon)
    }
    
    
    override def toString() : String = {
        cell.length match {
            case 0 => "LEAF[]"
            case 1 => "LEAF["+ tupleStr(cell.head)  + "]"
            case _ => "LEAF["+ cell.foldLeft("")( (acc,el) => acc+","+tupleStr(el) ) + "]"
        }
    }
    def tupleStr(  el:(Double,Double)  ) : String = {
        "<"+ el._1.toString + "," + el._2.toString  + ">"
    }
}
class InNode(d : Int,points : Array[(Double,Double)],sAxis : Int) extends Node(d) with Serializable{
    var pivot : (Double,Double) = (0,0)
    var left : Node = null
    var right : Node = null
    val (outL,outR) = split(points,sAxis)
    this.left = outL
    this.right = outR
    
    def split(p : Array[(Double,Double)],currentD : Int) : (Node,Node) = {
        p.isEmpty match { 
            case true => (new LeafNode(d,Array()),new LeafNode(d,Array())) 
            case _ =>
                //current Tree Depth / Dimension of the point -> 2 in the case of IR^2
                val axis = currentD%2
                stableSort(p,
                        (p1: (Double,Double),p2: (Double,Double)) =>
                        (p1.productElement(axis).asInstanceOf[Double] <
                        p2.productElement(axis).asInstanceOf[Double]) )
                val median = p.size / 2
                this.pivot = p(median)
                val (l,r) = p.splitAt(median)
                val (sl,sr) = (l.length,r.length)
                
                (sl,sr) match{
                    case (0,1) => (new LeafNode(d,Array()),new LeafNode(d,Array()))
                    case (1,1) => (new LeafNode(d,l),new LeafNode(d,Array()))
                    case _ => 
                        (currentD+1) match {
                            case this.d => (new LeafNode(d,l),new LeafNode(d,r.drop(1)))
                            case _ => (new InNode(d,l,sAxis+1),new InNode(d,r.drop(1),sAxis+1))
                        }//MATCH currentD+1
                }//MATCH (sl,sr)
        }//MATCH p.isEmpty
    }//SPLIT
    
    def rangeQuery(epsilon : Double, 
                   center : (Double,Double),
                   distanceF : ( (Double,Double),(Double,Double) )=>Double ,
                    currentDepth : Int): Seq[(Double,Double)] = {
        var cComp : Double = 0
        (currentDepth%2) match {
            case 0 => cComp = center._1
            case 1 => cComp = center._2
        }
        val (rMin,rMax) : (Double,Double) = (cComp - epsilon , cComp + epsilon )
        val comp = center.productElement(currentDepth%2).asInstanceOf[Double]
        
        val insertPivot = Array(this.pivot).filter(distanceF(center,_)<=epsilon) 
        
        if(comp>rMax)
            insertPivot ++ this.left.rangeQuery(epsilon,center,distanceF,currentDepth+1)
        else if(comp<rMin)
            insertPivot ++ this.right.rangeQuery(epsilon,center,distanceF,currentDepth+1)
        else
            insertPivot ++ this.left.rangeQuery(epsilon,center,distanceF,currentDepth+1) ++
                            this.right.rangeQuery(epsilon,center,distanceF,currentDepth+1)
    }
    
    override def toString() : String = {
        "<"+pivot._1+","+pivot._2+">{"+left.toString+"|"+right.toString+"}"
    }
}
