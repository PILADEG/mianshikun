package com.kun.mianshikun.model.dto.question;

import com.baomidou.mybatisplus.annotation.TableField;
import com.kun.mianshikun.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long notId;

    private String title;

    private String content;

    private List<String> tags;

    private String answer;
    @TableField(exist = false)
    private Long questionBankId;

    private Long userId;

    private String searchText;

    private static final long serialVersionUID = 1L;
}
