package nl.sourcelabs.kafka.spring.consumer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;

@SpringBootApplication
public class Application {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        MessageListener listener = context.getBean(MessageListener.class);

        listener.latch.await(10, TimeUnit.SECONDS);
        context.close();

    }

    @Bean
    public MessageListener messageListener() {
        return new MessageListener();
    }

    public static class MessageListener {

        private CountDownLatch latch = new CountDownLatch(3);

        @KafkaListener(
                groupId = "consumerGroup2",
                containerFactory = "consumerGroup2KafkaListenerContainerFactory",
                topicPartitions = {
                                    @TopicPartition(topic = "test",
                                            partitionOffsets =  { @PartitionOffset(partition = "0", initialOffset = "0"),
                                                                  @PartitionOffset(partition = "1", initialOffset = "0"),
                                                                  @PartitionOffset(partition = "2", initialOffset = "0")}
                                                )
                                    }
        )
        public void listenGroupFoo(String message) {
            System.out.println("Received Message in group 'foo': " + message);
            latch.countDown();
        }

    }

}
