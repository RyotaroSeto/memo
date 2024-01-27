package com.example.serdes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.kafka.common.serialization.Serializer;

import com.example.events.TicketOrder;
import com.google.gson.Gson;

/**
 * チケット取引イベントをシリアライズするクラス
 */
public class TicketOrderSerializer implements Serializer<TicketOrder> {
    private Gson gson = new Gson();

    @Override
    public byte[] serialize(String topic, TicketOrder data) {
        if (Objects.isNull(data)) {
            return new byte[] {};
        }
        return gson.toJson(data).getBytes(StandardCharsets.UTF_8);
    }

}
