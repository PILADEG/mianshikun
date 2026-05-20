package com.kun.mianshikun.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class QuestionBankVO implements Serializable {

    private Long id;

    private String title;

    private String description;

    private String picture;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private UserVO user;

    private List<QuestionVO> questionList;

    private static final long serialVersionUID = 1L;
}
