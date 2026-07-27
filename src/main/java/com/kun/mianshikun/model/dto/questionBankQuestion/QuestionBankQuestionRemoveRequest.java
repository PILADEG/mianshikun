package com.kun.mianshikun.model.dto.questionBankQuestion;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankQuestionRemoveRequest implements Serializable {
    public Long questionBankId;
    public Long questionId;
    private static final long serialVersionUID = 1L;
}
