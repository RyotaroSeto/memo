package com.example;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * Kafka Producerのインスタンスを生成するファクトリクラス.
 */
public class KafkaProducerFactory {
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:29092";
    private static final String SERIALIZER_NAME = "org.apache.kafka.common.serialization.StringSerializer";
    private static final String CLIENT_ID = "transactional-producer-1";

    private KafkaProducerFactory() {
    }

    /**
     * KafkaProducerの新しいインスタンスを生成するファクトリメソッド.
     *
     * @return KafkaProducerインスタンス
     */
    public static KafkaProducer<String, String> newInstance() {
        return new KafkaProducer<>(getProducerConfig());
    }

    private static Properties getProducerConfig() {
        Properties producerConfig = new Properties();

        // 必須項目
        producerConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerConfig.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, SERIALIZER_NAME);
        producerConfig.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SERIALIZER_NAME);
        producerConfig.setProperty(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID);

        // トランザクション設定項目
        producerConfig.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producerConfig.setProperty(ProducerConfig.RETRIES_CONFIG, "5");
        producerConfig.setProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "sample-tx-id");

        return producerConfig;
    }
}
