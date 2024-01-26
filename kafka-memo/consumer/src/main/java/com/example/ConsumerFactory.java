package com.example;

import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * KafkaConsumerのインスタンスを作成するファクトリクラス.
 */
public class ConsumerFactory {
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:29092";
    private static final String DESERIALIZER_NAME = "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String GROUP_ID = "G1";
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    private ConsumerFactory() {
    }

    public static KafkaConsumer<String, String> newInstance() {
        return new KafkaConsumer<>(getConsumerConfig());
    }

    private static Properties getConsumerConfig() {
        Properties configs = new Properties();
        configs.setProperty("bootstrap.servers", BOOTSTRAP_SERVERS);
        configs.setProperty("key.deserializer", DESERIALIZER_NAME);
        configs.setProperty("value.deserializer", DESERIALIZER_NAME);
        configs.setProperty("group.id", GROUP_ID);
        configs.setProperty("client.id", CLIENT_ID);
        configs.setProperty("auto.offset.reset", "earliest");

        return configs;
    }
}
