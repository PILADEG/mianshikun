package com.kun.mianshikun.model.dto.questionBankQuestion;

import com.kun.mianshikun.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQuestionQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long notId;

    private Long questionBankId;

    private Long questionId;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
