# DBSCAN-distributed
Implementazione Scala + Spark dell'Algoritmo di clustering DBSCAN

## Algoritmo  DBSCAN 

<p>
L'algoritmo DBSCAN e' un algoritmo di clustering, che divide un dataset in un numero di gruppi non fissato a priori.

Questo algoritmo itera il punti del dataset (**foreach** *p* **in** *DS*) per ogni *p* determina tutti i punti nella sua neighborhood calcolando la funzione  
*distance(p1:(**Double,Double**),p2:(**Double,Double**)):Boolean*. 

Una volta calcolati tutti i punti li conta, se questo conteggio *c* risulta essere minore di un certo valore prestabilito *minCount* allora *p* e' etichettato con label *NOISE*, altrimenti diventa il primo punto di un nuovo cluster. 
    
A questo punto ogni punto nella neighborhood di *p* entra a far parte del cluster se a sua volta nella sua neighborhood ci sono almeno *minCount* punti. 

Ricorsivamente ogni punto avvia la stessa computazione su ogni punto a lui vicino.  
    
L'algoritmo termina nel momento un cui tutti i punti in *DS* sono stati etichettati. 
    
</p>

![title](../img/Visual.gif)

<p> 
<a href="https://towardsdatascience.com/the-5-clustering-algorithms-data-scientists-need-to-know-a36d136ef68"> Image From Here </a>
</p>

## Implementazione

### Introduzione
Per prima cosa importiamo il dataset, che supponiamo essere nel file **clusterin**, in un RDD Spark 

~~~scala
val linesList = sc.textFile("clusterin")
~~~
La funzione **SparkContext.textFile** restituisce una struttura dati dove gli elemento e' una riga del file in input  Convertiamo questa struttura in una struttura contenente delle coppie di *Double* che rappresentano le coordinate di punti nel piano cartesiano 


~~~scala
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
~~~

### Architettura



## Test

### Dataset 

Il dataset utilizzato per i test di performance e' disponibile  
sul repository  dell'<a href="https://archive.ics.uci.edu/ml/machine-learning-databases/00550/"> Universita' della California</a>.  
Scaricato il file zip ed estratto, siamo interessati al file 
**urbanGB.txt**. Per rendere questo file utilizzabile dalla nostra applicazione
viene riformattato attraverso il comando 

~~~sh
cat "urbanGB.txt" | sed -Ee 's/([+-]?[0-9]+\.?[0-9]*e?[+-]?[0-9]*)\s*,\s*([+-]?[0-9]+\.?[0-9]*e?[+-]?[0-9]*)/\1 \2/g' 1>"clusterin"
~~~

Nel dataset sono contenute coordinate sotto forma di latitudine e longitudine per **360177** punti di incidenti urbani avvenuti in Regno Unito. 

![title](../img/urbanGB.png)

### Correttezza

Grazie alla libreria python [Scikit-Learn](https://scikit-learn.org/stable/), che contiene un implementazione sequenziale dell'algoritmo DBSCAN, siamo stati in grado di verificare la correttezza dell'algoritmo implementato.  

Il primo test fatto quindi e' stato quello di correttezza. Abbiamo avviato prima due esecuzioni sull'algoritmo **Scikit-Learn** con due differenti configurazioni di parametri :

| EPSILON     | MIN COUNT   |
| ----------- | ----------- |
| `200`       | `200`       |
| `0.07`      | `20`        |

in modo tale da ottenere, nel primo caso un unico cluster che comprendesse tutti i punti, mentre nel secondo caso un numero sufficientemente elevato di cluster.

Questo test e' stato  effettuato in quanto avevamo modo di credere, da un analisi statica del codice, che le prestazioni potessere variare in funzione dei parametri ed in particolare il numero di cluster che questi avrebbero portato ad individuare. Questa intuizione si e' poi rivelata esatta, come vedremo.

Da questi test iniziali, i cluster individuati sono stati questi :

**Esecuzione 1 (E=200,M=200)**

![title](../img/DIV8eps200minc200.png)

**Esecuzione 2 (E=0.07,M=20)**

![title](../img/DIV8eps0dt07minc20.png)

*Nota : L'esecuzione del primo esempio ha impiegato molto piu' tempo rispetto al secondo*

Il dataset contenente circa 300 mila istanze e' troppo grande perche' l'algoritmo lo possa elaborare su una singola macchina, per questo motivo il dataset e' stato campionato e sono stati estratti un ottavo dei dati

Lo script python utilizzato per creare questi risultati e' disponibile [qui](../py-util/pydbscan.py)