package com.example;

import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * KafkaConsumerのインスタンスを作成するファクトリクラス.
 */
public class ConsumerFactory {
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:29092";
    private static final String DESERIALIZER_NAME = "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String GROUP_ID = "Tx-Group1";
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    private ConsumerFactory() {
    }

    /**
     * KafkaConsumerのインスタンスを生成するファクトリメソッド
     *
     * @return 設定済みのConsumerインスタンス
     */
    public static KafkaConsumer<String, String> newInstance() {
        return new KafkaConsumer<>(getConsumerConfig());
    }

    private static Properties getConsumerConfig() {
        Properties configs = new Properties();

        // 必須項目
        configs.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configs.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DESERIALIZER_NAME);
        configs.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DESERIALIZER_NAME);
        configs.setProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        configs.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
        configs.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // トランザクション設定項目
        configs.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return configs;
    }
}
