package com.kun.mianshikun.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

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
}
