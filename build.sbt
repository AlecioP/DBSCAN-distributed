organization :="org.ercolessi-portaro"

version :="1.0-SNAPSHOT"

//Emr-5.32.0 uses Spark 2.4.7 which in turn uses Scala 2.12
//https://spark.apache.org/docs/2.4.7/

scalaVersion := "2.12.13"

// https://mvnrepository.com/artifact/org.apache.spark/spark-core
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.7"

