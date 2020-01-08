# Init swarm if not already done so
docker swarm init

# Create a docker network for everything to communicate on
docker network create -d overlay --attachable kafka-net

# Spin up a zookeeper instance
docker service create --network kafka-net --name=zookeeper \
          --publish 2181:2181 qnib/plain-zookeeper:2018-04-25
          
# Spin up a Zookeer UI 
docker service create --network kafka-net --name=zkui \
          --publish 9090:9090 \
          qnib/plain-zkui@sha256:30c4aa1236ee90e4274a9059a5fa87de2ee778d9bfa3cb48c4c9aafe7cfa1a13
          
# See it all running
docker service ls --format 'table {{.Name}}\t{{.Replicas}}\t{{.Ports}}'

# Next:
# Open http://localhost:9090 in a web browser to see what Zookeeper keeps. The login is admin/manager.

# Spin up a Kafka broker
docker service create --network kafka-net --name broker \
         --hostname="{{.Service.Name}}.{{.Task.Slot}}.{{.Task.ID}}" \
         -e KAFKA_BROKER_ID={{.Task.Slot}} -e ZK_SERVERS=tasks.zookeeper \
         --replicas 3 \
         qnib/plain-kafka:2019-01-28_2.1.0

# Create our Topic
docker exec -t -e JMX_PORT="" \
	$(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'|head -n1) \
	/opt/kafka/bin/kafka-topics.sh --zookeeper tasks.zookeeper:2181 \
	--partitions=1 --replication-factor=1 --create --topic test

# Check in Zookeeper UI
# Open http://localhost:9090 again, navigate to "/brokers/topics"
# Drill down a bit and see what other things you can find

# Better that directly looking into zookeeper is to use Yahoo's Kafka Manager
# Some parts dont work so I made my own container with the latest version of the manager
# build it by 
cd HelloWorld/kafka-manager/docker
docker build -t sourcelabs/kafka-manager .

# Then run it
docker service create --network kafka-net --name manager \
          -e ZK_HOSTS=tasks.zookeeper --publish=9000:9000 \
		  sourcelabs/kafka-manager
# now open up http://localhost:9000/

# See we have no clusters defined
# Next we'll add a cluster
# Click "Cluster" -> "Add Cluster"

# Enter:
#	Cluster Name: kafka
# 	Cluster Zookeeper Hosts: tasks.zookeeper:2181
#   Check "Enable JMX Polling (Set JMX_PORT env variable before starting kafka server)"
# 	Click "Save"
# 	Click "Go to cluster view"
#   See we have "1" topic
#	Click on the "1"

# Confirm the current offset of the topic 
docker exec -e JMX_PORT=1235 $(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'| head -n1) \
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test

# Now we'll populate the topic
docker run -t --rm --network kafka-net qnib/golang-kafka-producer:2018-05-01.5 5

# Confirm the current offset of the topic has increased
docker exec -e JMX_PORT=1235 $(docker ps -q --filter 'label=com.docker.swarm.service.name=broker'| head -n1) \
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test













