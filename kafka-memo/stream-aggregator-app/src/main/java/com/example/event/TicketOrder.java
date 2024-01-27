package com.example.event;

import com.google.gson.annotations.SerializedName;

/**
 * チケット取引情報イベント
 */
public class TicketOrder {
    @SerializedName("order_id")
    private String orderId;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("content_id")
    private String contentId;
    private String category;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
