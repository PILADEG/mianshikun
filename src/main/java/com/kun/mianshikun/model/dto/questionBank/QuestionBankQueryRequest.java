package com.kun.mianshikun.model.dto.questionBank;

import com.kun.mianshikun.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long notId;

    private String title;

    private String description;

    private String picture;

    private Long userId;

    private String searchText;

    private static final long serialVersionUID = 1L;
}
