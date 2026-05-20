package com.kun.mianshikun.model.dto.questionBankQuestion;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankQuestionAddRequest implements Serializable {

    private Long questionBankId;

    private Long questionId;

    private static final long serialVersionUID = 1L;
}
