package com.tibudget.utils;

import java.time.OffsetDateTime;

public class InvoiceDto {

    private long id;
    private String name;
    private String state;
    private String fileState;
    private String fileUrl;
    private String amount;
    private OffsetDateTime date;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getFileState() {
        return fileState;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getAmount() {
        return amount;
    }

    public OffsetDateTime getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "Invoice{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", amount='" + amount + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
