package com.example;

import java.time.Duration;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.event.TicketOrder;
import com.example.serdes.TicketOrderSerdes;

/**
 * ストリーム処理のエントリーポイント
 */
public class StreamApp {
    private static final Logger logger = LoggerFactory.getLogger(StreamApp.class.getName());
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:29092";
    private static final String SOURCE_TOPIC = "category-filtered-ticket-order";
    private static final String SINK_TOPIC = "suspicious-user";

    public static void main(String[] args) {
        // プロセッサー・トポロジーをビルド
        Topology topology = buildTopology();
        logger.info(topology.describe().toString());

        // 設定値を取得
        Properties streamConfig = getStreamsConfig();

        // 設定値とトポロジーを組み合わせてKafka Streamsアプリケーションを定義
        KafkaStreams streams = new KafkaStreams(topology, streamConfig);
        streams.start();

        // グレースフル・シャットダウン
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static Properties getStreamsConfig() {
        Properties streamConfig = new Properties();

        // (1) 必須の設定値
        streamConfig.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "aggregator-streams-app");
        streamConfig.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        // (2) デフォルトで使用するイベントのキーと値のシリアライズ/デシリアライズ方法を定義
        streamConfig.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.String().getClass().getName());
        streamConfig.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                Serdes.String().getClass().getName());

        return streamConfig;
    }

    private static Topology buildTopology() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();

        // ① ソースプロセッサー (Topicからイベントを読み取り)
        KStream<String, TicketOrder> ticketOrderStream = streamsBuilder.stream(SOURCE_TOPIC,
                Consumed.with(Serdes.String(), new TicketOrderSerdes()));

        // ② 時間ウィンドウで集約 (5分のウィンドウ、1分の遅延許容)
        TimeWindows window = TimeWindows.ofSizeAndGrace(Duration.ofMinutes(5L), Duration.ofMinutes(1L));
        KTable<Windowed<String>, Integer> orderCountStream = ticketOrderStream
                .groupByKey()
                .windowedBy(window)
                .aggregate(
                        () -> 0,
                        (key, value, orderCount) -> orderCount + 1,
                        Materialized.with(Serdes.String(), Serdes.Integer()))
                .suppress(Suppressed.untilWindowCloses(BufferConfig.unbounded().shutDownWhenFull()));

        // ③ 時間あたり取引数の多いユーザーを抽出
        KStream<String, Integer> suspiciousUserStream = orderCountStream
                .toStream()
                .filter((userId, orderCount) -> orderCount >= 3)
                .map((windowedKey, orderCount) -> KeyValue.pair(windowedKey.key(), orderCount));

        // ④ シンクプロセッサー (抽出結果のイベントをTopicに書き戻し)
        suspiciousUserStream.to(SINK_TOPIC, Produced.with(Serdes.String(), Serdes.Integer()));

        // プロセッサー・トポロジーをビルド
        return streamsBuilder.build();
    }
}
