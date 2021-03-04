organization :="com.github.aleciop"

name := "dbscan-distributed"

version :="1.0-SNAPSHOT"

developers ++= List(
  Developer("AlecioP", "Alessio Portaro", "@AlecioP", url("https://github.com/AlecioP")),
  Developer("andreaErco", "Andrea Ercolessi", "@andreaErco", url("https://github.com/andreaErco"))
)

//Emr-5.32.0 uses Spark 2.4.7 which in turn uses Scala 2.12
//https://spark.apache.org/docs/2.4.7/

scalaVersion := "2.12.13"

val sparkVersion = "2.4.7"

/*
https://mungingdata.com/apache-spark/building-jar-sbt/

" 
[...]
The “provided” string at the end of the line
indicates that the spark-sql dependency should 
be provided by the runtime environment that uses 
this JAR file.
[...]
*/

// https://mvnrepository.com/artifact/org.apache.spark/spark-core
libraryDependencies += "org.apache.spark" %% "spark-core" % sparkVersion % "provided"


//https://mungingdata.com/apache-spark/building-jar-sbt/
//For JAR naming conventions


artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
  artifact.name + "_" + sv.binary + "-" + sparkVersion + "_" + module.revision + "." + artifact.extension
}
