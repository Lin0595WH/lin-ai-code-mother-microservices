package com.lin.linaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppChatGenRequest implements Serializable {

    private String message;
}
