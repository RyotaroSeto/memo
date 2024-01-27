package com.example.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import com.example.events.TicketOrder;

/**
 * Kafkaのイベントをバイト配列とクラスに変換するSerde定義
 */
public class TicketOrderSerdes implements Serde<TicketOrder> {

    @Override
    public Serializer<TicketOrder> serializer() {
        return new TicketOrderSerializer();
    }

    @Override
    public Deserializer<TicketOrder> deserializer() {
        return new TicketOrderDeserializer();
    }

}
