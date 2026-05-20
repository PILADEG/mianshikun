package com.kun.mianshikun.model.dto.question;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class QuestionAddRequest implements Serializable {

    private String title;

    private String content;

    private List<String> tags;

    private String answer;

    private static final long serialVersionUID = 1L;
}
