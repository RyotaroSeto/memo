package com.example;

import java.time.Duration;
import java.util.Collections;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumerアプリケーションのエントリーポイント
 */
public class ConsumerApp {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerApp.class);
    private static final String TOPIC = "ticket-order";

    public static void main(String[] args) {

        final Consumer<String, String> consumer = ConsumerFactory.newInstance();
        consumer.subscribe(Collections.singletonList(TOPIC));
        logger.info("Start to poll records from Topic={}", TOPIC);

        // 安全にConsumerを停止させる処理をアプリシャットダウン前に挟む
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received stop signal...");
            consumer.wakeup();
        }));

        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1L));
                records.forEach(record -> {
                    int recordPartition = record.partition();
                    long recordOffset = record.offset();
                    String recordKey = record.key();
                    String recordVal = record.value();
                    logger.info(
                            "Received record: Partition={}, Offset={}, Key={}, Value={}",
                            recordPartition,
                            recordOffset,
                            recordKey,
                            recordVal);
                });
            }
        } catch (WakeupException we) {
            // 別スレッドから実行された consumer.wakeup() が投げる例外
            // 意図的なアプリケーションの停止による例外なので、ここでは何もしない。
        } catch (Exception e) {
            // 意図しない例外
            logger.error("Unexpected error", e);
        } finally {
            logger.info("Closing consumer...");
            consumer.close();
        }
    }
}
