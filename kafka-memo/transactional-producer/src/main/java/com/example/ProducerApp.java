package com.example;

import java.util.Random;
import java.util.UUID;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producerアプリケーションのエントリーポイントクラス
 */
public class ProducerApp {
    private static final Logger logger = LoggerFactory.getLogger(ProducerApp.class);
    private static final String TOPIC = "ticket-order";

    public static void main(String[] args) {
        KafkaProducer<String, String> producer = KafkaProducerFactory.newInstance();

        // トランザクション初期化処理
        producer.initTransactions();

        try {
            // トランザクション開始
            producer.beginTransaction();

            // イベント送信
            Random rand = new Random();
            final String userId = String.valueOf((rand.nextInt(100) + 100));
            final String orderId = UUID.randomUUID().toString();
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, userId, orderId);

            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception == null) {
                        logger.info("sent record: topic={}, partition={}, offset={}", metadata.topic(),
                                metadata.partition(), metadata.offset());
                    } else {
                        logger.error("got error w/ send()", exception);
                    }
                }
            });

            // トランザクション確定
            producer.commitTransaction();
        } catch (KafkaException ke) {
            // トランザクション中断
            producer.abortTransaction();
        } finally {
            producer.close();
        }
    }
}
