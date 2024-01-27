package com.example.serdes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.kafka.common.serialization.Deserializer;

import com.example.events.TicketOrder;
import com.google.gson.Gson;

/**
 * チケット取引イベントをデシリアライズするクラス
 */
public class TicketOrderDeserializer implements Deserializer<TicketOrder> {
    private Gson gson = new Gson();

    @Override
    public TicketOrder deserialize(String topic, byte[] data) {
        if (Objects.isNull(data)) {
            return null;
        }
        return gson.fromJson(new String(data, StandardCharsets.UTF_8), TicketOrder.class);
    }
}
