package com.example;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import com.example.events.TicketOrder;
import com.example.serdes.TicketOrderSerdes;

/**
 * イベントをフィルターするSteamsアプリケーションのエントリーポイント
 */
public class StreamApp {
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:29092";
    private static final String SOURCE_TOPIC = "ticket-order";
    private static final String SINK_TOPIC = "category-filtered-ticket-order";

    public static void main(String[] args) {
        // Build processor topology
        Topology topology = buildTopology();

        // Set properties
        Properties streamConfig = getStreamsConfig();

        // Build and run streams application
        KafkaStreams streams = new KafkaStreams(topology, streamConfig);
        streams.start();

        // Register graceful shutdown procedure
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static Properties getStreamsConfig() {
        Properties streamConfig = new Properties();
        streamConfig.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "filter-streams-app");
        streamConfig.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        streamConfig.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamConfig.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        return streamConfig;
    }

    private static Topology buildTopology() {
        // Build processor topology
        StreamsBuilder streamsBuilder = new StreamsBuilder();

        // Topicからイベント読み出し
        KStream<String, TicketOrder> ticketOrderStream = streamsBuilder.stream(SOURCE_TOPIC,
                Consumed.with(Serdes.String(), new TicketOrderSerdes()));

        // フィルター処理
        KStream<String, TicketOrder> filteredStream = ticketOrderStream
                .filter((key, value) -> value.getCategory().equals("live"));

        // フィルター済みイベントをTopicに書き込み
        filteredStream.to(SINK_TOPIC, Produced.with(Serdes.String(), new TicketOrderSerdes()));

        return streamsBuilder.build();
    }
}
