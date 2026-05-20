package com.kun.mianshikun.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class QuestionVO implements Serializable {

    private Long id;

    private String title;

    private String content;

    private List<String> tags;

    private String answer;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private UserVO user;

    private static final long serialVersionUID = 1L;
}
