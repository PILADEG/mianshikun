package com.kun.mianshikun.model.dto.questionBankQuestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
@Data
public class QuestionBankQuestionBatchRequest  implements Serializable {
    public Long questionBankId;

    public List<Long> questionIdList;

    private static final long serialVersionUID = 1L;
}
