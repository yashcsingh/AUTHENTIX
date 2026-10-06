package com.authentix.backend.dto;

import jakarta.validation.constraints.Size;

public class ConfirmTransferRequest {

    @Size(max = 255, message = "Note cannot exceed 255 characters")
    private String note;

    public ConfirmTransferRequest() {
    }

    public ConfirmTransferRequest(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
