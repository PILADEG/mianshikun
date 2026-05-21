package com.kun.mianshikun.model.vo;

import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class QuestionBankQuestionVO implements Serializable {

    private Long id;

    private Long questionBankId;

    private Long questionId;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private UserVO user;

    private static final long serialVersionUID = 1L;

    public static QuestionBankQuestionVO objToVo(QuestionBankQuestion questionBankQuestion) {
        if (questionBankQuestion == null) {
            return null;
        }
        QuestionBankQuestionVO vo = new QuestionBankQuestionVO();
        BeanUtils.copyProperties(questionBankQuestion, vo);
        return vo;
    }
}
