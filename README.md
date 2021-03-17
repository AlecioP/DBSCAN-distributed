# DBSCAN-distributed

A **Scala** + **Spark** implementation of DBSCAN clustering algorithm 

## Build software

### Download and environment setting

First clone locally the repository

~~~shell
git clone https://github.com/AlecioP/DBSCAN-distributed
~~~

Then move to the local repository

~~~shell
cd DBSCAN-distributed
~~~

In order to build a jar file to execute remotely on EMR cluster we use [SBT](https://www.scala-sbt.org/) package manager (A package manager for JAVA and SCALA like MAVEN)

To install sbt you must have [JDK](https://openjdk.java.net/) installed so run :

`MACOS`

~~~shell
brew install openjdk
~~~
If you do not have [Homebrew](https://brew.sh/index_it) installed

~~~shell
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
~~~

`UBUNTU`

~~~shell
sudo apt-get install openjdk-11-jdk
~~~

Then you can install SBT : 

`MACOS`

~~~shell
brew install sbt
~~~

`UBUNTU`

~~~shell
sudo apt-get install sbt
~~~

### To compile JAR locally 

Read the [build.sbt](build.sbt) file to understand all about dependencies

Mainly we add the dependency for *Spark* with the specific version required from EMR cluster, but we are marking this dependency as "provided" because the EMR cluster has the library already installed

Then we compile a thin jar from our repository, which means that we are not including in the archive any source file from the dependencies but only our app's source files

To compile the sources

~~~shell
sbt compile
~~~

To create the JAR file 

~~~shell
sbt package
~~~

### To set up AWS execution

Then we only need to run our application on a EMR cluster

To do that first go to your AWS console and open the [EC2](https://console.aws.amazon.com/ec2/) service page 

Once there, from the menu on the left go to <br>
**Network & Security**>**Key Pairs**
Here you can create a pair of keys to for remote connect via ssh to an EC2 machine
Follow the wizard, create the keys-pair, save you copy to your local machine <br>
**NOTE** : Be aware of where the *\*.pem* file has been saved on your machine and of course don't loose it. <br>Alternatively install `AWS-CLI`:

`MACOS`
~~~shell
brew install awscli
~~~

`UBUNTU`
~~~shell
sudo apt-get install awscli
~~~

Once done :

~~~shell
export KEYNAME=SomeNameForKey

#Name the file with *.pem extension
export KEYFILE=/full/path/to/new/file/containing/key.pem

aws ec2 create-key-pair --key-name $KEYNAME --query 'KeyMaterial' --output text > $KEYFILE
~~~

Now we can create our cluster 
Go to [EMR](https://console.aws.amazon.com/elasticmapreduce/) service page.
Here, from the menu on the left, click into *Clusters* then create a cluster.
Select the number of nodes that compose your cluster, the kind of node according to your needs, choose the *Spark* version to execute (the one we use is Spark-2.4.7-aws from Emr-5.32.0, but you can change through build file), but most importantly choose the key-pair you just created from the security section.<br>
With `AWS-CLI` :

~~~shell

#CUSTOMIZE ALL THESE PARAMETERS

export KEYNAME=TheKeyNameInThePreviewSection
export SUBNET_ID=TheIdOfSubnet
export EXECUTOR_SECURITY_GROUP=SecurityGroupForExecutor
export DRIVER_SECURITY_GROUP=SecurityGroupForDriver
export LOG_BUCKET=s3://S3BucketForLog
export EMR_VERSION=emr-5.32.0

export CLUSTER_NAME=SomeName

export EXECUTOR_INSTANCE_TYPE=t4g.nano
export EXECUTOR_INSTANCE_NUM=2

export DRIVER_INSTANCE_TYPE=t4g.nano

export REGION=us-east-1

aws emr create-cluster --applications Name=Spark Name=Zeppelin \
--ec2-attributes '{"KeyName":"${KEYNAME}","InstanceProfile":"EMR_EC2_DefaultRole","SubnetId":"${SUBNET_ID}","EmrManagedSlaveSecurityGroup":"${EXECUTOR_SECURITY_GROUP}","EmrManagedMasterSecurityGroup":"${DRIVER_SECURITY_GROUP}"}' \
--service-role EMR_DefaultRole \
--enable-debugging \
--release-label $EMR_VERSION\
--log-uri '${LOG_BUCKET}'\
--name '${CLUSTER_NAME}' \
--instance-groups '[{"InstanceCount":${EXECUTOR_INSTANCE_NUM},"EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"SizeInGB":32,"VolumeType":"gp2"},"VolumesPerInstance":1}]},"InstanceGroupType":"CORE","InstanceType":"${EXECUTOR_INSTANCE_TYPE}","Name":"Core Instance Group"},{"InstanceCount":1,"EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"SizeInGB":32,"VolumeType":"gp2"},"VolumesPerInstance":1}]},"InstanceGroupType":"MASTER","InstanceType":"${DRIVER_INSTANCE_TYPE}","Name":"Master Instance Group"}]'\
--configurations '[{"Classification":"spark","Properties":{}}]' \
--scale-down-behavior TERMINATE_AT_TASK_COMPLETION \
--region $REGION
~~~

Now load into AWS the JAR we created previewsly. To do that go to [S3](https://s3.console.aws.amazon.com/s3/) service page.
Create a new bucket and upload the JAR. Let's do the same with our *Dataset* file. You can load it into the same S3 bucket. <br>
Using `AWS-CLI` :

~~~shell
export S3_BUCKET_NAME=SomeName
export APP_JAR=/Path/to/JAR.jar
export DATASET_FILE=/Path/to/Dataset

aws s3 mb s3://$S3_BUCKET_NAME

aws s3 cp $APP_JAR s3://$BUCKET_NAME

aws s3 cp $DATASET_FILE s3://$BUCKET_NAME
~~~

Now we have all ready to run our application. Go to your local machine and open a shell. Into the shell set a variable with the path to the aforementioned *\*.pem* file

~~~shell
export AWS_AUTH_KEY="path/to/key.pem"
~~~

Make sure only your user has r/w permissions to this file. So run the command 

~~~shell
[sudo] chmod 600 $AWS_AUTH_KEY
~~~

Than we need the url of the cluster. To obtain that, go to EMR service page, select the cluster from the list, click into cluster details and from the **Master public DNS** voice copy the *URL* provided. Now from your local shell type

~~~shell
export AWS_MASTER_URL="Paste_here_the_url_you_just_copied"
~~~

To this point we can access the cluster driver via ssh

~~~shell
ssh -i $AWS_AUTH_KEY $AWS_MASTER_URL
~~~

Finally from the remote EC2 instance shell we can run our application via

~~~shell
spark-submit \
--conf "spark.serializer=org.apache.spark.serializer.KryoSerializer" \
--conf "spark.kryo.registrationRequired=true" \
--conf "spark.kryo.classesToRegister=InNode,LeafNode" \
--conf "spark.dynamicAllocation.enabled=false" \
--conf "spark.default.parallelism=8" \
--num-executors 8 \
--executor-cores 1 \
--class EntryPoint \
s3://URL_OF_JAR_WITHIN_S3 \
--data-file s3://URL_OF_DATASET \
--eps 200 \
--minc 200
~~~

Write litterally **EntryPoint** which is the name of an object from the JAR