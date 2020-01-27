 Init swarm if not already done so
```bash
docker swarm init
```

 Create a docker network for everything to communicate on
```bash
docker network create -d overlay --attachable kafka-net
```

 Spin up a zookeeper instance
```bash
docker service create --network kafka-net --name=zookeeper \
          --publish 2181:2181 qnib/plain-zookeeper:2018-04-25
```
          
 Spin up a Zookeer UI 
```bash
docker service create --network kafka-net --name=zkui \
          --publish 9090:9090 \
          qnib/plain-zkui@sha256:30c4aa1236ee90e4274a9059a5fa87de2ee778d9bfa3cb48c4c9aafe7cfa1a13
```
          
 See it all running in the Docker Daemon
```bash
docker service ls --format 'table {{.Name}}\t{{.Replicas}}\t{{.Ports}}'
```

 Next:
 Open http://localhost:9090 in a web browser to see what Zookeeper keeps. The login is admin/manager.

 Spin up 3 Kafka brokers which will automatically create a cluster since they all point at the same Zookeeper
```bash
docker service create --network kafka-net --name broker \
         --hostname="{{.Service.Name}}.{{.Task.Slot}}.{{.Task.ID}}" \
         -e KAFKA_BROKER_ID={{.Task.Slot}} -e ZK_SERVERS=tasks.zookeeper \
         --replicas 3 \
         qnib/plain-kafka:2019-01-28_2.1.0
```

We should already start to see the brokers fill up Zooekeeper with data

The 3 brokers: http://localhost:9090/home?zkPath=/brokers/ids

And we can also confirm there are no topics created yet: http://localhost:9090/home?zkPath=/brokers

 Create our Topic
```bash
docker exec -t -e JMX_PORT="" \
	$(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'|head -n1) \
	/opt/kafka/bin/kafka-topics.sh --zookeeper tasks.zookeeper:2181 \
	--partitions=1 --replication-factor=1 --create --topic test
```

Now confirm that we indeed have a topic known to our brokers.
 Open http://localhost:9090 again, navigate to "/brokers/topics"
 Drill down a bit and see what other things you can find

 Better that directly looking into zookeeper is to use Yahoo's Kafka Manager
 Some parts dont work so I made my own container with the latest version of the manager
 build it by 
```bash
cd HelloWorld/kafka-manager/docker
docker build -t sourcelabs/kafka-manager .
```

 Then run it
```bash
docker service create --network kafka-net --name manager \
          -e ZK_HOSTS=tasks.zookeeper --publish=9000:9000 \
		  sourcelabs/kafka-manager
```
 now open up our manager:
 http://localhost:9000/

 Next we'll define our cluster
 Click "Cluster" -> "Add Cluster"

 Enter:
	Cluster Name: kafka
 	Cluster Zookeeper Hosts: tasks.zookeeper:2181
 	Version 2.1.0
   Check "Enable JMX Polling (Set JMX_PORT env variable before starting kafka server)"
 	Click "Save"
 	Click "Go to cluster view"
   See we have "1" topic
	Click on the "1"
   
We can see that our topic ( http://localhost:9000/clusters/kafka/topics/test )
isn't utilising all three of our brokers. The red block showing 33% broker spread shows us we got a problem.
Partition 0, carrying all data from the test topic only resides on 1 broker. Which means if broker 1 explodes we lose all data.
Let's change that.


```bash
docker exec -t -e JMX_PORT="" \
	$(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'|head -n1) \
	/opt/kafka/bin/kafka-topics.sh --zookeeper tasks.zookeeper --delete --topic test

docker exec -t -e JMX_PORT="" \
	$(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'|head -n1) \
	/opt/kafka/bin/kafka-topics.sh --zookeeper tasks.zookeeper:2181 \
	--partitions=1 --replication-factor=3 --create --topic test
```

Now see that the single partition carrying our data is spread out across our 3 brokers with 1 broker acting as leader.

http://localhost:9000/clusters/kafka/topics/test 

This broker will be the one to talk to all consumers and producers with the other 2 as standby.

 Confirm the current offset of the topic 
```bash
docker exec -e JMX_PORT=1235 $(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'| head -n1) \
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test
```

 Now we'll populate the topic
```bash
docker run -t --rm --network kafka-net qnib/golang-kafka-producer:2018-05-01.5 5
```

 Confirm the current offset of the topic has increased
```bash
docker exec -e JMX_PORT=1235 $(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'| head -n1) \
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test
```


Open the maven project /kafka/kafka-producer in your favourite IDE

Have a look at our producer class.

Run
```bash
mvn clean install
```

This will create a docker image that we can use to run our java app within the docker network.

Then run the image within our Docker swarm network:

```bash
docker run --network="kafka-net" sourcelabs/kafka-producer:1.0-SNAPSHOT
```

You should see lots of logging showing our messages being sent.

Now let's confirm our offset again:

```bash
docker exec -e JMX_PORT=1235 $(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'| head -n1) \
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test
```


Now we've got a cluster and seen how easy it is to connect I invite you all to have a crack at making something yourself.


Possible challenges:

1. Create a consumer
2. Serialise differently (using an AVRO schema might be too hard)
3. 

















