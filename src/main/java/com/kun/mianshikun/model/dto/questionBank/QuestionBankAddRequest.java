package com.kun.mianshikun.model.dto.questionBank;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankAddRequest implements Serializable {

    private String title;

    private String description;

    private String picture;

    private static final long serialVersionUID = 1L;
}
