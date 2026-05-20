package com.kun.mianshikun.model.dto.questionBank;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankEditRequest implements Serializable {

    private Long id;

    private String title;

    private String description;

    private String picture;

    private static final long serialVersionUID = 1L;
}
